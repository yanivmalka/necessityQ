package app.necessityq.domain

import java.time.LocalDate
import kotlin.math.ceil

/** How many "adult portions" the household is, from its family profiles (children count for less). */
object HouseholdSize {
    /** Zero when no profiles were filled in: then nothing is scaled. */
    fun people(members: List<FamilyMember>, today: LocalDate): Double = members.sumOf { weight(it, today) }

    /** A member with no known age counts as an adult. */
    fun weight(member: FamilyMember, today: LocalDate): Double {
        val months = member.ageMonths(today) ?: return 1.0
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
    private class PerPerson(val perWeek: Double, val unit: QuantityUnit, val step: Double)

    /** Keyed by the catalogue's canonical name ([Catalog.lookup]). */
    private val perPerson: Map<String, PerPerson> = mapOf(
        "חלב" to PerPerson(1.5, QuantityUnit.LITER, 1.0),
        "לחם" to PerPerson(1.5, QuantityUnit.PIECE, 1.0),
        "ביצים" to PerPerson(4.0, QuantityUnit.PIECE, 2.0),
        "יוגורט" to PerPerson(3.0, QuantityUnit.PIECE, 1.0),
        "קוטג'" to PerPerson(1.0, QuantityUnit.PIECE, 1.0),
        "עגבניות" to PerPerson(0.7, QuantityUnit.KILOGRAM, 0.5),
        "מלפפונים" to PerPerson(0.7, QuantityUnit.KILOGRAM, 0.5),
        "בננות" to PerPerson(0.6, QuantityUnit.KILOGRAM, 0.5),
        "תפוחים" to PerPerson(0.6, QuantityUnit.KILOGRAM, 0.5),
    )

    /**
     * [cadenceDays]: how often the item is bought, when known (a purchase has to last that long); [people] from
     * [HouseholdSize.people]. Rounded up to a sensible step, at least one step.
     */
    fun forItem(name: String, cadenceDays: Double?, people: Double): Quantity? {
        if (people <= 0.0) return null
        val norm = perPerson[Catalog.lookup(name)?.name ?: return null] ?: return null
        val weeks = ((cadenceDays ?: 7.0) / 7.0).coerceIn(0.5, 4.0)
        val raw = norm.perWeek * people * weeks
        val amount = (ceil(raw / norm.step) * norm.step).coerceAtLeast(norm.step)
        return Quantity(amount, norm.unit)
    }

    /** [suggestion] with an amount from the household's size when it has none of its own. */
    fun fill(suggestion: Suggestion, stats: ItemStats?, people: Double): Suggestion {
        if (suggestion.quantity != null) return suggestion
        val q = forItem(suggestion.identity.name, stats?.cadenceDays, people) ?: return suggestion
        return suggestion.copy(quantity = q)
    }
}
