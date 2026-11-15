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
                    reason = runningLowReason(cadence, s.daysSinceLast(today)),
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

    /** How long to hide a dismissed suggestion: half its cadence, at least two days. */
    fun dismissUntil(stats: ItemStats?, today: LocalDate): LocalDate {
        val days = max(2L, ((stats?.cadenceDays ?: 4.0) / 2.0).roundToLong())
        return today.plusDays(days)
    }

    companion object {
        fun runningLowReason(cadenceDays: Double, daysSince: Long): String {
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
            return "בדרך כלל $cadenceText · $sinceText"
        }
    }
}

/** Rules for adding to the list: the same item is consolidated instead of duplicated. */
object ListRules {
    sealed interface AddDecision {
        data class New(val quantity: Quantity?) : AddDecision
        data class Merge(val lineId: String, val quantity: Quantity?) : AddDecision
    }

    fun decideAdd(existing: List<ListLine>, itemId: String, quantity: Quantity?): AddDecision {
        val open = existing.firstOrNull { it.itemId == itemId && it.status == LineStatus.NEEDED }
            ?: return AddDecision.New(quantity)
        val current = open.quantity
        val merged: Quantity? = if (current == null) {
            quantity
        } else if (quantity == null) {
            current
        } else {
            (current + quantity) ?: quantity // different dimensions: the newer entry wins
        }
        return AddDecision.Merge(open.id, merged)
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
