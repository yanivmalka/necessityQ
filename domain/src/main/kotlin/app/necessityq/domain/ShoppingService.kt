package app.necessityq.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    /** The list for the next trip, built from the household's own rhythm (see [SuggestionEngine.forNextTrip]). */
    val tripList: List<Suggestion> = emptyList(),
    /** How many of the suggested lines were bought or thrown away. */
    val accuracy: SuggestionAccuracy = SuggestionAccuracy(0, 0, 0),
    /** Per item: how often its suggestion was dismissed or deleted unbought in the last two months. */
    val rejections: Map<String, Int> = emptyMap(),
    /** Recycle bin: deleted lines and bought lines that were cleared, newest first. */
    val removed: List<ListLine> = emptyList(),
    /** Items whose suggestion was recently turned down (hidden until their date). */
    val suppressed: Set<String> = emptySet(),
    /** Of [suppressed], the items hidden because the household has them at home ("יש לי בבית"). */
    val atHome: Set<String> = emptySet(),
    /** The size of the household the amounts were worked out for ([HouseholdSize.people]); 0 when unknown. */
    val people: Double = 0.0,
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

    /** Per household, how many people it is ([HouseholdSize.people], from its family profiles); kept by the app. */
    private val people = MutableStateFlow<Map<String, Double>>(emptyMap())

    /** The size of [householdId] (0 when unknown): the amounts of suggestions follow it, and purchases record it. */
    fun setHouseholdPeople(householdId: String, people: Double) {
        this.people.value = this.people.value + (householdId to people.coerceAtLeast(0.0))
    }

    fun householdPeople(householdId: String): Double = people.value[householdId] ?: 0.0

    /**
     * Purchases recorded before the household's size was (older versions) count as made at the size it has now, the
     * first time it is known: from then on, a household that grows or shrinks gets amounts that follow it at once,
     * without waiting for new purchases. Nothing to do once every purchase has a size. Returns how many were updated.
     */
    suspend fun backfillHouseholdPeople(householdId: String, people: Double): Int {
        if (people <= 0.0) return 0
        val missing = repository.purchases(householdId).first().filter { it.householdPeople == null }.map { it.id }
        if (missing.isNotEmpty()) repository.setPurchasesPeople(householdId, missing, people)
        return missing.size
    }

    /** The "יש לי בבית" marks of an item that still count (the last one in the last two months), or 0. */
    private fun atHomeMarks(s: SuggestionSuppression?, today: LocalDate): Int {
        val marked = s?.markedEpochDay ?: return 0
        return if (marked >= today.toEpochDay() - REJECTION_MEMORY_MILLIS / 86_400_000L) s.atHomeMarks else 0
    }

    private suspend fun suppressionOf(householdId: String, itemId: String): SuggestionSuppression? =
        repository.suppressions(householdId).first().firstOrNull { it.itemId == itemId }

    fun snapshot(householdId: String): Flow<ListSnapshot> = combine(
        repository.lines(householdId),
        repository.purchases(householdId),
        repository.suppressions(householdId),
        people.map { it[householdId] ?: 0.0 }.distinctUntilChanged(),
    ) { lines, allPurchases, suppressions, people ->
        val today = today()
        val purchases = counted(allPurchases, lines)
        val stretch = suppressions.associate { it.itemId to ConsumptionStatsEngine.atHomeStretch(atHomeMarks(it, today)) }
            .filterValues { it > 1.0 }
        val stats = statsEngine.compute(purchases, today, stretch)
        val needed = lines.filter { it.status == LineStatus.NEEDED }.map { it.itemId }.toSet()
        val rejections = rejections(lines, suppressions, today)
        val suggestions = suggestionEngine.suggest(stats.values, needed, suppressions, today, rejections)
        val active = suppressions.filter { it.untilEpochDay > today.toEpochDay() }
        val sizer = TripSizer(stats.values, people)
        // a household with no history yet: a first list from its size
        val tripList = if (stats.isEmpty()) sizer.starter(needed + active.map { it.itemId })
        else suggestionEngine.forNextTrip(stats.values, needed, suppressions, today, TRIP_DAYS, rejections = rejections)
            .map { sizer.size(it, TRIP_DAYS) }
        ListSnapshot(
            lines = lines,
            grouped = ListRules.grouped(lines),
            bought = lines.filter { it.status == LineStatus.BOUGHT }.sortedByDescending { it.boughtAtMillis ?: 0 },
            stats = stats,
            runningLow = suggestions.runningLow.map { sizer.size(it) },
            frequent = suggestions.frequent.map { sizer.size(it) },
            tripList = tripList,
            accuracy = SuggestionAccuracy.of(lines),
            rejections = rejections,
            removed = lines.filter { it.status == LineStatus.REMOVED }.sortedByDescending { it.removedAtMillis ?: 0 },
            suppressed = active.map { it.itemId }.toSet(),
            atHome = active.filter { it.atHome }.map { it.itemId }.toSet(),
            people = people,
        )
    }

    /** What the household threw away without buying, in the last two months: it teaches the suggestions. */
    private fun rejections(lines: List<ListLine>, suppressions: List<SuggestionSuppression>, today: LocalDate): Map<String, Int> {
        val fromLines = SuggestionAccuracy.rejectionsByItem(lines, clock.nowMillis() - REJECTION_MEMORY_MILLIS)
        // a dismissal is forgiven two months after its hiding ended
        val forgiven = today.toEpochDay() - REJECTION_MEMORY_MILLIS / 86_400_000L
        val fromDismissals = suppressions.filter { it.untilEpochDay >= forgiven }.associate { it.itemId to it.dismissals }
        return (fromLines.keys + fromDismissals.keys).associateWith { maxOf(fromLines[it] ?: 0, fromDismissals[it] ?: 0) }
    }

    /** The purchases that count: those of items the household deleted are left out (they show under "removed"). */
    fun purchases(householdId: String): Flow<List<Purchase>> =
        combine(repository.purchases(householdId), repository.lines(householdId), ::counted)


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
        requestedQuantity: Quantity?,
        source: LineSource,
        current: List<ListLine>,
        recipe: String? = null,
    ) {
        // far beyond any home purchase of this item (750 יח' of cheese) is a slip: made usual. A merely large
        // amount is kept and flagged on the list until someone confirms it (QuantityNorms.warningFor).
        // a recipe's "בצל" with no amount is one onion: two recipes then need two
        val asked = requestedQuantity ?: if (recipe != null) Recipes.defaultAmount(item) else null
        val quantity = QuantityNorms.homeSized(item.name, item.category, asked)
        val now = clock.nowMillis()
        val part = recipe?.let { RecipePart(it, quantity, fromMenu = source == LineSource.MENU) }
        when (val decision = ListRules.decideAdd(current, item.id, quantity, item.name)) {
            is ListRules.AddDecision.Merge -> {
                repository.updateLineQuantity(householdId, decision.lineId, decision.quantity, now)
                // the existing line now also serves this dish: say so, and how much the dish needs
                if (part != null) {
                    val line = current.firstOrNull { it.id == decision.lineId }
                    repository.updateLineRecipes(
                        householdId, decision.lineId, line?.recipe ?: recipe,
                        RecipeParts.merge(line?.recipeParts.orEmpty(), part), now,
                    )
                }
            }
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
                    recipe = recipe,
                    recipeParts = listOfNotNull(part),
                ),
            )
        }
    }

    /** Replaces the dishes a line serves (after a recipe was taken off the list); no dishes left = no recipe name. */
    suspend fun setRecipeParts(householdId: String, line: ListLine, parts: List<RecipePart>) =
        repository.updateLineRecipes(
            householdId, line.id,
            line.recipe?.takeIf { r -> parts.any { it.dish == r } } ?: parts.firstOrNull()?.dish,
            parts, clock.nowMillis(),
        )

    /** Sets how much of a line one dish needs (after its servings changed). */
    suspend fun setRecipePart(householdId: String, line: ListLine, part: RecipePart) =
        repository.updateLineRecipes(householdId, line.id, line.recipe ?: part.dish, RecipeParts.replace(line.recipeParts, part), clock.nowMillis())

    suspend fun acceptSuggestion(householdId: String, userId: String, suggestion: Suggestion, current: List<ListLine>) {
        val source = if (suggestion.kind == SuggestionKind.RUNNING_LOW) LineSource.SUGGESTED else LineSource.QUICK_ADD
        add(householdId, userId, suggestion.identity, suggestion.quantity, source, current)
    }

    /** One tap: puts the whole trip list on the shopping list. Returns how many items were added. */
    suspend fun addTripList(householdId: String, userId: String, items: List<Suggestion>, current: List<ListLine>): Int {
        items.forEach { acceptSuggestion(householdId, userId, it, current) }
        return items.size
    }

    /** "יש לי בבית": hides the suggestion for a whole cadence without counting as a rejection. */
    suspend fun haveAtHome(householdId: String, suggestion: Suggestion, stats: ItemStats?, priorRejections: Int = 0) {
        val today = today()
        val until = suggestionEngine.haveAtHomeUntil(stats, today)
        val marks = atHomeMarks(suppressionOf(householdId, suggestion.itemId), today) + 1
        repository.suppress(
            householdId,
            SuggestionSuppression(
                suggestion.itemId, until.toEpochDay(), priorRejections,
                atHome = true, atHomeMarks = marks, markedEpochDay = today.toEpochDay(),
            ),
        )
    }

    suspend fun dismissSuggestion(
        householdId: String, suggestion: Suggestion, stats: ItemStats?, priorRejections: Int = 0,
    ) {
        val until = suggestionEngine.dismissUntil(stats, today(), priorRejections)
        val before = suppressionOf(householdId, suggestion.itemId)
        repository.suppress(
            householdId,
            SuggestionSuppression(
                suggestion.itemId, until.toEpochDay(), priorRejections + 1,
                atHomeMarks = before?.atHomeMarks ?: 0, markedEpochDay = before?.markedEpochDay,
            ),
        )
    }

    suspend fun changeQuantity(householdId: String, line: ListLine, quantity: Quantity?) =
        repository.updateLineQuantity(householdId, line.id, quantity, clock.nowMillis())

    /** Sets the amount and confirms it is meant, even though it is more than usual for the item. */
    suspend fun changeQuantityApproved(householdId: String, line: ListLine, quantity: Quantity) {
        val now = clock.nowMillis()
        repository.updateLineQuantity(householdId, line.id, quantity, now)
        repository.approveLineQuantity(householdId, line.id, quantity, now)
    }

    /**
     * Renames a line from free text ("חלב 3%"). Returns the new identity, or null when the text is empty
     * or names the same item. A quantity typed with the name is ignored (it has its own control).
     */
    suspend fun rename(householdId: String, line: ListLine, text: String): ItemIdentity? {
        val parsed = QuickEntryParser.parse(text) ?: return null
        val item = parsed.item
        if (item.id == line.itemId && item.displayName == line.displayName) return null
        // keep the category the household moved it to when the item stays the same kind
        val category = if (item.category == Category.OTHER) line.category else item.category
        val renamed = item.copy(category = category)
        repository.renameLine(householdId, line.id, renamed, clock.nowMillis())
        return renamed
    }

    suspend fun changeNote(householdId: String, line: ListLine, note: String?) =
        repository.updateLineNote(householdId, line.id, note?.trim()?.ifBlank { null }, clock.nowMillis())

    /** Moves the line to the recycle bin. */
    suspend fun delete(householdId: String, line: ListLine) =
        repository.removeLines(householdId, listOf(line), RemoveReason.DELETED, clock.nowMillis())

    /** Back from the bin: to the list, or (undo of "clear") back to bought. */
    suspend fun restore(householdId: String, line: ListLine, status: LineStatus = LineStatus.NEEDED) =
        repository.restoreLine(householdId, line.id, status, clock.nowMillis())

    /** Empties the bin for good. */
    suspend fun purge(householdId: String, lines: List<ListLine>) =
        repository.purgeLines(householdId, lines.filter { it.status == LineStatus.REMOVED }.map { it.id })

    suspend fun adjustQuantity(householdId: String, line: ListLine, delta: Int) {
        val next = QuantitySteps.step(line.quantity, delta)
        if (next != line.quantity) changeQuantity(householdId, line, next)
    }

    /**
     * Persists a drag: [category] is the group it was dropped in; [previous]/[next] are the lines now
     * directly above and below it in that group (null at the ends).
     */
    suspend fun moveLine(householdId: String, line: ListLine, category: Category, previous: ListLine?, next: ListLine?) {
        val position = ListOrdering.positionBetween(previous?.orderKey, next?.orderKey)
        repository.moveLine(householdId, line.id, category, position, clock.nowMillis())
    }

    suspend fun setMissing(householdId: String, userId: String, line: ListLine, missing: Boolean) =
        repository.setMissing(householdId, line.id, missing, userId, clock.nowMillis())

    suspend fun checkOff(householdId: String, userId: String, line: ListLine, storeId: String? = null) {
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
            storeId = storeId,
            householdPeople = householdPeople(householdId).takeIf { it > 0.0 },
        )
        repository.checkOff(householdId, line.id, purchase)
    }

    suspend fun reopen(householdId: String, line: ListLine) =
        repository.reopen(householdId, line.id, purchaseIdFor(line.id), clock.nowMillis())

    suspend fun clearBought(householdId: String, lines: List<ListLine>) =
        repository.removeLines(householdId, lines.filter { it.status == LineStatus.BOUGHT }, RemoveReason.CLEARED, clock.nowMillis())

    /**
     * Adds the confirmed items of a generated list ([ListGenerator]) as [LineSource.AUTO] lines.
     * Returns the new line ids (for undo). Items already open on the list are skipped.
     */
    suspend fun addGenerated(householdId: String, userId: String, items: List<GeneratedItem>, current: List<ListLine>): List<String> {
        val onList = current.filter { it.status == LineStatus.NEEDED }.map { it.itemId }.toMutableSet()
        val now = clock.nowMillis()
        val added = mutableListOf<String>()
        items.forEachIndexed { index, item ->
            if (!onList.add(item.identity.id)) return@forEachIndexed
            val id = ids.newId()
            repository.addLine(
                householdId,
                ListLine(
                    id = id,
                    itemId = item.identity.id,
                    name = item.identity.name,
                    variant = item.identity.variant,
                    category = item.identity.category,
                    quantity = QuantityNorms.homeSized(item.identity.name, item.identity.category, item.quantity),
                    note = null,
                    status = LineStatus.NEEDED,
                    source = LineSource.AUTO,
                    addedBy = userId,
                    // keep the proposal's order as the order of adding
                    addedAtMillis = now + index,
                    updatedAtMillis = now,
                ),
            )
            added += id
        }
        return added
    }

    /**
     * Learning from a proposal: items that were preselected but the household took off before adding are treated
     * like a dismissed suggestion (hidden for a while, and not preselected again in "מקניות עבר").
     */
    /**
     * Preselected items the household took off a generated list: like dismissing the suggestion, each one counts on
     * top of the earlier ones ([rejections]), so it is hidden longer every time and given up after the third.
     */
    suspend fun rejectGenerated(
        householdId: String, items: List<GeneratedItem>, stats: Map<String, ItemStats>, rejections: Map<String, Int> = emptyMap(),
    ) {
        items.distinctBy { it.identity.id }.forEach { item ->
            val prior = rejections[item.identity.id] ?: 0
            val until = suggestionEngine.dismissUntil(stats[item.identity.id], today(), prior)
            val before = suppressionOf(householdId, item.identity.id)
            repository.suppress(
                householdId,
                SuggestionSuppression(
                    item.identity.id, until.toEpochDay(), prior + 1,
                    atHomeMarks = before?.atHomeMarks ?: 0, markedEpochDay = before?.markedEpochDay,
                ),
            )
        }
    }

    /** Undo of [addGenerated]: the lines never really existed, so they are removed for good. */
    suspend fun undoGenerated(householdId: String, lineIds: List<String>) =
        repository.purgeLines(householdId, lineIds)

    /** Starts a fresh list: the lines in [scope] go to the recycle bin (restorable). Returns them for undo. */
    suspend fun resetList(householdId: String, scope: ResetScope, lines: List<ListLine>): List<ListLine> {
        val targets = ListReset.linesFor(scope, lines)
        if (targets.isNotEmpty()) repository.removeLines(householdId, targets, RemoveReason.RESET, clock.nowMillis())
        return targets
    }

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
                        id = "${Purchase.TODO_PURCHASE_PREFIX}$index",
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
        /** The week's list looks this many days ahead (to the trip after the next one). */
        const val TRIP_DAYS = 7
        private const val REJECTION_MEMORY_MILLIS = 60L * 86_400_000L

        /** Deterministic: checking the same line off twice (two members at once) records one purchase. */
        fun purchaseIdFor(lineId: String) = "p_$lineId"

        /**
         * Purchases of lines that were deleted (reason [RemoveReason.DELETED]) do not count in the history or the
         * statistics. Clearing after a purchase or resetting the list keeps them: the item really was bought.
         */
        fun counted(purchases: List<Purchase>, lines: List<ListLine>): List<Purchase> {
            val deleted = lines
                .filter { it.status == LineStatus.REMOVED && it.removedReason == RemoveReason.DELETED }
                .map { it.id }.toSet()
            return if (deleted.isEmpty()) purchases else purchases.filter { it.lineId !in deleted }
        }
    }
}
