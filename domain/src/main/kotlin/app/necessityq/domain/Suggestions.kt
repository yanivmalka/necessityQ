package app.necessityq.domain

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class SuggestionKind { RUNNING_LOW, FREQUENT }

data class Suggestion(
    val itemId: String,
    val identity: ItemIdentity,
    val kind: SuggestionKind,
    val quantity: Quantity?,
    val score: Double,
    val reason: String,
)

/**
 * Turns statistics into suggestions. The list stays user-owned: suggestions are only shown,
 * never added automatically.
 *
 *  - RUNNING_LOW: the item has a cadence and we are ≥ [dueThreshold] through its interval.
 *    score = confidence × min(dueRatio, 2)
 *  - FREQUENT: the household's most frequently bought items that are not on the list — the
 *    one-tap "quick add" chips. score = recency-weighted frequency.
 */
class SuggestionEngine(
    private val dueThreshold: Double = 0.8,
    private val maxRunningLow: Int = 8,
    private val maxFrequent: Int = 12,
) {

    data class Result(val runningLow: List<Suggestion>, val frequent: List<Suggestion>)

    fun suggest(
        stats: Collection<ItemStats>,
        neededItemIds: Set<String>,
        suppressions: List<SuggestionSuppression>,
        today: LocalDate,
    ): Result {
        val todayEpoch = today.toEpochDay()
        val suppressed = suppressions.filter { it.untilEpochDay > todayEpoch }.map { it.itemId }.toSet()
        val candidates = stats.filter { it.itemId !in neededItemIds }

        val runningLow = candidates
            .filter { it.itemId !in suppressed }
            .mapNotNull { s ->
                val ratio = s.dueRatio(today) ?: return@mapNotNull null
                val cadence = s.cadenceDays ?: return@mapNotNull null
                if (ratio < dueThreshold || s.confidence <= 0.0) return@mapNotNull null
                Suggestion(
                    itemId = s.itemId,
                    identity = s.identity,
                    kind = SuggestionKind.RUNNING_LOW,
                    quantity = s.typicalQuantity,
                    score = s.confidence * ratio.coerceAtMost(2.0),
                    reason = runningLowReason(cadence, s.daysSinceLast(today), s.lastPurchaseEstimated),
                )
            }
            .sortedByDescending { it.score }
            .take(maxRunningLow)

        val lowIds = runningLow.map { it.itemId }.toSet()
        val frequent = candidates
            .filter { it.itemId !in lowIds }
            .sortedByDescending { it.frequencyScore }
            .take(maxFrequent)
            .map { s ->
                Suggestion(
                    itemId = s.itemId,
                    identity = s.identity,
                    kind = SuggestionKind.FREQUENT,
                    quantity = s.typicalQuantity,
                    score = s.frequencyScore,
                    reason = "נקנה ${s.purchaseDays} פעמים",
                )
            }

        return Result(runningLow, frequent)
    }

    /**
     * The list for the next trip, built from the household's real rhythm and not from defaults: every item with a
     * measured cadence that will run out before the trip after this one (today + [horizonDays]), most urgent first.
     * One-off items and items whose rhythm is still a guess ([minConfidence]) are left out, as are items already on
     * the list or hidden by a dismissal. The quantity is the typical size of a purchase.
     */
    fun forNextTrip(
        stats: Collection<ItemStats>,
        neededItemIds: Set<String>,
        suppressions: List<SuggestionSuppression>,
        today: LocalDate,
        horizonDays: Int = 7,
        minConfidence: Double = 0.4,
        limit: Int = 40,
    ): List<Suggestion> {
        val todayEpoch = today.toEpochDay()
        val suppressed = suppressions.filter { it.untilEpochDay > todayEpoch }.map { it.itemId }.toSet()
        val until = today.plusDays(horizonDays.toLong())
        return stats
            .filter { it.itemId !in neededItemIds && it.itemId !in suppressed }
            .filter { it.classification != Classification.ONE_OFF && it.confidence >= minConfidence }
            .mapNotNull { s ->
                val cadence = s.cadenceDays ?: return@mapNotNull null
                val due = s.nextDue ?: return@mapNotNull null
                if (!due.isBefore(until)) return@mapNotNull null
                val overdue = (today.toEpochDay() - due.toEpochDay()).toDouble() / cadence
                Suggestion(
                    itemId = s.itemId,
                    identity = s.identity,
                    kind = SuggestionKind.RUNNING_LOW,
                    quantity = s.typicalQuantity,
                    // overdue items first, then the ones due soonest; confidence breaks ties
                    score = overdue.coerceIn(-1.0, 2.0) + s.confidence,
                    reason = runningLowReason(cadence, s.daysSinceLast(today), s.lastPurchaseEstimated),
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /** How long to hide a dismissed suggestion: half its cadence, at least two days. */
    fun dismissUntil(stats: ItemStats?, today: LocalDate): LocalDate {
        val days = max(2L, ((stats?.cadenceDays ?: 4.0) / 2.0).roundToLong())
        return today.plusDays(days)
    }

    companion object {
        /** [estimated]: the last purchase is inferred, because later trips were not documented. */
        fun runningLowReason(cadenceDays: Double, daysSince: Long, estimated: Boolean = false): String {
            val every = cadenceDays.roundToInt().coerceAtLeast(1)
            val cadenceText = when (every) {
                1 -> "כל יום"
                2 -> "כל יומיים"
                7 -> "כל שבוע"
                14 -> "כל שבועיים"
                else -> "כל $every ימים"
            }
            val sinceText = when (daysSince) {
                0L -> "נקנה היום"
                1L -> "נקנה אתמול"
                2L -> "נקנה לפני יומיים"
                else -> "נקנה לפני $daysSince ימים"
            }
            return if (estimated) "בדרך כלל $cadenceText · כנראה $sinceText (לא תועד)" else "בדרך כלל $cadenceText · $sinceText"
        }
    }
}

/** Rules for adding to the list: the same item is consolidated instead of duplicated. */
object ListRules {
    sealed interface AddDecision {
        data class New(val quantity: Quantity?) : AddDecision
        data class Merge(val lineId: String, val quantity: Quantity?) : AddDecision
    }

    /** [name]: the item's name, so pieces and a weight of produce can be added up (see [combine]). */
    fun decideAdd(existing: List<ListLine>, itemId: String, quantity: Quantity?, name: String? = null): AddDecision {
        val open = existing.firstOrNull { it.itemId == itemId && it.status == LineStatus.NEEDED }
            ?: return AddDecision.New(quantity)
        return AddDecision.Merge(open.id, combine(name ?: open.name, open.quantity, quantity))
    }

    /**
     * [current] and [added] together, for an item called [name]. Different dimensions are added up through the
     * average weight of a piece when it is known ("2 בצל" and "300 גרם בצל" → 520 גרם, in the unit already on the
     * list); otherwise the newer entry wins.
     */
    fun combine(name: String, current: Quantity?, added: Quantity?): Quantity? {
        if (current == null) return added
        if (added == null) return current
        (current + added)?.let { return it }
        val converted = PieceWeights.convert(name, added, current.dimension) ?: return added
        return (current + converted) ?: added
    }

    /** Lines grouped by category in store-walk order, needed first, bought at the bottom. */
    fun grouped(lines: List<ListLine>): List<Pair<Category, List<ListLine>>> =
        lines.filter { it.status == LineStatus.NEEDED }
            .groupBy { it.category }
            .toSortedMap(compareBy { it.order })
            .map { (category, items) -> category to items.sortedBy { it.addedAtMillis } }
}

/** Autocomplete for the quick-add box: the household's own history first, then the catalogue. */
object Autocomplete {
    fun complete(query: String, stats: Collection<ItemStats>, limit: Int = 6): List<ItemIdentity> {
        val q = HebrewText.matchKey(query)
        if (q.length < 1) return emptyList()
        val fromHistory = stats
            .filter { HebrewText.matchKey(it.identity.displayName).contains(q) }
            .sortedWith(
                compareByDescending<ItemStats> { HebrewText.matchKey(it.identity.displayName).startsWith(q) }
                    .thenByDescending { it.frequencyScore },
            )
            .map { it.identity }
        val fromCatalog = Catalog.search(query, limit).map { ItemIdentity(it.name, null, it.category) }
        return (fromHistory + fromCatalog).distinctBy { it.id }.take(limit)
    }
}
