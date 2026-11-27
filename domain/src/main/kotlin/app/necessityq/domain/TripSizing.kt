package app.necessityq.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How often the household goes shopping, as it said. Every amount proposed for a trip is for this stretch: what runs
 * out sooner is bought for the whole stretch (four weeks of bread for a monthly trip), and what lasts longer is bought
 * one pack at a time, and only on the trips it is due (washing powder at a quarter a week: one a month).
 */
enum class TripCadence(val days: Int, val label: String, val span: String) {
    WEEKLY(7, "פעם בשבוע", "לשבוע"),
    BIWEEKLY(14, "פעם בשבועיים", "לשבועיים"),
    MONTHLY(30, "פעם בחודש", "לחודש");

    companion object {
        val DEFAULT = WEEKLY

        fun ofDays(days: Int?): TripCadence = entries.firstOrNull { it.days == days } ?: DEFAULT

        /** "לשבוע", "לשבועיים", "ל־3 שבועות", "לחודש", "לחודשיים", "ל־3 חודשים": how long an amount lasts. */
        fun spanText(days: Double): String {
            val weeks = (days / 7.0).roundToInt()
            return when {
                days < 10.5 -> "לשבוע"
                weeks == 2 -> "לשבועיים"
                weeks == 3 -> "ל־3 שבועות"
                days < 45 -> "לחודש"
                days < 75 -> "לחודשיים"
                else -> "ל־${(days / 30.0).roundToInt()} חודשים"
            }
        }
    }
}

/** An amount and, when it is not simply the household's usual purchase, where it came from. */
data class SizedQuantity(val quantity: Quantity?, val note: String? = null)

/**
 * The one place the amounts of proposed lines are worked out (suggestion cards, quick-add chips, the week's list,
 * the list from past purchases), so they all agree:
 *  1. one purchase: what the household usually buys ([ItemStats.typicalQuantity]); with no amount on record, what it
 *     said it buys in the first-list survey ([HouseholdBaseline], for as long as a purchase lasts), else the
 *     per-person table ([SmartQuantities]) for the size of the household ([HouseholdSize]);
 *  2. a household that grew or shrank since it bought the usual amount: the amount follows it (people now ÷ then);
 *  3. a list for a stretch of days (horizonDays): an item that runs out more often than that is bought several times;
 *  4. a computed amount is rounded to what a shop sells (whole pieces, whole packs). An amount the household really
 *     bought is kept as it is.
 */
class TripSizer(
    stats: Collection<ItemStats>,
    val people: Double,
    /** The first-list survey's answers, when it was filled in. */
    private val baseline: HouseholdBaseline? = null,
    /** The packs the household chose ([HouseholdPacks]); the rest are learned from what it usually buys. */
    private val chosen: PackChoices = PackChoices(),
    /** How often the household shops ([TripCadence.days]): an item with no rhythm of its own is bought for this long. */
    val tripDays: Int = TripCadence.DEFAULT.days,
) {
    private val byId = stats.associateBy { it.itemId }

    /** The pack the household buys of [identity], or null when nothing says ([HouseholdPacks.resolve]). */
    fun packFor(identity: ItemIdentity): Pack? = chosen.products[identity.id] ?: HouseholdPacks.resolve(
        identity.name, identity.variant, null,
        PackSizes.keyFor(identity.name, identity.variant)?.let { chosen.sizes[it] },
        byId[identity.id]?.typicalQuantity,
    )
    private val calibration = SmartQuantities.calibration(stats, people)

    fun size(identity: ItemIdentity, stats: ItemStats?, horizonDays: Int? = null): SizedQuantity {
        val notes = mutableListOf<String>()
        var computed = false
        val usual = stats?.typicalQuantity
        val then = stats?.typicalPeople
        var q: Quantity? = usual
        // how many days the computed amount is meant to last, to tell when a whole pack lasts much longer
        var coverDays: Double? = null
        if (usual != null) {
            if (people > 0.0 && then != null && then > 0.0 && abs(people / then - 1.0) >= SCALE_THRESHOLD) {
                q = usual.times(HouseholdSize.scale(people, then))
                computed = true
                notes += "מותאם ל־${peopleText(people)}"
            }
        } else {
            // with no rhythm of its own, an item is bought for as long as the household goes between trips
            val days = stats?.cadenceDays ?: tripDays.toDouble()
            val told = baseline?.amountFor(identity.name)
            val table = if (told != null) null else SmartQuantities.forItem(identity.name, days, people, calibration)
            if (told != null) {
                q = told.forDays(days)
                if (q != null) {
                    computed = true
                    coverDays = days
                    notes += SURVEY_NOTE
                }
            } else if (table != null) {
                q = table
                computed = true
                notes += (if (SmartQuantities.isEstimate(identity.name)) "הערכה לפי " else "לפי ") + peopleText(people)
            }
        }
        val times = timesIn(horizonDays, stats?.cadenceDays)
        val current = q
        if (current != null && times > 1) {
            q = current.times(times.toDouble())
            computed = true
            notes += spanOf(horizonDays!!) + ": " + timesText(times)
        }
        val pack = packFor(identity)
        // a computed amount is whole packs of what the household buys (19 yoghurts in packs of 8 are 16)
        val result = q?.let { if (computed) pack?.nearest(it) ?: purchasable(identity.name, identity.variant, it) else wholeCount(it) }
        // one pack that lasts well beyond the trip: say how long ("מספיק לחודש"), so it is clear why it is one
        val need = q
        val cover = coverDays
        if (result != null && need != null && cover != null && result.dimension == need.dimension && need.baseAmount > 0.0) {
            val lasts = cover * result.baseAmount / need.baseAmount
            if (lasts >= cover * LASTS_LONGER) notes += "מספיק " + TripCadence.spanText(lasts)
        }
        return SizedQuantity(result, notes.joinToString(" · ").ifEmpty { null })
    }

    fun size(s: Suggestion, horizonDays: Int? = null): Suggestion {
        val sized = size(s.identity, byId[s.itemId], horizonDays)
        return s.copy(quantity = sized.quantity ?: s.quantity, quantityNote = sized.note)
    }

    fun size(item: GeneratedItem, horizonDays: Int? = null): GeneratedItem {
        val sized = size(item.identity, byId[item.identity.id], horizonDays)
        return item.copy(quantity = sized.quantity ?: item.quantity, quantityNote = sized.note)
    }

    /**
     * A first list for a household with no history yet: what it said it buys in the first-list survey, for as long
     * as it goes between trips ([tripDays]), and the staples the per-person table knows that the survey
     * did not ask about (all of them without the survey), for the same stretch, for the size of the household. Empty when
     * neither is known.
     */
    fun starter(skip: Set<String>): List<Suggestion> {
        // answered in the survey (0 = "לא קונים": left out), by the survey's order
        val answered = baseline?.amounts.orEmpty()
        val order = FirstListSurvey.items.map { it.name }
        val told = answered.filterValues { it.amount > 0.0 }.keys
            .sortedBy { order.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .map { it to SURVEY_STARTER_REASON }
        // the staples the survey did not ask about (it was filled in before they were added), by the table
        val table = if (people <= 0.0) emptyList()
        else SmartQuantities.items.filter { name -> baseline?.amountFor(name) == null }.map { it to STARTER_REASON }
        return (told + table).mapNotNull { (name, reason) ->
            val def = Catalog.lookup(name)
            if (reason == STARTER_REASON && def == null) return@mapNotNull null
            val identity = ItemIdentity(def?.name ?: name, null, def?.category ?: Category.OTHER)
            if (identity.id in skip) return@mapNotNull null
            val sized = size(identity, null, tripDays)
            // the reason already says the survey; the note keeps only what it adds
            val note = sized.note?.split(" · ")?.filter { it != SURVEY_NOTE }?.joinToString(" · ")?.ifEmpty { null }
            Suggestion(
                itemId = identity.id, identity = identity, kind = SuggestionKind.RUNNING_LOW,
                quantity = sized.quantity, score = 1.0, reason = reason, quantityNote = note,
            )
        }.distinctBy { it.itemId }
    }

    companion object {
        /** A household this much bigger or smaller than when it bought the usual amount gets a new amount. */
        const val SCALE_THRESHOLD = 0.15
        const val STARTER_REASON = "רשימה ראשונה לפי גודל הבית"
        const val SURVEY_STARTER_REASON = "רשימה ראשונה לפי השאלון"
        const val SURVEY_NOTE = "לפי השאלון"
        /** A pack that lasts this many times the stretch it was bought for is said to ("מספיק לחודש"). */
        const val LASTS_LONGER = 1.5

        /** "לשבוע", "לשבועיים", "לחודש", or "ל־10 ימים" for a stretch that is none of them. */
        fun spanOf(days: Int): String = TripCadence.entries.firstOrNull { it.days == days }?.span ?: "ל־$days ימים"

        /** True for a line of a first list ([starter]). */
        fun isStarter(reason: String?): Boolean = reason == STARTER_REASON || reason == SURVEY_STARTER_REASON

        /** How many purchases of an item a list for [horizonDays] needs: one, or more when it runs out sooner. */
        fun timesIn(horizonDays: Int?, cadenceDays: Double?): Int {
            if (horizonDays == null || cadenceDays == null || cadenceDays <= 0.0) return 1
            // at most four purchases in a week or two; a monthly trip may hold bread for every few days
            return (horizonDays / cadenceDays).roundToInt().coerceIn(1, max(4, horizonDays / 3))
        }

        /**
         * What a shop sells closest to a computed amount: whole packs ([PackSizes.nearest]), whole pieces, or loose
         * goods to a sensible step (100 g, 250 g, 500 g; 250 ml). Never less than one step.
         */
        fun purchasable(name: String, variant: String?, q: Quantity): Quantity {
            PackSizes.nearest(name, q, variant)?.let { return it }
            return when (q.dimension) {
                Dimension.COUNT -> wholeCount(q)
                Dimension.WEIGHT -> {
                    val step = when {
                        q.baseAmount < 1000.0 -> 100.0
                        q.baseAmount < 3000.0 -> 250.0
                        else -> 500.0
                    }
                    Quantity.fromBase(max(step, Math.round(q.baseAmount / step) * step), Dimension.WEIGHT)
                }
                Dimension.VOLUME -> Quantity.fromBase(max(250.0, Math.round(q.baseAmount / 250.0) * 250.0), Dimension.VOLUME)
            }
        }

        /** Pieces and packages are whole: 1.5 bottles is one or two, never half of one. */
        fun wholeCount(q: Quantity): Quantity =
            if (q.dimension != Dimension.COUNT || q.amount == Math.round(q.amount).toDouble() && q.amount >= 1.0) q
            else Quantity(max(1.0, Math.round(q.amount).toDouble()), q.unit)

        fun peopleText(people: Double): String {
            val n = people.roundToInt().coerceAtLeast(1)
            return if (n == 1) "נפש אחת" else "$n נפשות"
        }

        private fun timesText(times: Int): String = if (times == 2) "פעמיים" else "$times פעמים"
    }
}

private fun Quantity.times(factor: Double): Quantity = Quantity(amount * factor, unit)
