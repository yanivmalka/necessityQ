package app.necessityq.domain

import java.time.LocalDate
import kotlin.math.ceil

/** How many "adult portions" the household is, from its family profiles (children count for less). */
object HouseholdSize {
    /** Zero when no profiles were filled in: then nothing is scaled. */
    fun people(members: List<FamilyMember>, today: LocalDate): Double = members.sumOf { weight(it, today) }

    /**
     * How much a household of [people] portions buys, in "single-person" shopping units. Sub-linear: a big household
     * shares (one tub of cottage serves several people, nobody has three of their own), a single person buys for one.
     * 1 → 1, 2 → 1.8, 4 → 3.2, 8 → 5.8. Only for amounts computed from a table; what the household really buys
     * is never reduced by it.
     */
    fun shared(people: Double): Double = if (people <= 1.0) people else Math.pow(people, SHARING)

    /** Ratio of what a household of [now] buys to what one of [then] bought. */
    fun scale(now: Double, then: Double): Double = shared(now) / shared(then)

    private const val SHARING = 0.85

    /** A member with no known age counts as an adult. */
    fun weight(member: FamilyMember, today: LocalDate): Double = weightForMonths(member.ageMonths(today))

    /** The weight of a person [months] old; unknown age counts as an adult. */
    fun weightForMonths(months: Int?): Double {
        months ?: return 1.0
        return when {
            months < 24 -> 0.25
            months < 72 -> 0.5
            months < 156 -> 0.75
            else -> 1.0
        }
    }
}

/**
 * A first amount for a suggestion the household has no history for ("חלב" with no purchase quantity on record),
 * from what a person typically uses in a week and the size of the household. Items with a purchase history keep
 * their own usual amount: it already says what this household buys. Unknown items get no amount.
 */
object SmartQuantities {
    /** [estimated]: no published figure; a rough guess that the household's own purchases will correct. */
    private class PerPerson(val perWeek: Double, val unit: QuantityUnit, val step: Double, val estimated: Boolean = false)

    /**
     * Keyed by the catalogue's canonical name ([Catalog.lookup]). Only items with a published per-capita figure are
     * listed; the rest get no amount rather than a guess. The figures are annual supply per person (which includes
     * waste), divided by 52, as reported by the Dairy Board / Ministry of Agriculture and quoted in the press:
     *  - חלב: about 46 L of drinking milk and milk drinks a year in 2025, 48 L in 2022 → 0.9 L a week
     *    (https://www.ice.co.il/consumerism/news/article/1113292)
     *  - ביצים: about 250 a year (236 in 2003, 254 in a later press report) → 4.8 a week
     *    (https://www.kikar.co.il/335/81534)
     *  - קוטג': white cheeses about 11 kg a year in 2025 → 0.21 kg a week, about 0.8 of a 250 g tub (an assumption
     *    on the tub size; the figure covers all white cheeses, not cottage alone)
     *  - תפוחים: about 13.6–14.1 kg a year → 0.27 kg a week
     * Rough estimates ([PerPerson.estimated]) for items with no per-product figure (the Ministry of Agriculture
     * gives only about 211 kg of all fresh fruit and vegetables per person for 2024): bread, yoghurt, tomatoes,
     * cucumbers, bananas. They are only a first amount: once the household buys the item, its own purchases
     * replace the estimate, and [calibration] carries what it learned from other items over to the rest.
     */
    private val perPerson: Map<String, PerPerson> = mapOf(
        "חלב" to PerPerson(0.9, QuantityUnit.LITER, 1.0),
        "ביצים" to PerPerson(4.8, QuantityUnit.PIECE, 2.0),
        "קוטג'" to PerPerson(0.8, QuantityUnit.PIECE, 1.0),
        "תפוחים" to PerPerson(0.27, QuantityUnit.KILOGRAM, 0.5),
        "לחם" to PerPerson(1.5, QuantityUnit.PIECE, 1.0, estimated = true),
        "יוגורט" to PerPerson(3.0, QuantityUnit.PIECE, 1.0, estimated = true),
        "עגבניות" to PerPerson(0.7, QuantityUnit.KILOGRAM, 0.5, estimated = true),
        "מלפפונים" to PerPerson(0.7, QuantityUnit.KILOGRAM, 0.5, estimated = true),
        "בננות" to PerPerson(0.6, QuantityUnit.KILOGRAM, 0.5, estimated = true),
    )

    /**
     * The items there is an amount for, in this order (a first list for a new household, see [TripSizer.starter]):
     * the table above, then every other basic product of the first-list survey ([FirstListSurvey.items], its rough
     * estimate per adult and per home), except those only a home with a small child buys.
     */
    val items: List<String>
        get() = perPerson.keys.toList() + FirstListSurvey.items.filter { !it.forBabiesOnly && it.name !in perPerson }.map { it.name }

    /** True when the amount for [name] is a rough estimate and not from a published figure. */
    fun isEstimate(name: String): Boolean {
        val canonical = Catalog.lookup(name)?.name ?: return false
        perPerson[canonical]?.let { return it.estimated }
        return surveyItem(canonical) != null
    }

    /** A basic product of the survey the table above does not have. */
    private fun surveyItem(canonical: String): SurveyItem? =
        FirstListSurvey.items.firstOrNull { it.name == canonical && !it.forBabiesOnly }?.takeIf { canonical !in perPerson }

    /**
     * [cadenceDays]: how often the item is bought, when known (a purchase has to last that long); [people] from
     * [HouseholdSize.people]. Rounded up to a sensible step, at least one step.
     */
    fun forItem(name: String, cadenceDays: Double?, people: Double, calibration: Double = 1.0): Quantity? {
        if (people <= 0.0) return null
        val canonical = Catalog.lookup(name)?.name ?: return null
        val norm = perPerson[canonical] ?: return surveyItem(canonical)?.let { fromSurvey(it, cadenceDays, people, calibration) }
        val weeks = ((cadenceDays ?: 7.0) / 7.0).coerceIn(0.5, 4.0)
        val raw = norm.perWeek * HouseholdSize.shared(people) * weeks * calibration
        val amount = (ceil(raw / norm.step) * norm.step).coerceAtLeast(norm.step)
        return Quantity(amount, norm.unit)
    }

    /**
     * The survey's rough estimate ([SurveyItem.perAdult] for each adult portion, shared as in [HouseholdSize.shared],
     * plus [SurveyItem.perHome]) for as long as a purchase lasts: the item's rhythm when known, else its period (a
     * week for fresh food, a month for the pantry). Rounded up to its step.
     */
    private fun fromSurvey(item: SurveyItem, cadenceDays: Double?, people: Double, calibration: Double): Quantity? {
        val perPeriod = item.perHome + item.perAdult * HouseholdSize.shared(people)
        if (perPeriod <= 0.0) return null
        val days = (cadenceDays ?: item.period.days.toDouble()).coerceIn(3.0, 60.0)
        val raw = perPeriod * days / item.period.days * calibration
        val amount = (ceil(raw / item.step - 1e-9) * item.step).coerceAtLeast(item.step)
        return Quantity(amount, item.unit)
    }

    /**
     * What the household has taught the estimates: how much more (or less) it really buys than the table says,
     * the median over the table's items it has a purchase history for. A family that always buys twice the
     * milk the table proposes probably buys more of the other items too. 1.0 until two items can be compared;
     * kept within 0.5..2 so a few odd purchases cannot throw the amounts far off.
     */
    fun calibration(stats: Collection<ItemStats>, people: Double): Double {
        if (people <= 0.0) return 1.0
        val ratios = stats.mapNotNull { s ->
            val typical = s.typicalQuantity ?: return@mapNotNull null
            val proposed = forItem(s.identity.name, s.cadenceDays, people) ?: return@mapNotNull null
            if (proposed.dimension != typical.dimension || proposed.baseAmount <= 0.0) return@mapNotNull null
            typical.baseAmount / proposed.baseAmount
        }
        if (ratios.size < 2) return 1.0
        return ConsumptionStatsEngine.median(ratios).coerceIn(0.5, 2.0)
    }

    /** [suggestion] with an amount from the household's size when it has none of its own. */
    fun fill(suggestion: Suggestion, stats: ItemStats?, people: Double, calibration: Double = 1.0): Suggestion {
        if (suggestion.quantity != null) return suggestion
        val q = forItem(suggestion.identity.name, stats?.cadenceDays, people, calibration) ?: return suggestion
        return suggestion.copy(quantity = q)
    }
}
