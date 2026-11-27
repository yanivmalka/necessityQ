package app.necessityq.domain

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** How a survey item is counted: fresh food by the week, pantry and home goods by the month. */
enum class SurveyPeriod(val days: Int, val label: String) {
    WEEK(7, "בשבוע"),
    MONTH(30, "בחודש"),
}

/**
 * One basic product the first-list survey asks about: what nearly every home buys. [perAdult] and [perHome] are a
 * rough local estimate per [period] (an adult portion, see [HouseholdSize], plus a fixed part for the home), used
 * when the model's estimate is not available and to keep the model's answer within reason. [perBaby]: only for
 * homes with a small child (nappies), per child.
 */
data class SurveyItem(
    val name: String,
    val unit: QuantityUnit,
    val step: Double,
    val period: SurveyPeriod,
    val perAdult: Double = 0.0,
    val perHome: Double = 0.0,
    val perBaby: Double = 0.0,
) {
    val category: Category get() = Catalog.lookup(name)?.category ?: Category.OTHER
    val identity: ItemIdentity get() = ItemIdentity(name, null, category)
    val forBabiesOnly: Boolean get() = perBaby > 0.0 && perAdult == 0.0 && perHome == 0.0
}

/**
 * Who lives in the home, as far as amounts go: ages in whole years, no names. From the family profiles (an adult
 * whose age is unknown counts as [ADULT_AGE]), or, with none, from the survey's own question ("כמה אתם בבית?").
 */
data class HouseholdMakeup(val ages: List<Int>) {
    /** Adult portions, as [HouseholdSize.people] counts them. */
    val people: Double get() = ages.sumOf { HouseholdSize.weightForMonths(it * 12) }
    /** Children young enough for nappies. */
    val babies: Int get() = ages.count { it < BABY_YEARS }
    val isEmpty: Boolean get() = ages.isEmpty()

    /** "2 מבוגרים, ילדים בגילים 9 ו4" style: what the estimate was made for. */
    fun describe(): String {
        val adults = ages.count { it >= ADULT_FROM }
        val young = ages.filter { it < ADULT_FROM }.sortedDescending()
        val parts = mutableListOf<String>()
        if (adults == 1) parts += "מבוגר אחד" else if (adults > 1) parts += "$adults מבוגרים"
        if (young.size == 1) parts += "ילד אחד בגיל ${young[0]}"
        else if (young.isNotEmpty()) parts += "${young.size} ילדים בגילים " + HebrewText.nameList(young.map { it.toString() }, max = MAX_PEOPLE)
        return parts.joinToString(", ")
    }

    companion object {
        const val ADULT_AGE = 30
        const val ADULT_FROM = 18
        const val BABY_YEARS = 3
        const val MAX_PEOPLE = 15

        fun of(members: List<FamilyMember>, today: LocalDate): HouseholdMakeup =
            HouseholdMakeup(members.take(MAX_PEOPLE).map { m -> m.ageMonths(today)?.div(12) ?: ADULT_AGE })

        /** The survey's own question, by age group: one representative age for each. */
        fun ofGroups(counts: Map<AgeGroup, Int>): HouseholdMakeup =
            HouseholdMakeup(AgeGroup.entries.flatMap { g -> List((counts[g] ?: 0).coerceIn(0, MAX_PEOPLE)) { g.age } }.take(MAX_PEOPLE))
    }
}

/** The groups the survey asks about when the family has no profiles; [age] stands for the group. */
enum class AgeGroup(val label: String, val age: Int) {
    ADULTS("מבוגרים ובני נוער (13+)", HouseholdMakeup.ADULT_AGE),
    KIDS("ילדים (6–12)", 9),
    SMALL("ילדים (2–5)", 4),
    BABIES("תינוקות (עד שנתיים)", 1),
}

/** What the household said it buys of one survey item: [amount] of [unit] every [periodDays]; 0 = not bought. */
data class BaselineAmount(val amount: Double, val unit: QuantityUnit, val periodDays: Int) {
    val quantity: Quantity? get() = if (amount > 0.0) Quantity(amount, unit) else null

    /** How much lasts [days] at this rate; null when the household does not buy it. */
    fun forDays(days: Double): Quantity? {
        if (amount <= 0.0 || periodDays <= 0) return null
        return Quantity(amount * days.coerceAtLeast(1.0) / periodDays, unit)
    }
}

/**
 * The answers of the first-list survey, kept with the household's family data ([MealData.baseline]). The survey is
 * filled once: from then on the amounts of items the household has not bought yet come from it ([TripSizer]), and
 * once it buys an item, what it really bought replaces the answer.
 */
data class HouseholdBaseline(
    val filledAtMillis: Long,
    /** Keyed by the catalogue name ([SurveyItem.name]). */
    val amounts: Map<String, BaselineAmount> = emptyMap(),
    /** Who lives in the home, when it was told in the survey (no family profiles); null = from the profiles. */
    val makeup: HouseholdMakeup? = null,
    /** The household chose not to fill it in; it is not asked again (it can still be opened). */
    val skipped: Boolean = false,
) {
    /** The answer for [name] (any spelling or variant of a survey item), or null when it was not asked. */
    fun amountFor(name: String): BaselineAmount? = amounts[Catalog.lookup(name)?.name ?: name]
}

/** A survey item's proposed amount and where it came from. */
data class SurveyEstimate(val amount: Double, val source: Source) {
    enum class Source { HISTORY, MODEL, TABLE }
}

/**
 * The first-list survey: before the first list is built, the household is asked once how much it buys of the
 * basic products ([items]). The amounts are proposed first: from its own purchases when it has any, else by the
 * model from the ages of the people in the home ([BaselineEstimator]), else from the local table. The household
 * corrects what is wrong, and [check] points at an answer far from what is usual for a home of its size.
 */
object FirstListSurvey {
    private val G = QuantityUnit.GRAM
    private val KG = QuantityUnit.KILOGRAM
    private val L = QuantityUnit.LITER
    private val PC = QuantityUnit.PIECE
    private val PK = QuantityUnit.PACKAGE
    private val W = SurveyPeriod.WEEK
    private val M = SurveyPeriod.MONTH

    /**
     * In store-walk order. Rough estimates (no published per-product figure except milk, eggs, cottage and apples,
     * see [SmartQuantities]): only a first proposal, which the model and the household correct.
     */
    val items: List<SurveyItem> = listOf(
        SurveyItem("עגבניות", KG, 0.5, W, perAdult = 0.7),
        SurveyItem("מלפפונים", KG, 0.5, W, perAdult = 0.7),
        SurveyItem("פלפל", KG, 0.5, W, perAdult = 0.3),
        SurveyItem("בצל", KG, 0.5, W, perAdult = 0.3),
        SurveyItem("תפוחי אדמה", KG, 0.5, W, perAdult = 0.6),
        SurveyItem("גזר", KG, 0.5, W, perAdult = 0.3),
        SurveyItem("תפוחים", KG, 0.5, W, perAdult = 0.27),
        SurveyItem("בננות", KG, 0.5, W, perAdult = 0.6),
        SurveyItem("חלב", L, 1.0, W, perAdult = 0.9),
        SurveyItem("ביצים", PC, 6.0, W, perAdult = 4.8),
        SurveyItem("קוטג'", PC, 1.0, W, perAdult = 0.8),
        SurveyItem("גבינה לבנה", PC, 1.0, W, perAdult = 0.5),
        SurveyItem("גבינה צהובה", G, 100.0, W, perAdult = 100.0),
        SurveyItem("יוגורט", PC, 1.0, W, perAdult = 3.0),
        SurveyItem("לחם", PC, 1.0, W, perAdult = 1.5),
        SurveyItem("פיתות", PK, 1.0, W, perAdult = 0.4),
        SurveyItem("חזה עוף", KG, 0.5, W, perAdult = 0.4),
        SurveyItem("בשר טחון", KG, 0.5, W, perAdult = 0.2),
        SurveyItem("מים מינרליים", PK, 1.0, W, perAdult = 0.5),
        SurveyItem("אורז", KG, 1.0, M, perAdult = 0.8),
        SurveyItem("פסטה", PK, 1.0, M, perAdult = 2.0),
        SurveyItem("שמן", L, 1.0, M, perAdult = 0.5, perHome = 0.5),
        SurveyItem("סוכר", KG, 1.0, M, perAdult = 0.4, perHome = 0.5),
        SurveyItem("קמח", KG, 1.0, M, perAdult = 0.4),
        SurveyItem("טונה", PC, 1.0, M, perAdult = 3.0),
        SurveyItem("קפה", PK, 1.0, M, perAdult = 0.7),
        SurveyItem("קורנפלקס", PK, 1.0, M, perAdult = 1.0),
        SurveyItem("נייר טואלט", PK, 1.0, M, perAdult = 0.25, perHome = 0.5),
        SurveyItem("סבון כלים", PC, 1.0, M, perHome = 1.0),
        SurveyItem("אבקת כביסה", PC, 1.0, M, perAdult = 0.25, perHome = 0.5),
        SurveyItem("שקיות אשפה", PK, 1.0, M, perAdult = 0.25, perHome = 1.0),
        SurveyItem("משחת שיניים", PC, 1.0, M, perAdult = 0.5),
        SurveyItem("שמפו", PC, 1.0, M, perAdult = 0.3, perHome = 0.3),
        SurveyItem("חיתולים", PK, 1.0, M, perBaby = 4.0),
        SurveyItem("מגבונים", PK, 1.0, M, perBaby = 4.0),
    )

    fun item(name: String): SurveyItem? = items.firstOrNull { it.name == (Catalog.lookup(name)?.name ?: name) }

    /** The items asked of this home: nappies only where there is a small child. */
    fun itemsFor(makeup: HouseholdMakeup): List<SurveyItem> = items.filter { !it.forBabiesOnly || makeup.babies > 0 }

    /** The local table's amount for [item] per its period, to its step (at least one step when the home buys it). */
    fun tableEstimate(item: SurveyItem, makeup: HouseholdMakeup): Double {
        if (makeup.isEmpty) return 0.0
        val raw = item.perHome + item.perAdult * HouseholdSize.shared(makeup.people) + item.perBaby * makeup.babies
        if (raw <= 0.0) return 0.0
        return max(item.step, ceil(raw / item.step - 1e-9) * item.step)
    }

    /**
     * The model's amount for [item], kept only when it is a sane amount: not negative, and within a quarter to four
     * times the local table (a model that says a family of four drinks no milk, or forty litres a week, is wrong).
     * Rounded to the item's step. Null = use the table.
     */
    fun acceptModel(item: SurveyItem, model: Double?, table: Double): Double? {
        if (model == null || !model.isFinite() || model < 0.0) return null
        if (table > 0.0 && (model < table / LIMIT_FACTOR || model > table * LIMIT_FACTOR)) return null
        if (table == 0.0 && model > item.step * LIMIT_FACTOR) return null
        return toStep(item, model)
    }

    /**
     * What the household already buys of [item], per the item's period, when its purchases say so (an amount it
     * really bought and how often); converted to the survey's unit, or null when the units can't be compared.
     */
    fun fromHistory(item: SurveyItem, stats: ItemStats?): Double? {
        val typical = stats?.typicalQuantity ?: return null
        val cadence = stats.cadenceDays?.takeIf { it > 0.0 } ?: return null
        if (typical.dimension != item.unit.dimension) return null
        val perPeriod = typical.baseAmount / item.unit.toBase * item.period.days / cadence
        return toStep(item, perPeriod).takeIf { it > 0.0 }
    }

    /**
     * The proposed amount of every item asked of this home: the household's own history first, then the model's
     * estimate ([model], keyed by item name; null when it could not be asked), then the local table.
     */
    fun estimates(
        makeup: HouseholdMakeup,
        model: Map<String, Double>?,
        stats: Collection<ItemStats> = emptyList(),
    ): Map<String, SurveyEstimate> {
        val byName = stats.associateBy { Catalog.lookup(it.identity.name)?.name ?: it.identity.name }
        return itemsFor(makeup).associate { item ->
            val table = tableEstimate(item, makeup)
            val estimate = fromHistory(item, byName[item.name])?.let { SurveyEstimate(it, SurveyEstimate.Source.HISTORY) }
                ?: acceptModel(item, model?.get(item.name), table)?.let { SurveyEstimate(it, SurveyEstimate.Source.MODEL) }
                ?: SurveyEstimate(table, SurveyEstimate.Source.TABLE)
            item.name to estimate
        }
    }

    /**
     * A word when an answer is far from what is usual for a home of this size (three times as much, or a third),
     * so a slip ("20 ליטר חלב") is caught before it becomes the usual amount. Null when the answer looks fine.
     */
    fun check(item: SurveyItem, answer: Double, usual: Double): String? {
        if (answer <= 0.0 || usual <= 0.0) return null
        return when {
            answer >= usual * CHECK_FACTOR && answer - usual > item.step -> "הרבה יותר מהמקובל לבית בגודל שלכם (בערך ${format(item, usual)})"
            answer <= usual / CHECK_FACTOR && usual - answer > item.step -> "פחות מהמקובל לבית בגודל שלכם (בערך ${format(item, usual)})"
            else -> null
        }
    }

    /** One step up or down; never below zero ("לא קונים"). */
    fun step(item: SurveyItem, amount: Double, delta: Int): Double = max(0.0, toStep(item, amount + delta * item.step))

    fun format(item: SurveyItem, amount: Double): String =
        if (amount <= 0.0) "לא קונים" else Quantity(amount, item.unit).format()

    /** The answers as they are kept. [makeup] only when the home was described in the survey (no profiles). */
    fun baseline(answers: Map<String, Double>, makeup: HouseholdMakeup?, nowMillis: Long): HouseholdBaseline =
        HouseholdBaseline(
            filledAtMillis = nowMillis,
            amounts = answers.mapNotNull { (name, amount) ->
                val item = item(name) ?: return@mapNotNull null
                item.name to BaselineAmount(max(0.0, amount), item.unit, item.period.days)
            }.toMap(),
            makeup = makeup,
        )

    /** The household's size: its family profiles, or, with none, who it said lives at home in the survey. */
    fun people(data: MealData, today: LocalDate): Double =
        if (data.members.isNotEmpty()) HouseholdSize.people(data.members, today) else data.baseline?.makeup?.people ?: 0.0

    /** Who lives at home: the family profiles, or the survey's answer. Empty when unknown. */
    fun makeup(data: MealData, today: LocalDate): HouseholdMakeup =
        if (data.members.isNotEmpty()) HouseholdMakeup.of(data.members, today) else data.baseline?.makeup ?: HouseholdMakeup(emptyList())

    /** The survey is asked before building a list until it was filled in or skipped once. */
    fun shouldAsk(data: MealData): Boolean = data.baseline == null

    private fun toStep(item: SurveyItem, amount: Double): Double = (amount / item.step).roundToInt() * item.step

    private const val LIMIT_FACTOR = 4.0
    private const val CHECK_FACTOR = 3.0
}

/** Port: the model's estimate of what a home of these ages buys of each item, per item's period. Null = could not ask. */
interface BaselineEstimator {
    suspend fun estimate(householdId: String, ages: List<Int>, items: List<SurveyItem>): Map<String, Double>?
}
