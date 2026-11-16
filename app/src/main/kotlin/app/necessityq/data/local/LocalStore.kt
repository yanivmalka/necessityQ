package app.necessityq.data.local

import app.necessityq.domain.Category
import app.necessityq.domain.Household
import app.necessityq.domain.HouseholdRepository
import app.necessityq.domain.InMemoryShoppingRepository
import app.necessityq.domain.LineSource
import app.necessityq.domain.LineStatus
import app.necessityq.domain.ListLine
import app.necessityq.domain.LocalState
import app.necessityq.domain.Purchase
import app.necessityq.domain.Quantity
import app.necessityq.domain.QuantityUnit
import app.necessityq.domain.Session
import app.necessityq.domain.SuggestionSuppression
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

@Serializable
internal data class QuantityDto(val amount: Double, val unit: String)

@Serializable
internal data class LineDto(
    val id: String, val itemId: String, val name: String, val variant: String? = null,
    val category: String, val quantity: QuantityDto? = null, val note: String? = null,
    val status: String, val source: String, val addedBy: String,
    val addedAt: Long, val updatedAt: Long, val boughtAt: Long? = null,
    val position: Long = 0, val missing: Boolean = false, val recipe: String? = null,
    val removedAt: Long? = null, val removedReason: String? = null, val removedFrom: String? = null,
)

@Serializable
internal data class PurchaseDto(
    val id: String, val itemId: String, val name: String, val variant: String? = null,
    val category: String, val quantity: QuantityDto? = null, val boughtAt: Long,
    val boughtBy: String, val lineId: String? = null,
)

@Serializable
internal data class StateDto(
    val householdName: String? = null,
    val lines: List<LineDto> = emptyList(),
    val purchases: List<PurchaseDto> = emptyList(),
    val suppressions: Map<String, Long> = emptyMap(),
)

/**
 * Single-device mode used when Firebase is not configured. State lives in memory (reactive)
 * and is written to a JSON file in the app's private storage after every change.
 */
class LocalStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val writer = Executors.newSingleThreadExecutor()

    private val initial: StateDto = runCatching { json.decodeFromString<StateDto>(file.readText()) }.getOrDefault(StateDto())
    private val householdName = MutableStateFlow(initial.householdName)

    val shopping: InMemoryShoppingRepository = InMemoryShoppingRepository(initial.toDomain()) { persist() }

    val household: HouseholdRepository = object : HouseholdRepository {
        override val isShared = false
        override val session: Flow<Session> = householdName.map { name ->
            Session.SignedIn(LOCAL_USER, name?.let { Household(LOCAL_HOUSEHOLD, it, "", listOf(LOCAL_USER)) })
        }

        override suspend fun createHousehold(name: String): Household {
            householdName.value = name.trim().ifBlank { "הבית שלנו" }
            persist()
            return Household(LOCAL_HOUSEHOLD, householdName.value!!, "", listOf(LOCAL_USER))
        }

        override suspend fun joinHousehold(code: String): Household =
            error("הצטרפות לבית משותף דורשת חיבור לשרת (Firebase). ראו README.")

        override suspend fun leaveHousehold() {
            householdName.value = null
            persist()
        }
    }

    private fun persist() {
        val snapshot = StateDto(
            householdName = householdName.value,
            lines = shopping.current.value.lines.values.map { it.toDto() },
            purchases = shopping.current.value.purchases.values.map { it.toDto() },
            suppressions = shopping.current.value.suppressions.mapValues { it.value.untilEpochDay },
        )
        writer.execute {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(StateDto.serializer(), snapshot))
            tmp.renameTo(file)
        }
    }

    companion object {
        const val LOCAL_USER = "local-user"
        const val LOCAL_HOUSEHOLD = "local"
    }
}

private fun Quantity.toDto() = QuantityDto(amount, unit.name)
private fun QuantityDto.toDomain(): Quantity? =
    runCatching { Quantity(amount, QuantityUnit.valueOf(unit)) }.getOrNull()

private fun category(name: String) = runCatching { Category.valueOf(name) }.getOrDefault(Category.OTHER)

private fun ListLine.toDto() = LineDto(
    id, itemId, name, variant, category.name, quantity?.toDto(), note, status.name, source.name,
    addedBy, addedAtMillis, updatedAtMillis, boughtAtMillis,
    position, missing, recipe, removedAtMillis, removedReason?.name, removedFrom?.name,
)

private fun Purchase.toDto() = PurchaseDto(
    id, itemId, name, variant, category.name, quantity?.toDto(), boughtAtMillis, boughtBy, lineId,
)

private fun StateDto.toDomain(): LocalState = LocalState(
    lines = lines.associate { d ->
        d.id to ListLine(
            id = d.id, itemId = d.itemId, name = d.name, variant = d.variant, category = category(d.category),
            quantity = d.quantity?.toDomain(), note = d.note,
            status = runCatching { LineStatus.valueOf(d.status) }.getOrDefault(LineStatus.NEEDED),
            source = runCatching { LineSource.valueOf(d.source) }.getOrDefault(LineSource.MANUAL),
            addedBy = d.addedBy, addedAtMillis = d.addedAt, updatedAtMillis = d.updatedAt, boughtAtMillis = d.boughtAt,
            position = d.position, missing = d.missing, recipe = d.recipe, removedAtMillis = d.removedAt,
            removedReason = d.removedReason?.let { runCatching { app.necessityq.domain.RemoveReason.valueOf(it) }.getOrNull() },
            removedFrom = d.removedFrom?.let { runCatching { LineStatus.valueOf(it) }.getOrNull() },
        )
    },
    purchases = purchases.associate { d ->
        d.id to Purchase(
            id = d.id, itemId = d.itemId, name = d.name, variant = d.variant, category = category(d.category),
            quantity = d.quantity?.toDomain(), boughtAtMillis = d.boughtAt, boughtBy = d.boughtBy, lineId = d.lineId,
        )
    },
    suppressions = suppressions.mapValues { (itemId, until) -> SuggestionSuppression(itemId, until) },
)
