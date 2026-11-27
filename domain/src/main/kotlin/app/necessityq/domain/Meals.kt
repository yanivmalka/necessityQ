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

/** What a saved "dish" is: a meal that is cooked and rated, or a kit (a shopping bundle, not necessarily food). */
enum class DishKind(val label: String) { MEAL("מאכל"), KIT("ערכה") }

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
    /** Ingredients (item ids) left out when the recipe went on the list, so changing servings does not add them. */
    val listSkip: Set<String> = emptySet(),
    /** What the household chose the last time the recipe was put on the list; null = never confirmed. */
    val listMemory: ListMemory? = null,
    /** [DishKind.KIT]: no flavour, no family ratings, no "cooked" wording; it is a bundle of products. */
    val kind: DishKind = DishKind.MEAL,
    /** False when nobody said how many servings the recipe makes ([servings] is then only the default, not a fact). */
    val servingsKnown: Boolean = true,
    /** The web page the recipe was imported from. */
    val sourceUrl: String? = null,
) {
    val isKit: Boolean get() = kind == DishKind.KIT

    val plannedServings: Int get() = cookServings ?: servings

    companion object {
        const val DEFAULT_SERVINGS = 4
    }
}

/**
 * What was confirmed when a recipe first went on the list, so the next time it goes on with the same products.
 * Keys are the ingredient's base id ([ScaledIngredient.baseId]): the item as the recipe line names it.
 */
data class ListMemory(
    /** The kind chosen for an ingredient: baseId → "מלא", "3%", "בסמטי"… */
    val variants: Map<String, String> = emptyMap(),
    /** Ingredients that were offered (so ones added to the recipe later can be told apart). */
    val known: Set<String> = emptySet(),
    /** Offered ingredients that were left off the list. */
    val skipped: Set<String> = emptySet(),
)

enum class MealSlot(val label: String) {
    BREAKFAST("בוקר"), LUNCH("צהריים"), DINNER("ערב"), SHABBAT("שבת"),
    /** Extra slots a day can have besides the three meals: a snack or dessert, or a kit. */
    SNACK("נשנוש"), OTHER("אחר"),
}

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
    /** Item ids the household always has at home ("יש בבית"): recipes and the menu leave them off the list. */
    val pantry: Set<String> = emptySet(),
    /** Item ids the household said it does need to buy, although recipes assume it is at home ([Staples]). */
    val buy: Set<String> = emptySet(),
    /** The pack size the household buys, per item ([PackSizes.keyFor] → size in grams / ml / pieces); none = whatever fits. */
    val packs: Map<String, Long> = emptyMap(),
    /** The first-list survey's answers ([FirstListSurvey]); null until it was filled in or skipped. */
    val baseline: HouseholdBaseline? = null,
    /** How often the household goes shopping: the amounts of every trip's list are for this long ([TripSizer]). */
    val cadence: TripCadence = TripCadence.DEFAULT,
) {
    /** Left off the list: marked "יש בבית", or a staple the household has not said it needs ([Staples]). */
    fun isAtHome(ingredient: ScaledIngredient): Boolean = isAtHome(ingredient.base, ingredient.quantity)

    fun isAtHome(item: ItemIdentity, quantity: Quantity?): Boolean =
        item.id in pantry || (item.id !in buy && Staples.assumedAtHome(item, quantity))

    /**
     * Whether [ingredient] of [dish] stays off the list when the recipe is put on it: what was decided the last time
     * for ingredients that were offered then, otherwise whether it is at home.
     */
    fun leavesOut(dish: Dish, ingredient: ScaledIngredient): Boolean {
        val memory = dish.listMemory
        return if (memory != null && ingredient.baseId in memory.known) ingredient.baseId in memory.skipped else isAtHome(ingredient)
    }

    fun withPack(key: String, size: Long?): MealData = copy(packs = if (size == null) packs - key else packs + (key to size))

    fun rating(memberId: String, dishId: String): Int? = ratings[memberId]?.get(dishId)

    fun withPantry(itemId: String, atHome: Boolean): MealData =
        if (atHome) copy(pantry = pantry + itemId, buy = buy - itemId) else copy(pantry = pantry - itemId, buy = buy + itemId)

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

    /** Slots a day can add when needed (shown only once they hold something, or when added). */
    val extraSlots = listOf(MealSlot.SNACK, MealSlot.OTHER)

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
            if (!dish.isKit && people.any { dislikes(it, dish) }) return@mapNotNull null
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
     * each scaled to the meal's servings, with the dish it is for. What is always at home is left out.
     */
    fun ingredientsFor(data: MealData, fromEpochDay: Long, days: Int): List<Pair<ScaledIngredient, String>> {
        val byId = data.dishes.associateBy { it.id }
        return (fromEpochDay until fromEpochDay + days).flatMap { d ->
            mealsOn(data, d).filter { !it.cooked }.mapNotNull { meal ->
                byId[meal.dishId]?.let { dish -> dish to (meal.servings ?: dish.plannedServings) }
            }
        }.flatMap { (dish, servings) ->
            Recipes.scaled(dish, servings).filterNot { data.leavesOut(dish, it) }.map { it to dish.name }
        }
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
data class ScaledIngredient(
    /** What goes on the list: the item with the kind the household chose ([ListMemory.variants]), if any. */
    val item: ItemIdentity,
    val quantity: Quantity?,
    /** The item as the recipe line names it: what "at home", "left off" and the remembered choices are keyed by. */
    val base: ItemIdentity = item,
) {
    val baseId: String get() = base.id
}

/** Recipe arithmetic: parse ingredient lines and scale them to a number of servings. */
object Recipes {
    /** One ingredient line read as a recipe line: kitchen units and preparation words understood ([RecipeLine]). */
    fun parse(text: String): ParsedEntry? = RecipeLine.parse(text)

    fun identity(text: String): ItemIdentity? = parse(text)?.item

    /** The dish's ingredients for [servings] (the recipe's own amounts × servings / yield), without the item ids in [skip]. */
    fun scaled(dish: Dish, servings: Int, skip: Set<String> = emptySet()): List<ScaledIngredient> {
        val factor = servings.toDouble() / dish.servings.coerceAtLeast(1)
        val variants = dish.listMemory?.variants.orEmpty()
        return dish.ingredients.mapNotNull { text ->
            val parsed = parse(text) ?: return@mapNotNull null
            if (parsed.item.id in skip) return@mapNotNull null
            ScaledIngredient(listItem(parsed.item, variants), parsed.quantity?.let { scale(it, factor) }, parsed.item)
        }.distinctBy { it.baseId }
    }

    /** [item] with the kind chosen for it in [variants] (keyed by the item's own id), if any. */
    fun listItem(item: ItemIdentity, variants: Map<String, String>): ItemIdentity =
        variants[item.id]?.takeIf { item.variant == null }?.let { item.copy(variant = it) } ?: item

    /**
     * The method split into steps: one per non-empty line, without numbering the user typed
     * ("1.", "2)", "-", "•"). A single paragraph is split at sentence ends.
     */
    fun steps(instructions: String): List<String> {
        val lines = instructions.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val parts = if (lines.size == 1) lines.single().split(Regex("(?<=[.!?])\\s+")) else lines
        return parts.map { it.replace(Regex("^(?:\\d+\\s*[.)\\-:]|[-•*])(?=\\s)\\s*"), "").trim() }.filter { it.isNotEmpty() }
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

    /**
     * The amount a recipe means when its line has none: one piece of produce counted by the piece ("בצל" = one
     * onion). Null for everything else ("שמן זית", "רסק"): a bottle or a can serves several recipes.
     */
    fun defaultAmount(item: ItemIdentity): Quantity? =
        if (PieceWeights.gramsPerPiece(item.name) != null) Quantity.pieces(1) else null

    /** One ingredient line as shown for [servings]: "6 ביצים" for "4 ביצים" ×1.5. */
    fun display(dish: Dish, text: String, servings: Int): String {
        val parsed = parse(text) ?: return text
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

    fun plan(lines: List<ListLine>, dish: Dish, fromServings: Int, toServings: Int, skip: Set<String> = emptySet()): List<Change> {
        val before = Recipes.scaled(dish, fromServings, skip).associateBy { it.item.id }
        return Recipes.scaled(dish, toServings, skip).mapNotNull { now ->
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

    /** Taking a recipe off the list, per line: shrink it by the dish's part, or delete a line that only served the dish. */
    sealed interface Removal {
        data class Shrink(val lineId: String, val quantity: Quantity?, val parts: List<RecipePart>) : Removal
        data class Delete(val lineId: String) : Removal
    }

    fun removal(lines: List<ListLine>, dish: Dish): List<Removal> = onList(lines, dish).map { line ->
        val part = line.recipeParts.firstOrNull { it.dish == dish.name && !it.fromMenu }
        val rest = line.recipeParts.filterNot { it.dish == dish.name && !it.fromMenu }
        val current = line.quantity
        val partQ = part?.quantity
        val remaining = if (current != null && partQ != null && current.dimension == partQ.dimension) current.baseAmount - partQ.baseAmount else null
        when {
            remaining != null && remaining > 1e-9 -> {
                val q = if (current!!.dimension == Dimension.COUNT) Quantity(remaining, current.unit) else Quantity.fromBase(remaining, current.dimension)
                Removal.Shrink(line.id, q, rest)
            }
            rest.isEmpty() && (line.source == LineSource.RECIPE || remaining != null) -> Removal.Delete(line.id)
            else -> Removal.Shrink(line.id, current, rest)
        }
    }

    /**
     * Whether the recipe has open lines on the list from any route: put on by itself ([onList]) or through the
     * menu ("מרכיבים לרשימה" for the week). Used to show the same state everywhere; the sync itself uses [onList].
     */
    fun onListAny(lines: List<ListLine>, dish: Dish): Boolean =
        lines.any { l -> l.status == LineStatus.NEEDED && ((l.recipe == dish.name && l.source == LineSource.RECIPE) || l.recipeParts.any { it.dish == dish.name }) }

    /** Open lines that came from this recipe. */
    fun onList(lines: List<ListLine>, dish: Dish): List<ListLine> =
        lines.filter { l ->
            l.status == LineStatus.NEEDED &&
                ((l.recipe == dish.name && l.source == LineSource.RECIPE) || l.recipeParts.any { it.dish == dish.name && !it.fromMenu })
        }
}

/** Editing a recipe's ingredient lines while it is shown for some number of servings. */
object RecipeEdits {
    /** How far one tap on + / − moves an amount as shown: one unit, or 10 / 50 / 100 in grams or ml. */
    fun step(shown: Quantity): Double = when {
        shown.dimension == Dimension.COUNT -> 1.0
        shown.baseAmount >= 1000 -> 100.0
        shown.baseAmount >= 100 -> 50.0
        else -> 10.0
    }

    private fun factor(dish: Dish, servings: Int) = servings.toDouble() / dish.servings.coerceAtLeast(1)

    /** A quantity shown for [servings], back in the recipe's own yield (counts kept just under, so rounding up shows it again). */
    fun toYield(shown: Quantity, dish: Dish, servings: Int): Quantity {
        val raw = shown.baseAmount / factor(dish, servings)
        if (shown.dimension == Dimension.COUNT) {
            return Quantity((kotlin.math.floor(raw * 100) / 100).coerceAtLeast(0.01), shown.unit)
        }
        // whole grams / ml; kilos and liters only when they stay exact in two decimals
        val base = kotlin.math.round(raw).coerceAtLeast(1.0)
        return if (base >= 1000 && base % 10 == 0.0) Quantity.fromBase(base, shown.dimension)
        else Quantity(base, if (shown.dimension == Dimension.WEIGHT) QuantityUnit.GRAM else QuantityUnit.MILLILITER)
    }

    /** Ingredient text from an amount and the item's words: "6 יח' ביצים", "1.5 ק\"ג עגבניות". */
    fun format(quantity: Quantity?, name: String): String =
        if (quantity == null) name else "${Quantity.formatNumber(quantity.amount)} ${quantity.unit.label} $name"

    private fun nameOf(parsed: ParsedEntry) = parsed.nameText.ifBlank { parsed.item.displayName }

    /**
     * One step more ([direction] = 1) or less (−1) of ingredient [index], as shown for [servings].
     * An ingredient without an amount gets one unit on +; an amount never goes below one step (remove it instead).
     */
    fun adjust(dish: Dish, index: Int, servings: Int, direction: Int): Dish {
        val line = dish.ingredients.getOrNull(index) ?: return dish
        val parsed = Recipes.parse(line) ?: return dish
        val shown = parsed.quantity?.let { Recipes.scale(it, factor(dish, servings)) }
        val next = if (shown == null) {
            if (direction > 0) Quantity.pieces(1) else return dish
        } else {
            val step = step(shown)
            val base = shown.baseAmount + direction * step
            if (base < step - 1e-9) return dish
            if (shown.dimension == Dimension.COUNT) Quantity(base, shown.unit) else Quantity.fromBase(base, shown.dimension)
        }
        val updated = format(toYield(next, dish, servings), nameOf(parsed))
        return dish.copy(ingredients = dish.ingredients.mapIndexed { i, t -> if (i == index) updated else t })
    }

    fun remove(dish: Dish, index: Int): Dish =
        dish.copy(ingredients = dish.ingredients.filterIndexed { i, _ -> i != index })

    /** Adds an ingredient typed for the servings on screen ("300 גרם פטריות" for 6 servings); stored in the recipe's yield. */
    fun add(dish: Dish, typed: String, servings: Int): Dish {
        val parsed = Recipes.parse(typed) ?: return dish
        val stored = parsed.quantity?.let { format(toYield(it, dish, servings), nameOf(parsed)) } ?: typed.trim()
        return dish.copy(ingredients = dish.ingredients + stored)
    }
}

/**
 * Puts the menu's ingredients on the list so there is enough for every planned meal. Each line keeps how much of
 * it each dish needs ([RecipePart] with fromMenu); adding the menu again sets those parts to what the meals need
 * now and changes the line by the difference. So two dishes that share an ingredient add up ("6 עגבניות" for
 * shakshuka and "4 עגבניות" for a salad → 10), the same dish on two days counts twice, and pressing the button
 * again changes nothing. Amounts added by hand or from the recipe screen are kept on top.
 */
object MenuListSync {
    sealed interface Change {
        data class Add(val item: ItemIdentity, val quantity: Quantity?, val parts: List<RecipePart>) : Change
        data class Update(val lineId: String, val quantity: Quantity?, val parts: List<RecipePart>) : Change
    }

    /** Per item: what each dish needs (summed over its meals), in the order the items first appear. */
    fun needs(ingredients: List<Pair<ScaledIngredient, String>>): List<Pair<ItemIdentity, List<RecipePart>>> =
        ingredients.groupBy { it.first.item.id }.values.map { group ->
            val item = group.first().first.item
            val parts = group.groupBy { it.second }.map { (dish, uses) ->
                val q = uses.map { it.first.quantity ?: Recipes.defaultAmount(item) }
                    .reduce { a, b -> ListRules.combine(item.name, a, b) }
                RecipePart(dish, q, fromMenu = true)
            }
            item to parts
        }

    /** Total of [parts] for an item called [name]. */
    fun total(name: String, parts: List<RecipePart>): Quantity? =
        parts.map { it.quantity }.fold(null as Quantity?) { acc, q -> ListRules.combine(name, acc, q) }

    fun plan(lines: List<ListLine>, ingredients: List<Pair<ScaledIngredient, String>>): List<Change> =
        needs(ingredients).mapNotNull { (item, parts) ->
            val open = lines.firstOrNull { it.status == LineStatus.NEEDED && it.itemId == item.id }
                ?: return@mapNotNull Change.Add(item, total(item.name, parts), parts)
            val dishes = parts.map { it.dish }.toSet()
            val before = open.recipeParts.filter { it.fromMenu && it.dish in dishes }
            val nextParts = open.recipeParts.filterNot { it.fromMenu && it.dish in dishes } + parts
            val quantity = adjusted(item.name, open.quantity, total(item.name, before), total(item.name, parts))
            if (quantity == open.quantity && nextParts.toSet() == open.recipeParts.toSet()) null
            else Change.Update(open.id, quantity, nextParts)
        }

    /** [current] without what the menu asked for [before], plus what it asks for [now]. */
    internal fun adjusted(name: String, current: Quantity?, before: Quantity?, now: Quantity?): Quantity? {
        if (current == null) return now
        if (now == null) return current
        if (before == null) return ListRules.combine(name, current, now)
        val was = PieceWeights.convert(name, before, current.dimension)
        val target = PieceWeights.convert(name, now, current.dimension)
        if (was == null || target == null) return ListRules.combine(name, current, now)
        val base = current.baseAmount - was.baseAmount + target.baseAmount
        return when {
            base <= 1e-9 -> target
            current.dimension == Dimension.COUNT -> Quantity(base, current.unit)
            else -> Quantity.fromBase(base, current.dimension)
        }
    }
}

/** What adding a recipe to the list would do, ingredient by ingredient, before anything changes. */
object RecipeListPreview {
    enum class Kind { NEW, MERGE, AT_HOME }

    data class Row(
        /** What would go on the list (with the chosen kind). */
        val item: ItemIdentity,
        /** The recipe's amount for the servings. */
        val quantity: Quantity?,
        val kind: Kind,
        /** For [Kind.MERGE]: what the list has now, and will have after. */
        val current: Quantity? = null,
        val after: Quantity? = null,
        /** The item as the recipe line names it (before a kind is chosen). */
        val base: ItemIdentity = item,
    ) {
        val baseId: String get() = base.id
    }

    fun of(lines: List<ListLine>, dish: Dish, servings: Int, pantry: Set<String>, buy: Set<String> = emptySet()): List<Row> =
        Recipes.scaled(dish, servings).map { ing ->
            val open = lines.firstOrNull { it.status == LineStatus.NEEDED && it.itemId == ing.item.id }
            when {
                ing.baseId in pantry || (ing.baseId !in buy && Staples.assumedAtHome(ing.base, ing.quantity)) ->
                    Row(ing.item, ing.quantity, Kind.AT_HOME, base = ing.base)
                open != null -> {
                    val now = open.quantity
                    val after = when {
                        now == null -> ing.quantity
                        ing.quantity == null -> now
                        else -> (now + ing.quantity) ?: now
                    }
                    Row(ing.item, ing.quantity, Kind.MERGE, now, after, ing.base)
                }
                else -> Row(ing.item, ing.quantity, Kind.NEW, base = ing.base)
            }
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
