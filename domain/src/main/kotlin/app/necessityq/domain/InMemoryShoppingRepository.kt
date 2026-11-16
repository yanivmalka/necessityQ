package app.necessityq.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Whole state of one device-local household. Used by the single-device mode and by tests. */
data class LocalState(
    val lines: Map<String, ListLine> = emptyMap(),
    val purchases: Map<String, Purchase> = emptyMap(),
    val suppressions: Map<String, SuggestionSuppression> = emptyMap(),
)

/**
 * Reactive in-memory implementation of [ShoppingRepository]. [onChange] is invoked after every
 * write so a caller can persist the state (the Android local mode writes it to a file).
 */
open class InMemoryShoppingRepository(
    initial: LocalState = LocalState(),
    private val onChange: (LocalState) -> Unit = {},
) : ShoppingRepository {

    private val state = MutableStateFlow(initial)
    val current: StateFlow<LocalState> = state.asStateFlow()

    private fun mutate(block: (LocalState) -> LocalState) {
        state.update(block)
        onChange(state.value)
    }

    override fun lines(householdId: String): Flow<List<ListLine>> = state.map { it.lines.values.toList() }
    override fun purchases(householdId: String): Flow<List<Purchase>> = state.map { it.purchases.values.toList() }
    override fun suppressions(householdId: String): Flow<List<SuggestionSuppression>> =
        state.map { it.suppressions.values.toList() }

    override suspend fun addLine(householdId: String, line: ListLine) =
        mutate { it.copy(lines = it.lines + (line.id to line)) }

    override suspend fun updateLineQuantity(householdId: String, lineId: String, quantity: Quantity?, atMillis: Long) =
        mutate { s ->
            val line = s.lines[lineId] ?: return@mutate s
            s.copy(lines = s.lines + (lineId to line.copy(quantity = quantity, updatedAtMillis = atMillis)))
        }

    override suspend fun updateLineNote(householdId: String, lineId: String, note: String?, atMillis: Long) =
        mutate { s ->
            val line = s.lines[lineId] ?: return@mutate s
            s.copy(lines = s.lines + (lineId to line.copy(note = note, updatedAtMillis = atMillis)))
        }

    override suspend fun deleteLine(householdId: String, lineId: String) =
        mutate { it.copy(lines = it.lines - lineId) }

    override suspend fun checkOff(householdId: String, lineId: String, purchase: Purchase) =
        mutate { s ->
            val line = s.lines[lineId] ?: return@mutate s
            s.copy(
                lines = s.lines + (lineId to line.copy(
                    status = LineStatus.BOUGHT,
                    boughtAtMillis = purchase.boughtAtMillis,
                    updatedAtMillis = purchase.boughtAtMillis,
                )),
                purchases = s.purchases + (purchase.id to purchase),
            )
        }

    override suspend fun reopen(householdId: String, lineId: String, purchaseId: String, atMillis: Long) =
        mutate { s ->
            val line = s.lines[lineId] ?: return@mutate s
            s.copy(
                lines = s.lines + (lineId to line.copy(status = LineStatus.NEEDED, boughtAtMillis = null, updatedAtMillis = atMillis)),
                purchases = s.purchases - purchaseId,
            )
        }

    override suspend fun clearBought(householdId: String, lineIds: List<String>) =
        mutate { it.copy(lines = it.lines - lineIds.toSet()) }

    override suspend fun addPurchase(householdId: String, purchase: Purchase) =
        mutate { it.copy(purchases = it.purchases + (purchase.id to purchase)) }

    override suspend fun deletePurchase(householdId: String, purchaseId: String) =
        mutate { it.copy(purchases = it.purchases - purchaseId) }

    override suspend fun moveLine(householdId: String, lineId: String, category: Category, position: Long, atMillis: Long) =
        mutate { s ->
            val line = s.lines[lineId] ?: return@mutate s
            s.copy(lines = s.lines + (lineId to line.copy(category = category, position = position, updatedAtMillis = atMillis)))
        }

    override suspend fun setMissing(householdId: String, lineId: String, missing: Boolean, byUserId: String?, atMillis: Long) =
        mutate { s ->
            val line = s.lines[lineId] ?: return@mutate s
            s.copy(lines = s.lines + (lineId to line.copy(missing = missing, missingBy = byUserId.takeIf { missing }, updatedAtMillis = atMillis)))
        }

    override suspend fun suppress(householdId: String, suppression: SuggestionSuppression) =
        mutate { it.copy(suppressions = it.suppressions + (suppression.itemId to suppression)) }
}
