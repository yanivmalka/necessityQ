package app.necessityq.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

enum class Classification { ROUTINE, OCCASIONAL, ONE_OFF }

/** Everything the statistics engine knows about one generic item for one household. */
data class ItemStats(
    val itemId: String,
    val identity: ItemIdentity,
    val purchaseDays: Int,
    val firstPurchase: LocalDate,
    /** The last documented purchase. */
    val lastPurchase: LocalDate,
    /**
     * Best estimate of the real last purchase. Equals [lastPurchase] unless shopping after it went partly
     * undocumented (the To Do era); then it is projected along the item's rhythm (see [TodoCoverage]).
     */
    val estimatedLastPurchase: LocalDate = lastPurchase,
    /** Recency-weighted median days between purchases, normalised to a typical-size purchase. */
    val cadenceDays: Double?,
    /** Median absolute deviation of the interval, in days. */
    val spreadDays: Double?,
    val typicalQuantity: Quantity?,
    val lastQuantity: Quantity?,
    val nextDue: LocalDate?,
    val confidence: Double,
    val classification: Classification,
    /** Recency-weighted purchase frequency, used to rank quick-add chips. */
    val frequencyScore: Double,
    /** How many people the household was when it bought [typicalQuantity] (median); null when not recorded. */
    val typicalPeople: Double? = null,
) {
    /** True when the last purchase is an estimate because later trips were not documented. */
    val lastPurchaseEstimated: Boolean get() = estimatedLastPurchase != lastPurchase

    fun daysSinceLast(today: LocalDate): Long = ChronoUnit.DAYS.between(estimatedLastPurchase, today)

    /** How far through the expected interval we are: 1.0 = due today. */
    fun dueRatio(today: LocalDate): Double? {
        val due = nextDue ?: return null
        val expected = ChronoUnit.DAYS.between(estimatedLastPurchase, due).coerceAtLeast(1)
        return daysSinceLast(today).toDouble() / expected
    }
}

/**
 * Transparent, explainable consumption statistics (no ML):
 *
 *  1. Purchases are grouped per item and per calendar day (two check-offs on the same day = one trip).
 *  2. Each interval between trips is divided by the relative size of the purchase that started it,
 *     so a double-size stock-up does not look like a slower cadence (ratio clamped to 0.5..3).
 *  3. Cadence = recency-weighted median of the normalised intervals (recent trips weigh more).
 *  4. Next due date = last trip + cadence × (last purchase size ÷ typical size).
 *  5. Confidence grows with the number of intervals and shrinks with their variability.
 *
 * History imported from Microsoft To Do is only partly documented, so it is read through [TodoCoverage]:
 * its check-offs are grouped into shopping trips, intervals that span an undocumented trip are left out
 * (if none is left, the cadence comes from the share of documented trips that included the item), and
 * when trips after the last purchase were not documented, the item is assumed to have kept its rhythm
 * there instead of being reported as long overdue.
 */
class ConsumptionStatsEngine(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val recencyDecay: Double = 0.8,
    /** Only the most recent purchases inform the estimate, so habits can change. */
    private val maxTrips: Int = 12,
) {

    /**
     * [stretch]: per item, how much longer than measured its cadence really is, because the household keeps finding
     * it at home when it is suggested ([atHomeStretch]); 1 when not given.
     */
    fun compute(purchases: List<Purchase>, today: LocalDate, stretch: Map<String, Double> = emptyMap()): Map<String, ItemStats> {
        val coverage = TodoCoverage.of(purchases, today, zone)
        return purchases.groupBy { it.itemId }.mapValues { (itemId, list) ->
            computeItem(itemId, list, today, coverage, stretch[itemId] ?: 1.0)
        }
    }

    private data class Trip(val day: LocalDate, val baseAmount: Double?, val quantity: Quantity?)

    fun computeItem(
        itemId: String,
        purchases: List<Purchase>,
        today: LocalDate,
        coverage: TodoCoverage? = null,
        stretch: Double = 1.0,
    ): ItemStats {
        require(purchases.isNotEmpty())
        val latestIdentity = purchases.maxBy { it.boughtAtMillis }.identity

        val trips = purchases
            .groupBy { coverage?.tripOf(it) ?: Instant.ofEpochMilli(it.boughtAtMillis).atZone(zone).toLocalDate() }
            .map { (day, sameDay) -> Trip(day, sumBase(sameDay), sumQuantity(sameDay)) }
            .sortedBy { it.day }
            .takeLast(maxTrips)

        // Base amounts are only comparable within one dimension (1 יח' vs 750 גרם): use the dimension of the
        // latest purchase and ignore trips in another one, so "750 g" is never read as 750 pieces.
        val dim = trips.lastOrNull { it.baseAmount != null }?.quantity?.dimension
        val knownAmounts = trips.filter { it.quantity?.dimension == dim }.mapNotNull { it.baseAmount }
        // an amount the household really bought (never the average of two trips: 1 and 2 bottles are not 1.5)
        val typicalBase = if (dim == null || knownAmounts.isEmpty()) null else observedMedian(knownAmounts)
        // a median far beyond any home purchase of this item is a slip in the data (750 יח' of cottage cheese)
        val typicalQuantity = QuantityNorms.homeSized(
            latestIdentity.name, latestIdentity.category, typicalBase?.let { Quantity.fromBase(it, dim!!) },
        )

        fun sizeRatio(trip: Trip): Double {
            if (trip.quantity?.dimension != dim) return 1.0
            val amount = trip.baseAmount ?: return 1.0
            val typical = typicalBase ?: return 1.0
            return (amount / typical).coerceIn(0.5, 3.0)
        }

        // an interval that spans an undocumented trip is unknown: the item may have been bought there
        val normalisedIntervals = trips.zipWithNext().mapNotNull { (a, b) ->
            if (coverage?.hasUndocumentedTripBetween(a.day, b.day) == true) null
            else ChronoUnit.DAYS.between(a.day, b.day).toDouble() / sizeRatio(a)
        }
        // fallback for To Do history: the share of documented trips that included the item
        val todoRate = if (normalisedIntervals.isEmpty() && coverage != null) {
            val todoTrips = purchases.mapNotNull { coverage.tripOf(it) }.distinct().sorted()
            val later = if (todoTrips.size >= 2) coverage.tripsAfter(todoTrips.first()) else 0
            if (later > 0) (todoTrips.size - 1).toDouble() / later else null
        } else null

        val measured: Double?
        val spread: Double?
        var intervals = normalisedIntervals.size
        if (todoRate != null && todoRate > 0.0) {
            measured = (coverage!!.tripIntervalDays / todoRate).coerceAtLeast(1.0)
            spread = null
            intervals = (trips.size - 1).coerceAtMost(3) // a coarser estimate than measured intervals
        } else if (normalisedIntervals.isEmpty()) {
            measured = null
            spread = null
        } else {
            val n = normalisedIntervals.size
            val weights = List(n) { i -> recencyDecay.pow((n - 1 - i).toDouble()) }
            measured = weightedMedian(normalisedIntervals, weights).coerceAtLeast(1.0)
            spread = median(normalisedIntervals.map { abs(it - measured) })
        }
        // "יש לי בבית" again and again: the item lasts longer than its purchases say (see [AT_HOME_STRETCH])
        val cadence = measured?.let { it * stretch }

        val last = trips.last()
        var estimatedLast = last.day
        var nextDue = cadence?.let { c ->
            last.day.plusDays((c * sizeRatio(last)).roundToLong().coerceAtLeast(1))
        }
        // trips after the last purchase were not documented: assume the rhythm went on through them
        val documentedFrom = coverage?.documentedFromAfter(last.day)
        if (cadence != null && nextDue != null && documentedFrom != null) {
            val step = cadence.roundToLong().coerceAtLeast(1)
            while (nextDue!!.isBefore(documentedFrom)) {
                estimatedLast = nextDue
                nextDue = nextDue.plusDays(step)
            }
        }

        val baseConfidence = when {
            intervals == 0 -> 0.0
            intervals == 1 -> 0.3
            intervals == 2 -> 0.5
            intervals == 3 -> 0.65
            intervals <= 5 -> 0.8
            else -> 0.9
        }
        val variation = if (measured != null && spread != null) spread / measured else 1.0
        val estimatePenalty = if (estimatedLast != last.day) 0.6 else 1.0
        val confidence = baseConfidence * (1.0 - (variation * 0.5).coerceAtMost(0.5)) * estimatePenalty

        val classification = when {
            trips.size >= 4 && variation < 0.6 -> Classification.ROUTINE
            trips.size >= 2 -> Classification.OCCASIONAL
            else -> Classification.ONE_OFF
        }

        val frequencyScore = trips.sumOf { trip ->
            val ageDays = ChronoUnit.DAYS.between(trip.day, today).coerceAtLeast(0).toDouble()
            0.5.pow(ageDays / 60.0) // half-life of two months
        }

        return ItemStats(
            itemId = itemId,
            identity = latestIdentity,
            purchaseDays = trips.size,
            firstPurchase = trips.first().day,
            lastPurchase = last.day,
            estimatedLastPurchase = estimatedLast,
            cadenceDays = cadence,
            spreadDays = spread,
            typicalQuantity = typicalQuantity,
            lastQuantity = last.quantity,
            nextDue = nextDue,
            confidence = confidence,
            classification = classification,
            frequencyScore = frequencyScore,
            typicalPeople = purchases.sortedBy { it.boughtAtMillis }.takeLast(maxTrips * 2)
                .mapNotNull { it.householdPeople?.takeIf { n -> n > 0.0 } }
                .takeIf { it.isNotEmpty() }?.let(::median),
        )
    }

    private fun sumBase(sameDay: List<Purchase>): Double? {
        val quantities = sameDay.map { it.quantity }
        if (quantities.any { it == null }) return null
        val dims = quantities.map { it!!.dimension }.toSet()
        if (dims.size != 1) return null
        return quantities.sumOf { it!!.baseAmount }
    }

    private fun sumQuantity(sameDay: List<Purchase>): Quantity? {
        var total: Quantity? = null
        for (p in sameDay) {
            val q = p.quantity ?: return null
            total = if (total == null) q else (total + q ?: return null)
        }
        return total
    }

    companion object {
        fun median(values: List<Double>): Double {
            require(values.isNotEmpty())
            val sorted = values.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
        }

        /** Each "יש לי בבית" lately makes the cadence this much longer (of the measured one), up to [AT_HOME_MAX_STRETCH]. */
        const val AT_HOME_STRETCH = 0.25
        const val AT_HOME_MAX_STRETCH = 2.0

        /** How much longer the cadence is after [marks] recent "יש לי בבית" (1 with none). */
        fun atHomeStretch(marks: Int): Double = (1.0 + AT_HOME_STRETCH * marks.coerceAtLeast(0)).coerceAtMost(AT_HOME_MAX_STRETCH)

        /** The median as one of the values themselves: the lower middle one when their number is even. */
        fun observedMedian(values: List<Double>): Double {
            require(values.isNotEmpty())
            return values.sorted()[(values.size - 1) / 2]
        }

        fun weightedMedian(values: List<Double>, weights: List<Double>): Double {
            require(values.size == weights.size && values.isNotEmpty())
            val pairs = values.zip(weights).sortedBy { it.first }
            val half = weights.sum() / 2.0
            var acc = 0.0
            for ((index, pair) in pairs.withIndex()) {
                acc += pair.second
                if (abs(acc - half) < 1e-9 && index + 1 < pairs.size) {
                    return (pair.first + pairs[index + 1].first) / 2.0
                }
                if (acc > half) return pair.first
            }
            return pairs.last().first
        }
    }
}
