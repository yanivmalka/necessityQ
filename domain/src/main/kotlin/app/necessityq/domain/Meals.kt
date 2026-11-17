package app.necessityq.domain

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * Meal planning:
 *  - family profiles (avatar, age from birth month/year, gender, salty/sweet preference, strong dislikes,
 *    a star rating per dish, optionally linked to the member's own app account so they rate for themselves),
 *  - recipes with a yield in servings; the household chooses how many servings it makes and the
 *    quantities on the shopping list follow,
 *  - a weekly / bi-weekly cycle shown on real dates one or two weeks ahead, with one-off changes per date
 *    and "cooked" tracking,
 *  - menu → shopping list, and bought items → dish ideas.
 */

enum class Gender(val label: String) { MALE("בן"), FEMALE("בת") }

/** What a member tends to prefer. */
enum class FlavorPref(val label: String) { SALTY("מלוח"), SWEET("מתוק"), BOTH("גם וגם") }

/** The character of a dish. */
enum class Flavor(val label: String) { SALTY("מלוח"), SWEET("מתוק") }

/** A person the household cooks for (not necessarily an app user). */
data class FamilyMember(
    val id: String,
    val name: String,
    /** An emoji chosen as the member's icon; null = the first letter of the name. */
    val avatar: String? = null,
    val birthYear: Int? = null,
    /** 1..12 */
    val birthMonth: Int? = null,
    val gender: Gender? = null,
    val flavor: FlavorPref? = null,
    /** Foods the member clearly does not eat ("פטריות", "חציל"); dishes containing them are not offered for them. */
    val dislikes: List<String> = emptyList(),
    /** The member's own app account (household participant), so they can rate dishes themselves. */
    val linkedUserId: String? = null,
) {
    /** Age in whole months, or null when the birth year is unknown (month defaults to mid-year). */
    fun ageMonths(today: LocalDate): Int? {
        val year = birthYear ?: return null
        val month = birthMonth ?: 6
        val months = (today.year - year) * 12 + (today.monthValue - month)
        return months.takeIf { it >= 0 }
    }

    /** "3 שנים", "8 חודשים", "שנה וחצי"-style short label. */
    fun ageLabel(today: LocalDate): String? {
        val m = ageMonths(today) ?: return null
        return when {
            m < 1 -> "תינוק/ת"
            m < 12 -> if (m == 1) "חודש" else "$m חודשים"
            m < 24 -> if (m >= 18) "שנה וחצי" else "שנה"
            else -> "${m / 12} שנים"
        }
    }

    val initial: String get() = name.trim().take(1).ifEmpty { "?" }
}

/**
 * A recipe. Ingredients are free text with an optional amount for the recipe's own yield
 * ("4 ביצים", "500 גרם פסטה", "בצל").
 */
data class Dish(
    val id: String,
    val name: String,
    val ingredients: List<String>,
    /** Links the dish to its Tzameret food when it was taken from there. */
    val nutritionCode: Int? = null,
    /** How many servings the recipe, as written, makes. */
    val servings: Int = DEFAULT_SERVINGS,
    /** How many servings the household usually makes; null = as written. */
    val cookServings: Int? = null,
    val instructions: String = "",
    val flavor: Flavor? = null,
    /** Servings the ingredients currently on the shopping list were added for (to adjust them later). */
    val listServings: Int? = null,
) {
    val plannedServings: Int get() = cookServings ?: servings

    companion object {
        const val DEFAULT_SERVINGS = 4
    }
}

enum class MealSlot(val label: String) { BREAKFAST("בוקר"), LUNCH("צהריים"), DINNER("ערב"), SHABBAT("שבת") }

/** One cooked meal in the cycle. [day] is 0..(7×cycleWeeks−1), 0 = Sunday of week A; an empty [audience] means everyone. */
data class PlannedMeal(
    val day: Int,
    val slot: MealSlot,
    val dishId: String,
    val audience: Set<String> = emptySet(),
    /** Servings for this meal; null = the dish's planned servings. */
    val servings: Int? = null,
)

/** A change for one date only. [dishId] null = nothing is cooked in this slot on that date. */
data class DatedMeal(
    val epochDay: Long,
    val slot: MealSlot,
    val dishId: String?,
    val audience: Set<String> = emptySet(),
    val servings: Int? = null,
)

data class MealPlan(
    /** Length of the cycle, and how far ahead the calendar plans (one or two weeks). */
    val cycleWeeks: Int = 1,
    val meals: List<PlannedMeal> = emptyList(),
    /** Epoch day of a Sunday that starts week A; null = [DEFAULT_ANCHOR]. */
    val anchorEpochDay: Long? = null,
    val overrides: List<DatedMeal> = emptyList(),
    /** Meals marked as cooked: [MealPlanner.key]. */
    val cooked: Set<String> = emptySet(),
) {
    companion object {
        /** 1970-01-04 was a Sunday. */
        const val DEFAULT_ANCHOR = 3L
    }
}

/** Star ratings: 1..5, and [NEVER] = "בכלל לא" (rules the dish out for that member). */
object Rating {
    const val NEVER = 0
    const val MAX = 5
    const val NEUTRAL = 3
    fun label(stars: Int): String = when (stars) {
        NEVER -> "בכלל לא"
        1 -> "לא אוהב"
        2 -> "פחות"
        3 -> "בסדר"
        4 -> "אוהב"
        else -> "אוהב מאוד"
    }
}

data class MealData(
    val members: List<FamilyMember> = emptyList(),
    val dishes: List<Dish> = emptyList(),
    /** memberId → dishId → stars ([Rating]) */
    val ratings: Map<String, Map<String, Int>> = emptyMap(),
    val plan: MealPlan = MealPlan(),
) {
    fun rating(memberId: String, dishId: String): Int? = ratings[memberId]?.get(dishId)

    fun withRating(memberId: String, dishId: String, stars: Int?): MealData {
        val mine = ratings[memberId].orEmpty()
        val next = if (stars == null) mine - dishId else mine + (dishId to stars.coerceIn(Rating.NEVER, Rating.MAX))
        return copy(ratings = ratings + (memberId to next))
    }

    fun dish(id: String): Dish? = dishes.firstOrNull { it.id == id }

    fun withDish(dish: Dish): MealData =
        if (dishes.any { it.id == dish.id }) copy(dishes = dishes.map { if (it.id == dish.id) dish else it }) else copy(dishes = dishes + dish)

    fun withoutDish(dishId: String): MealData = copy(
        dishes = dishes.filter { it.id != dishId },
        ratings = ratings.mapValues { (_, m) -> m - dishId },
        plan = plan.copy(
            meals = plan.meals.filter { it.dishId != dishId },
            overrides = plan.overrides.filter { it.dishId != dishId },
        ),
    )

    fun withMember(member: FamilyMember): MealData =
        if (members.any { it.id == member.id }) copy(members = members.map { if (it.id == member.id) member else it }) else copy(members = members + member)

    fun withoutMember(memberId: String): MealData = copy(
        members = members.filter { it.id != memberId },
        ratings = ratings - memberId,
        plan = plan.copy(
            meals = plan.meals.map { it.copy(audience = it.audience - memberId) },
            overrides = plan.overrides.map { it.copy(audience = it.audience - memberId) },
        ),
    )

    /** The family member linked to an app account, if any. */
    fun memberFor(userId: String): FamilyMember? = members.firstOrNull { it.linkedUserId == userId }
}

/** A meal as it falls on a real date (cycle or one-off change). */
data class ResolvedMeal(
    val epochDay: Long,
    val slot: MealSlot,
    val dishId: String,
    val audience: Set<String>,
    val servings: Int?,
    /** True when it comes from a one-off change for this date. */
    val dated: Boolean,
    val cooked: Boolean,
)

object MealPlanner {
    val dayNames = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")
    val shortDayNames = listOf("א'", "ב'", "ג'", "ד'", "ה'", "ו'", "ש'")

    /** Slots offered for every day. */
    val daySlots = listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER)

    fun key(epochDay: Long, slot: MealSlot) = "$epochDay:${slot.name}"

    /** 0 = Sunday … 6 = Saturday. */
    fun weekdayIndex(date: LocalDate): Int = date.dayOfWeek.value % 7

    /** The Sunday on or before [date]. */
    fun sundayOf(date: LocalDate): LocalDate = date.minusDays(weekdayIndex(date).toLong())

    /** The cycle day (0..7×cycleWeeks−1) a date falls on. */
    fun cycleDay(plan: MealPlan, epochDay: Long): Int {
        val anchor = plan.anchorEpochDay ?: MealPlan.DEFAULT_ANCHOR
        return Math.floorMod(epochDay - anchor, (7L * plan.cycleWeeks.coerceIn(1, 2))).toInt()
    }

    /** Makes the week of [today] week A of the cycle. */
    fun anchorThisWeek(plan: MealPlan, today: LocalDate): MealPlan = plan.copy(anchorEpochDay = sundayOf(today).toEpochDay())

    /** Meals on one date: one-off changes win over the cycle, slot by slot. */
    fun mealsOn(data: MealData, epochDay: Long): List<ResolvedMeal> {
        val plan = data.plan
        val dated = plan.overrides.filter { it.epochDay == epochDay }
        val day = cycleDay(plan, epochDay)
        val fromCycle = plan.meals.filter { it.day == day && dated.none { d -> d.slot == it.slot } }
        val known = data.dishes.map { it.id }.toSet()
        return (
            dated.mapNotNull { d ->
                d.dishId?.let { ResolvedMeal(epochDay, d.slot, it, d.audience, d.servings, true, key(epochDay, d.slot) in plan.cooked) }
            } + fromCycle.map { ResolvedMeal(epochDay, it.slot, it.dishId, it.audience, it.servings, false, key(epochDay, it.slot) in plan.cooked) }
            ).filter { it.dishId in known }.sortedBy { it.slot.ordinal }
    }

    /** Sets (or clears, with null) the meal of a slot — in the cycle, or only on that date. */
    fun setMeal(
        data: MealData,
        epochDay: Long,
        slot: MealSlot,
        dishId: String?,
        audience: Set<String>,
        servings: Int?,
        onlyThisDate: Boolean,
    ): MealData {
        val plan = data.plan
        val day = cycleDay(plan, epochDay)
        val withoutDated = plan.overrides.filterNot { it.epochDay == epochDay && it.slot == slot }
        return if (onlyThisDate) {
            data.copy(plan = plan.copy(overrides = withoutDated + DatedMeal(epochDay, slot, dishId, audience, servings)))
        } else {
            val others = plan.meals.filterNot { it.day == day && it.slot == slot }
            val meals = if (dishId == null) others else others + PlannedMeal(day, slot, dishId, audience, servings)
            data.copy(plan = plan.copy(meals = meals, overrides = withoutDated))
        }
    }

    fun setCooked(data: MealData, epochDay: Long, slot: MealSlot, cooked: Boolean): MealData {
        val k = key(epochDay, slot)
        val set = if (cooked) data.plan.cooked + k else data.plan.cooked - k
        return data.copy(plan = data.plan.copy(cooked = set))
    }

    /** Drops one-off changes and cooked marks older than [keepDays] (the document stays small). */
    fun prune(data: MealData, today: LocalDate, keepDays: Long = 60): MealData {
        val min = today.toEpochDay() - keepDays
        return data.copy(
            plan = data.plan.copy(
                overrides = data.plan.overrides.filter { it.epochDay >= min },
                cooked = data.plan.cooked.filter { (it.substringBefore(':').toLongOrNull() ?: 0) >= min }.toSet(),
            ),
        )
    }

    /** Cooked meals in the last [days] days, newest first. */
    fun recentlyCooked(data: MealData, today: LocalDate, days: Int = 14): List<ResolvedMeal> {
        val end = today.toEpochDay()
        return (end downTo end - days + 1).flatMap { d -> mealsOn(data, d).filter { it.cooked } }
    }

    /** Dishes ranked for an audience: a "בכלל לא" or a strong dislike rules a dish out; otherwise stars and flavour. */
    fun rank(data: MealData, audience: Set<String>): List<Pair<Dish, Int>> {
        val people = data.members.filter { audience.isEmpty() || it.id in audience }
        return data.dishes.mapNotNull { dish ->
            val stars = people.mapNotNull { data.rating(it.id, dish.id) }
            if (Rating.NEVER in stars) return@mapNotNull null
            if (people.any { dislikes(it, dish) }) return@mapNotNull null
            val flavorBonus = people.count { p ->
                dish.flavor != null && (p.flavor == FlavorPref.BOTH || p.flavor?.name == dish.flavor.name)
            }
            dish to stars.sumOf { it - Rating.NEUTRAL } * 2 + flavorBonus
        }.sortedByDescending { it.second }
    }

    /** True when the dish contains a food the member clearly doesn't eat. */
    fun dislikes(member: FamilyMember, dish: Dish): Boolean {
        if (member.dislikes.isEmpty()) return false
        val text = HebrewStem.words((dish.ingredients + dish.name).joinToString(" "))
        return member.dislikes.any { d ->
            val words = HebrewStem.words(d)
            words.isNotEmpty() && words.all { w -> text.any { it.startsWith(w) } }
        }
    }

    /**
     * Ingredients for the meals from [fromEpochDay] for [days] days (cooked meals skipped),
     * each scaled to the meal's servings, with the dish it is for.
     */
    fun ingredientsFor(data: MealData, fromEpochDay: Long, days: Int): List<Pair<ScaledIngredient, String>> {
        val byId = data.dishes.associateBy { it.id }
        return (fromEpochDay until fromEpochDay + days).flatMap { d ->
            mealsOn(data, d).filter { !it.cooked }.mapNotNull { meal ->
                byId[meal.dishId]?.let { dish -> dish to (meal.servings ?: dish.plannedServings) }
            }
        }.flatMap { (dish, servings) -> Recipes.scaled(dish, servings).map { it to dish.name } }
    }

    /** "What can we cook from what we bought": dishes ordered by the share of ingredients already bought. */
    fun fromBought(data: MealData, boughtItemIds: Set<String>): List<Pair<Dish, Double>> =
        data.dishes.mapNotNull { dish ->
            if (dish.ingredients.isEmpty()) return@mapNotNull null
            val have = dish.ingredients.count { text -> Recipes.identity(text)?.id?.let { it in boughtItemIds } == true }
            if (have == 0) null else dish to have.toDouble() / dish.ingredients.size
        }.sortedByDescending { it.second }

    /** A few common dishes to start from (amounts for 4 servings). */
    val sampleDishes = listOf(
        Dish("s_shakshuka", "שקשוקה", listOf("8 ביצים", "6 עגבניות", "2 פלפל", "בצל", "רסק"), flavor = Flavor.SALTY),
        Dish("s_pasta", "פסטה ברוטב עגבניות", listOf("500 גרם פסטה", "רסק", "שום", "בצל", "200 גרם גבינה צהובה"), flavor = Flavor.SALTY),
        Dish("s_chicken", "עוף בתנור עם תפוחי אדמה", listOf("8 כרעיים", "1 ק\"ג תפוחי אדמה", "2 בצל", "שמן זית"), flavor = Flavor.SALTY),
        Dish("s_schnitzel", "שניצל ופירה", listOf("1 ק\"ג חזה עוף", "2 ביצים", "פירורי לחם", "1 ק\"ג תפוחי אדמה"), flavor = Flavor.SALTY),
        Dish("s_couscous", "קוסקוס עם ירקות", listOf("500 גרם קוסקוס", "ירקות לקוסקוס", "3 גזר", "3 קישואים"), flavor = Flavor.SALTY),
        Dish("s_salad", "סלט ירקות", listOf("4 עגבניות", "4 מלפפון", "בצל", "לימון"), flavor = Flavor.SALTY),
        Dish("s_pancakes", "פנקייק", listOf("2 ביצים", "300 גרם קמח", "1 ליטר חלב", "סוכר"), flavor = Flavor.SWEET),
    )
}

/** An ingredient after scaling: what goes on the list. */
data class ScaledIngredient(val item: ItemIdentity, val quantity: Quantity?)

/** Recipe arithmetic: parse ingredient lines and scale them to a number of servings. */
object Recipes {
    fun identity(text: String): ItemIdentity? = QuickEntryParser.parse(text)?.item

    /** The dish's ingredients for [servings] (the recipe's own amounts × servings / yield). */
    fun scaled(dish: Dish, servings: Int): List<ScaledIngredient> {
        val factor = servings.toDouble() / dish.servings.coerceAtLeast(1)
        return dish.ingredients.mapNotNull { text ->
            val parsed = QuickEntryParser.parse(text) ?: return@mapNotNull null
            ScaledIngredient(parsed.item, parsed.quantity?.let { scale(it, factor) })
        }.distinctBy { it.item.id }
    }

    /** Scales a quantity for shopping: counts round up to whole units, weights/volumes to a tidy step. */
    fun scale(q: Quantity, factor: Double): Quantity? {
        if (factor <= 0) return null
        val raw = q.amount * factor
        val amount = when (q.dimension) {
            Dimension.COUNT -> ceil(raw - 1e-9).coerceAtLeast(1.0)
            else -> {
                val base = raw * q.unit.toBase
                val step = when {
                    base >= 1000 -> 50.0
                    base >= 100 -> 10.0
                    else -> 5.0
                }
                ((base / step).roundToInt().coerceAtLeast(1) * step) / q.unit.toBase
            }
        }
        return if (q.dimension == Dimension.COUNT) Quantity(amount, q.unit) else Quantity.fromBase(amount * q.unit.toBase, q.dimension)
    }

    /** One ingredient line as shown for [servings]: "6 ביצים" for "4 ביצים" ×1.5. */
    fun display(dish: Dish, text: String, servings: Int): String {
        val parsed = QuickEntryParser.parse(text) ?: return text
        val q = parsed.quantity ?: return parsed.item.displayName
        val factor = servings.toDouble() / dish.servings.coerceAtLeast(1)
        val scaled = scale(q, factor) ?: q
        val weight = PieceWeights.estimate(parsed.item.name, scaled)?.let { "  " + PieceWeights.label(it) } ?: ""
        return "${parsed.item.displayName} · ${scaled.format()}$weight"
    }
}

/**
 * Keeps the shopping list in step with a recipe's servings. Moving from [fromServings] to [toServings]
 * changes each ingredient's open line by the difference (so a line that was merged with other needs keeps them);
 * ingredients that are no longer on the list are added again.
 */
object RecipeListSync {
    sealed interface Change {
        data class Update(val lineId: String, val quantity: Quantity?, val recipeQuantity: Quantity?) : Change
        data class Add(val ingredient: ScaledIngredient) : Change
    }

    fun plan(lines: List<ListLine>, dish: Dish, fromServings: Int, toServings: Int): List<Change> {
        val before = Recipes.scaled(dish, fromServings).associateBy { it.item.id }
        return Recipes.scaled(dish, toServings).mapNotNull { now ->
            val open = lines.filter { it.status == LineStatus.NEEDED && it.itemId == now.item.id }
                .sortedByDescending { l -> l.recipe == dish.name || l.recipeParts.any { it.dish == dish.name } }
                .firstOrNull()
                ?: return@mapNotNull Change.Add(now)
            val was = before[now.item.id]?.quantity
            val target = now.quantity ?: return@mapNotNull null
            val current = open.quantity
            val next = when {
                current == null -> target
                was == null || was.dimension != current.dimension || target.dimension != current.dimension -> target
                else -> {
                    val base = current.baseAmount - was.baseAmount + target.baseAmount
                    if (base <= 0) target else if (current.dimension == Dimension.COUNT) Quantity(base, current.unit) else Quantity.fromBase(base, current.dimension)
                }
            }
            if (next == current) null else Change.Update(open.id, next, target)
        }
    }

    /** Open lines that came from this recipe. */
    fun onList(lines: List<ListLine>, dish: Dish): List<ListLine> =
        lines.filter { l ->
            l.status == LineStatus.NEEDED &&
                ((l.recipe == dish.name && l.source == LineSource.RECIPE) || l.recipeParts.any { it.dish == dish.name && !it.fromMenu })
        }
}

/** Port for the household's meal-planning data (stored as one document). */
interface MealRepository {
    fun meals(householdId: String): kotlinx.coroutines.flow.Flow<MealData>
    suspend fun save(householdId: String, data: MealData)
}

class InMemoryMealRepository : MealRepository {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(MealData())
    override fun meals(householdId: String) = state
    override suspend fun save(householdId: String, data: MealData) { state.value = data }
}
