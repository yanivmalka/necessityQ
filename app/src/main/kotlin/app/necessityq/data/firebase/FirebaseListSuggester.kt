package app.necessityq.data.firebase

import app.necessityq.domain.AiListAnswer
import app.necessityq.domain.AiListLine
import app.necessityq.domain.EstimateException
import app.necessityq.domain.EstimateFailure
import app.necessityq.domain.GenericItemDef
import app.necessityq.domain.ListSuggester
import app.necessityq.domain.QuantityUnit
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The list the AI builds (products and amounts), through the `suggestStarterList` cloud function
 * (firebase/functions/src/starter.ts), which asks Gemini. Only ages, the span and the catalogue are sent.
 */
class FirebaseListSuggester(private val functions: FirebaseFunctions) : ListSuggester {
    override suspend fun suggest(householdId: String, ages: List<Int>, days: Int, catalog: List<GenericItemDef>, exclude: List<String>): AiListAnswer {
        val args = mapOf(
            "householdId" to householdId,
            "ages" to ages,
            "days" to days,
            "catalog" to catalog.map { mapOf("name" to it.name, "category" to it.category.name) },
            "exclude" to exclude,
        )
        val reply = try {
            withTimeoutOrNull(TIMEOUT_MS) { functions.getHttpsCallable("suggestStarterList").call(args).await() }
        } catch (e: FirebaseFunctionsException) {
            throw EstimateException(
                when (e.code) {
                    FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> EstimateFailure.TIMEOUT
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> EstimateFailure.LIMIT
                    FirebaseFunctionsException.Code.UNAUTHENTICATED -> EstimateFailure.SIGNED_OUT
                    FirebaseFunctionsException.Code.UNAVAILABLE -> EstimateFailure.OFFLINE
                    // the server reached the model and the model refused (a bad key, an unknown model, a quota)
                    FirebaseFunctionsException.Code.INTERNAL -> EstimateFailure.MODEL_ERROR
                    FirebaseFunctionsException.Code.FAILED_PRECONDITION, FirebaseFunctionsException.Code.NOT_FOUND -> EstimateFailure.UNAVAILABLE
                    else -> EstimateFailure.ERROR
                },
                e,
                detail = e.message,
            )
        } catch (e: java.io.IOException) {
            throw EstimateException(EstimateFailure.OFFLINE, e)
        }
        val result = reply ?: throw EstimateException(EstimateFailure.TIMEOUT)
        val data = result.data as? Map<*, *> ?: throw EstimateException(EstimateFailure.BAD_REPLY)
        val items = data["items"] as? List<*> ?: throw EstimateException(EstimateFailure.BAD_REPLY)
        val lines = items.mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val name = m["name"] as? String ?: return@mapNotNull null
            val amount = (m["amount"] as? Number)?.toDouble() ?: return@mapNotNull null
            val unit = unitOf(m["unit"] as? String) ?: return@mapNotNull null
            AiListLine(name, amount, unit, (m["reason"] as? String).orEmpty())
        }
        return AiListAnswer(lines, cached = data["cached"] == true, model = data["model"] as? String)
    }

    private fun unitOf(name: String?) = when (name) {
        "piece" -> QuantityUnit.PIECE
        "package" -> QuantityUnit.PACKAGE
        "kilogram" -> QuantityUnit.KILOGRAM
        "gram" -> QuantityUnit.GRAM
        "liter" -> QuantityUnit.LITER
        "milliliter" -> QuantityUnit.MILLILITER
        else -> null
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}
