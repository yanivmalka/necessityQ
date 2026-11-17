package app.necessityq.domain

/*
 * Meal planning — first, conceptual layer (data model + simple rules), prepared for a fuller feature:
 *  - family profiles with food preferences,
 *  - dishes with ingredients,
 *  - a weekly / bi-weekly cyclic menu with a target audience per meal,
 *  - menu → shopping list, and bought items → dish ideas.
 */

/** A person the household cooks for (not necessarily an app user). */
data class FamilyMember(val id: String, val name: String)

enum class Taste(val label: String, val score: Int) {
    LOVE("אוהב מאוד", 3),
    LIKE("אוהב", 2),
    OK("בסדר", 1),
    DISLIKE("לא אוהב", -2),
    NEVER("בכלל לא", -100),
}

/** A dish; [nutritionCode] links it to its Tzameret food when it was taken from there. */
data class Dish(val id: String, val name: String, val ingredients: List<String>, val nutritionCode: Int? = null)

enum class MealSlot(val label: String) { BREAKFAST("בוקר"), LUNCH("צהריים"), DINNER("ערב"), SHABBAT("שבת") }

/** One cooked meal in the cycle. [day] is 0..(7×cycleWeeks−1); an empty [audience] means everyone. */
data class PlannedMeal(val day: Int, val slot: MealSlot, val dishId: String, val audience: Set<String> = emptySet())

data class MealPlan(val cycleWeeks: Int = 1, val meals: List<PlannedMeal> = emptyList())

data class MealData(
    val members: List<FamilyMember> = emptyList(),
    val dishes: List<Dish> = emptyList(),
    /** memberId → dishId → taste */
    val tastes: Map<String, Map<String, Taste>> = emptyMap(),
    val plan: MealPlan = MealPlan(),
)

object MealPlanner {
    val dayNames = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")

    /** Dishes ranked for an audience: anyone who never eats it rules it out; otherwise the sum of tastes. */
    fun rank(data: MealData, audience: Set<String>): List<Pair<Dish, Int>> {
        val people = audience.ifEmpty { data.members.map { it.id }.toSet() }
        return data.dishes.mapNotNull { dish ->
            val tastes = people.mapNotNull { data.tastes[it]?.get(dish.id) }
            if (Taste.NEVER in tastes) null else dish to tastes.sumOf { it.score }
        }.sortedByDescending { it.second }
    }

    /** Ingredients needed for one week of the cycle, each with the dish it is for. */
    fun ingredientsForWeek(data: MealData, week: Int): List<Pair<String, String>> {
        val byId = data.dishes.associateBy { it.id }
        return data.plan.meals.filter { it.day / 7 == week }
            .mapNotNull { byId[it.dishId] }
            .flatMap { dish -> dish.ingredients.map { it.trim() to dish.name } }
            .filter { it.first.isNotEmpty() }
            .distinctBy { HebrewText.matchKey(it.first) }
    }

    /** "What can we cook from what we bought": dishes ordered by the share of ingredients already bought. */
    fun fromBought(data: MealData, boughtItemIds: Set<String>): List<Pair<Dish, Double>> =
        data.dishes.mapNotNull { dish ->
            if (dish.ingredients.isEmpty()) return@mapNotNull null
            val have = dish.ingredients.count { ItemIdentity.fromText(it).id in boughtItemIds }
            if (have == 0) null else dish to have.toDouble() / dish.ingredients.size
        }.sortedByDescending { it.second }

    /** A few common dishes to start from. */
    val sampleDishes = listOf(
        Dish("s_shakshuka", "שקשוקה", listOf("ביצים", "עגבניות", "פלפל", "בצל", "רסק")),
        Dish("s_pasta", "פסטה ברוטב עגבניות", listOf("פסטה", "רסק", "שום", "בצל", "גבינה צהובה")),
        Dish("s_chicken", "עוף בתנור עם תפוחי אדמה", listOf("כרעיים", "תפוחי אדמה", "בצל", "שמן זית")),
        Dish("s_schnitzel", "שניצל ופירה", listOf("חזה עוף", "ביצים", "פירורי לחם", "תפוחי אדמה")),
        Dish("s_couscous", "קוסקוס עם ירקות", listOf("קוסקוס", "ירקות לקוסקוס", "גזר", "קישואים")),
        Dish("s_salad", "סלט ירקות", listOf("עגבניות", "מלפפון", "בצל", "לימון")),
    )
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
