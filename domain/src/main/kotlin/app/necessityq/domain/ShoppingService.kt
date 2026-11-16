package app.necessityq.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything the list screen needs, recomputed whenever any member changes anything. */
data class ListSnapshot(
    val lines: List<ListLine>,
    val grouped: List<Pair<Category, List<ListLine>>>,
    val bought: List<ListLine>,
    val stats: Map<String, ItemStats>,
    val runningLow: List<Suggestion>,
    val frequent: List<Suggestion>,
)

/**
 * Application service: the use cases of the shopping list. Holds no state of its own —
 * the repository's realtime streams are the single source of truth.
 */
class ShoppingService(
    private val repository: ShoppingRepository,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val statsEngine: ConsumptionStatsEngine = ConsumptionStatsEngine(zone),
    private val suggestionEngine: SuggestionEngine = SuggestionEngine(),
) {
    private fun today(): LocalDate = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()

    fun snapshot(householdId: String): Flow<ListSnapshot> = combine(
        repository.lines(householdId),
        repository.purchases(householdId),
        repository.suppressions(householdId),
    ) { lines, purchases, suppressions ->
        val today = today()
        val stats = statsEngine.compute(purchases, today)
        val needed = lines.filter { it.status == LineStatus.NEEDED }.map { it.itemId }.toSet()
        val suggestions = suggestionEngine.suggest(stats.values, needed, suppressions, today)
        ListSnapshot(
            lines = lines,
            grouped = ListRules.grouped(lines),
            bought = lines.filter { it.status == LineStatus.BOUGHT }.sortedByDescending { it.boughtAtMillis ?: 0 },
            stats = stats,
            runningLow = suggestions.runningLow,
            frequent = suggestions.frequent,
        )
    }

    fun purchases(householdId: String): Flow<List<Purchase>> = repository.purchases(householdId)

    /** Adds a free-text entry ("2 קוטג' 5%"); returns the parsed entry, or null if the text was empty. */
    suspend fun addFromText(householdId: String, userId: String, text: String, current: List<ListLine>): ParsedEntry? {
        val parsed = QuickEntryParser.parse(text) ?: return null
        add(householdId, userId, parsed.item, parsed.quantity, LineSource.MANUAL, current)
        return parsed
    }

    suspend fun add(
        householdId: String,
        userId: String,
        item: ItemIdentity,
        quantity: Quantity?,
        source: LineSource,
        current: List<ListLine>,
    ) {
        val now = clock.nowMillis()
        when (val decision = ListRules.decideAdd(current, item.id, quantity)) {
            is ListRules.AddDecision.Merge ->
                repository.updateLineQuantity(householdId, decision.lineId, decision.quantity, now)
            is ListRules.AddDecision.New -> repository.addLine(
                householdId,
                ListLine(
                    id = ids.newId(),
                    itemId = item.id,
                    name = item.name,
                    variant = item.variant,
                    category = item.category,
                    quantity = decision.quantity,
                    note = null,
                    status = LineStatus.NEEDED,
                    source = source,
                    addedBy = userId,
                    addedAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
        }
    }

    suspend fun acceptSuggestion(householdId: String, userId: String, suggestion: Suggestion, current: List<ListLine>) {
        val source = if (suggestion.kind == SuggestionKind.RUNNING_LOW) LineSource.SUGGESTED else LineSource.QUICK_ADD
        add(householdId, userId, suggestion.identity, suggestion.quantity, source, current)
    }

    suspend fun dismissSuggestion(householdId: String, suggestion: Suggestion, stats: ItemStats?) {
        val until = suggestionEngine.dismissUntil(stats, today())
        repository.suppress(householdId, SuggestionSuppression(suggestion.itemId, until.toEpochDay()))
    }

    suspend fun changeQuantity(householdId: String, line: ListLine, quantity: Quantity?) =
        repository.updateLineQuantity(householdId, line.id, quantity, clock.nowMillis())

    suspend fun changeNote(householdId: String, line: ListLine, note: String?) =
        repository.updateLineNote(householdId, line.id, note?.trim()?.ifBlank { null }, clock.nowMillis())

    suspend fun delete(householdId: String, line: ListLine) = repository.deleteLine(householdId, line.id)

    suspend fun checkOff(householdId: String, userId: String, line: ListLine) {
        val purchase = Purchase(
            id = purchaseIdFor(line.id),
            itemId = line.itemId,
            name = line.name,
            variant = line.variant,
            category = line.category,
            quantity = line.quantity,
            boughtAtMillis = clock.nowMillis(),
            boughtBy = userId,
            lineId = line.id,
        )
        repository.checkOff(householdId, line.id, purchase)
    }

    suspend fun reopen(householdId: String, line: ListLine) =
        repository.reopen(householdId, line.id, purchaseIdFor(line.id), clock.nowMillis())

    suspend fun clearBought(householdId: String, lines: List<ListLine>) =
        repository.clearBought(householdId, lines.filter { it.status == LineStatus.BOUGHT }.map { it.id })

    suspend fun deletePurchase(householdId: String, purchase: Purchase) =
        repository.deletePurchase(householdId, purchase.id)

    /**
     * Loads [TodoSample]: history for the statistics plus the open items as list lines.
     * Safe to run more than once (ids are deterministic; items already on the list are skipped).
     * Returns (lines added, purchases recorded).
     */
    suspend fun importTodoSample(householdId: String, userId: String, current: List<ListLine>): Pair<Int, Int> {
        val onList = current.filter { it.status == LineStatus.NEEDED }.map { it.itemId }.toMutableSet()
        var addedLines = 0
        var recorded = 0
        TodoSample.rows.forEachIndexed { index, row ->
            val parsed = QuickEntryParser.parse(row.title) ?: return@forEachIndexed
            val identity = parsed.item
            if (row.isOpen) {
                if (!onList.add(identity.id)) return@forEachIndexed
                repository.addLine(
                    householdId,
                    ListLine(
                        id = "todo_l$index",
                        itemId = identity.id,
                        name = identity.name,
                        variant = identity.variant,
                        category = identity.category,
                        quantity = parsed.quantity,
                        note = null,
                        status = LineStatus.NEEDED,
                        source = LineSource.MANUAL,
                        addedBy = userId,
                        addedAtMillis = row.atMillis,
                        updatedAtMillis = row.atMillis,
                    ),
                )
                addedLines++
            } else {
                repository.addPurchase(
                    householdId,
                    Purchase(
                        id = "p_todo_$index",
                        itemId = identity.id,
                        name = identity.name,
                        variant = identity.variant,
                        category = identity.category,
                        quantity = parsed.quantity,
                        boughtAtMillis = row.atMillis,
                        boughtBy = userId,
                        lineId = null,
                    ),
                )
                recorded++
            }
        }
        return addedLines to recorded
    }

    companion object {
        /** Deterministic: checking the same line off twice (two members at once) records one purchase. */
        fun purchaseIdFor(lineId: String) = "p_$lineId"
    }
}
