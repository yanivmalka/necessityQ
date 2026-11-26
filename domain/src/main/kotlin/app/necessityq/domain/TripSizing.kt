package app.necessityq.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** An amount and, when it is not simply the household's usual purchase, where it came from. */
data class SizedQuantity(val quantity: Quantity?, val note: String? = null)

/**
 * The one place the amounts of proposed lines are worked out (suggestion cards, quick-add chips, the week's list,
 * the list from past purchases), so they all agree:
 *  1. one purchase: what the household usually buys ([ItemStats.typicalQuantity]); with no amount on record, the
 *     per-person table ([SmartQuantities]) for the size of the household ([HouseholdSize]);
 *  2. a household that grew or shrank since it bought the usual amount: the amount follows it (people now ÷ then);
 *  3. a list for a stretch of days (horizonDays): an item that runs out more often than that is bought several times;
 *  4. a computed amount is rounded to what a shop sells (whole pieces, whole packs). An amount the household really
 *     bought is kept as it is.
 */
class TripSizer(stats: Collection<ItemStats>, val people: Double) {
    private val byId = stats.associateBy { it.itemId }
    private val calibration = SmartQuantities.calibration(stats, people)

    fun size(identity: ItemIdentity, stats: ItemStats?, horizonDays: Int? = null): SizedQuantity {
        val notes = mutableListOf<String>()
        var computed = false
        val usual = stats?.typicalQuantity
        val then = stats?.typicalPeople
        var q: Quantity? = usual
        if (usual != null) {
            if (people > 0.0 && then != null && then > 0.0 && abs(people / then - 1.0) >= SCALE_THRESHOLD) {
                q = usual.times(people / then)
                computed = true
                notes += "מותאם ל־${peopleText(people)}"
            }
        } else {
            val table = SmartQuantities.forItem(identity.name, stats?.cadenceDays, people, calibration)
            if (table != null) {
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
            notes += (if (horizonDays == 7) "לשבוע" else "ל־$horizonDays ימים") + ": " + timesText(times)
        }
        val result = q?.let { if (computed) purchasable(identity.name, identity.variant, it) else wholeCount(it) }
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
     * A first list for a household with no history yet: the staples the per-person table knows, for a week, for
     * the size of the household. Empty when the household's size is unknown (no family profiles).
     */
    fun starter(skip: Set<String>): List<Suggestion> {
        if (people <= 0.0) return emptyList()
        return SmartQuantities.items.mapNotNull { name ->
            val def = Catalog.lookup(name) ?: return@mapNotNull null
            val identity = ItemIdentity(def.name, null, def.category)
            if (identity.id in skip) return@mapNotNull null
            val sized = size(identity, null, 7)
            Suggestion(
                itemId = identity.id, identity = identity, kind = SuggestionKind.RUNNING_LOW,
                quantity = sized.quantity, score = 1.0, reason = STARTER_REASON, quantityNote = sized.note,
            )
        }
    }

    companion object {
        /** A household this much bigger or smaller than when it bought the usual amount gets a new amount. */
        const val SCALE_THRESHOLD = 0.15
        const val STARTER_REASON = "רשימה ראשונה לפי גודל הבית"

        /** How many purchases of an item a list for [horizonDays] needs: one, or more when it runs out sooner. */
        fun timesIn(horizonDays: Int?, cadenceDays: Double?): Int {
            if (horizonDays == null || cadenceDays == null || cadenceDays <= 0.0) return 1
            return (horizonDays / cadenceDays).roundToInt().coerceIn(1, 4)
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

/**
 * A limit on the shopping as a whole, not only on each item: four sensible amounts of dairy can still add up to
 * 72 yoghurts for eight people. Per category, the pieces (tubs, packs) a person is likely to use in a week are capped;
 * when the computed amounts of the category together go over the cap for the household and the days the list
 * covers, they are all scaled down by the same share (whole pieces, at least one each). Amounts the household really
 * bought are left alone, and only computed ones count towards what is cut.
 */
object BasketLimits {
    /** Pieces a person uses in a week, per category: dairy tubs and packs (yoghurt, cottage, cheeses, puddings). */
    private val piecesPerPersonWeek: Map<Category, Double> = mapOf(Category.DAIRY to 6.0)

    /** The cap for [category] for [people] over [horizonDays] (null = a week), or null when there is none. */
    fun capFor(category: Category, people: Double, horizonDays: Int?): Double? {
        if (people <= 0.0) return null
        val perWeek = piecesPerPersonWeek[category] ?: return null
        val weeks = ((horizonDays ?: 7) / 7.0).coerceAtLeast(1.0)
        return Math.ceil(perWeek * people * weeks)
    }

    fun <T> cap(
        items: List<T>,
        people: Double,
        horizonDays: Int?,
        category: (T) -> Category,
        quantity: (T) -> Quantity?,
        computed: (T) -> Boolean,
        replace: (T, Quantity, String) -> T,
    ): List<T> {
        val scale = HashMap<Category, Double>()
        for ((cat, group) in items.groupBy(category)) {
            val cap = capFor(cat, people, horizonDays) ?: continue
            val pieces = group.mapNotNull { quantity(it)?.takeIf { q -> q.dimension == Dimension.COUNT }?.amount }.sum()
            val fixed = group.sumOf { if (!computed(it)) quantity(it)?.takeIf { q -> q.dimension == Dimension.COUNT }?.amount ?: 0.0 else 0.0 }
            val flexible = pieces - fixed
            if (pieces > cap + 1e-9 && flexible > 0.0) scale[cat] = ((cap - fixed) / flexible).coerceIn(0.0, 1.0)
        }
        if (scale.isEmpty()) return items
        return items.map { item ->
            val factor = scale[category(item)] ?: return@map item
            val q = quantity(item)
            if (q == null || q.dimension != Dimension.COUNT || !computed(item)) return@map item
            val reduced = Quantity(max(1.0, Math.floor(q.amount * factor)), q.unit)
            if (reduced.amount >= q.amount) item
            else replace(item, reduced, "הוקטן: מגבלת מוצרי חלב ל־${TripSizer.peopleText(people)}")
        }
    }

    fun capSuggestions(items: List<Suggestion>, people: Double, horizonDays: Int?): List<Suggestion> = cap(
        items, people, horizonDays, { it.identity.category }, { it.quantity }, { it.quantityNote != null },
        { s, q, note -> s.copy(quantity = q, quantityNote = listOfNotNull(s.quantityNote, note).joinToString(" · ")) },
    )

    fun capGenerated(items: List<GeneratedItem>, people: Double, horizonDays: Int?): List<GeneratedItem> = cap(
        items, people, horizonDays, { it.identity.category }, { it.quantity }, { it.quantityNote != null },
        { g, q, note -> g.copy(quantity = q, quantityNote = listOfNotNull(g.quantityNote, note).joinToString(" · ")) },
    )
}
