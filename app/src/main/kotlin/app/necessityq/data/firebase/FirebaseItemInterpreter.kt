package app.necessityq.data.firebase

import app.necessityq.domain.InterpretedItem
import app.necessityq.domain.ItemInterpreter
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads quick-add text the app did not understand through the `interpretItems` cloud function (Gemini, see
 * firebase/functions/src/interpret.ts). Null when it could not ask: offline, too slow, or the daily limit.
 */
class FirebaseItemInterpreter(private val functions: FirebaseFunctions) : ItemInterpreter {
    override suspend fun interpret(householdId: String, text: String, people: Double): List<InterpretedItem>? {
        val args = buildMap<String, Any> {
            put("householdId", householdId)
            put("text", text)
            if (people > 0) put("people", people)
        }
        val result = withTimeoutOrNull(TIMEOUT_MS) { functions.getHttpsCallable("interpretItems").call(args).await() } ?: return null
        val items = (result.data as? Map<*, *>)?.get("items") as? List<*> ?: return null
        return items.mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val name = (m["name"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            InterpretedItem(
                name = name,
                variant = (m["variant"] as? String)?.trim()?.ifEmpty { null },
                amount = (m["amount"] as? Number)?.toDouble(),
                unit = m["unit"] as? String,
                category = (m["category"] as? String) ?: "OTHER",
            )
        }
    }

    private companion object {
        /** Longer than this, the household adds the text as typed rather than wait. */
        const val TIMEOUT_MS = 15_000L
    }
}
