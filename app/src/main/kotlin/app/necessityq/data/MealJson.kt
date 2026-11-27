package app.necessityq.data

import app.necessityq.domain.DatedMeal
import app.necessityq.domain.Dish
import app.necessityq.domain.DishKind
import app.necessityq.domain.FamilyMember
import app.necessityq.domain.Flavor
import app.necessityq.domain.FlavorPref
import app.necessityq.domain.BaselineAmount
import app.necessityq.domain.Gender
import app.necessityq.domain.HouseholdBaseline
import app.necessityq.domain.TripCadence
import app.necessityq.domain.HouseholdMakeup
import app.necessityq.domain.ListMemory
import app.necessityq.domain.MealData
import app.necessityq.domain.MealPlan
import app.necessityq.domain.MealSlot
import app.necessityq.domain.PlannedMeal
import app.necessityq.domain.QuantityUnit
import app.necessityq.domain.Rating
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * JSON form of [MealData]; unknown/legacy values are skipped rather than failing.
 * Ratings are also written in the older word form ("tastes") so a member still on an older version keeps them.
 */
object MealJson {
    @Serializable private data class M(
        val id: String,
        val name: String,
        val avatar: String? = null,
        val birthYear: Int? = null,
        val birthMonth: Int? = null,
        val gender: String? = null,
        val flavor: String? = null,
        val dislikes: List<String> = emptyList(),
        val linkedUserId: String? = null,
    )
    @Serializable private data class D(
        val id: String,
        val name: String,
        val ingredients: List<String> = emptyList(),
        val nutritionCode: Int? = null,
        val servings: Int = Dish.DEFAULT_SERVINGS,
        val cookServings: Int? = null,
        val instructions: String = "",
        val flavor: String? = null,
        val listServings: Int? = null,
        val listSkip: List<String> = emptyList(),
        /** What was confirmed the first time the recipe went on the list ([ListMemory]); false = never. */
        val hasMemory: Boolean = false,
        val variants: Map<String, String> = emptyMap(),
        val known: List<String> = emptyList(),
        val skipped: List<String> = emptyList(),
        /** "MEAL" (default) or "KIT"; older versions ignore it and show a kit as a recipe. */
        val kind: String? = null,
        /** True when the recipe did not say how many servings it makes (older data: false). */
        val servingsUnknown: Boolean = false,
        val sourceUrl: String? = null,
    )
    @Serializable private data class P(val day: Int, val slot: String, val dishId: String, val audience: List<String> = emptyList(), val servings: Int? = null)
    @Serializable private data class O(val epochDay: Long, val slot: String, val dishId: String? = null, val audience: List<String> = emptyList(), val servings: Int? = null)
    /** One answer of the first-list survey: [amount] of [unit] (a [QuantityUnit] name) every [days]; 0 = not bought. */
    @Serializable private data class BA(val amount: Double, val unit: String, val days: Int)
    /** The first-list survey ([HouseholdBaseline]); [ages] only when the home was described in the survey. */
    @Serializable private data class B(
        val filledAt: Long,
        val amounts: Map<String, BA> = emptyMap(),
        val ages: List<Int>? = null,
        val skipped: Boolean = false,
    )
    @Serializable private data class Root(
        val members: List<M> = emptyList(),
        val dishes: List<D> = emptyList(),
        val tastes: Map<String, Map<String, String>> = emptyMap(),
        val ratings: Map<String, Map<String, Int>> = emptyMap(),
        val cycleWeeks: Int = 1,
        val meals: List<P> = emptyList(),
        val anchorEpochDay: Long? = null,
        val overrides: List<O> = emptyList(),
        val cooked: List<String> = emptyList(),
        val pantry: List<String> = emptyList(),
        val buy: List<String> = emptyList(),
        val packs: Map<String, Long> = emptyMap(),
        val baseline: B? = null,
        /** [TripCadence.days]; absent = a weekly trip. */
        val tripDays: Int? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val legacyToStars = mapOf("LOVE" to 5, "LIKE" to 4, "OK" to 3, "DISLIKE" to 2, "NEVER" to Rating.NEVER)
    private fun starsToLegacy(stars: Int) = when {
        stars == Rating.NEVER -> "NEVER"
        stars >= 5 -> "LOVE"
        stars == 4 -> "LIKE"
        stars == 3 -> "OK"
        else -> "DISLIKE"
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

    fun encode(data: MealData): String = json.encodeToString(
        Root.serializer(),
        Root(
            members = data.members.map {
                M(it.id, it.name, it.avatar, it.birthYear, it.birthMonth, it.gender?.name, it.flavor?.name, it.dislikes, it.linkedUserId)
            },
            dishes = data.dishes.map {
                D(it.id, it.name, it.ingredients, it.nutritionCode, it.servings, it.cookServings, it.instructions, it.flavor?.name, it.listServings, it.listSkip.toList(),
                    it.listMemory != null, it.listMemory?.variants.orEmpty(), it.listMemory?.known?.toList().orEmpty(), it.listMemory?.skipped?.toList().orEmpty(),
                    it.kind.takeIf { k -> k != DishKind.MEAL }?.name, !it.servingsKnown, it.sourceUrl)
            },
            tastes = data.ratings.mapValues { (_, m) -> m.mapValues { starsToLegacy(it.value) } },
            ratings = data.ratings,
            cycleWeeks = data.plan.cycleWeeks,
            meals = data.plan.meals.map { P(it.day, it.slot.name, it.dishId, it.audience.toList(), it.servings) },
            anchorEpochDay = data.plan.anchorEpochDay,
            overrides = data.plan.overrides.map { O(it.epochDay, it.slot.name, it.dishId, it.audience.toList(), it.servings) },
            cooked = data.plan.cooked.toList(),
            pantry = data.pantry.toList(),
            buy = data.buy.toList(),
            packs = data.packs,
            baseline = data.baseline?.let { b ->
                B(b.filledAtMillis, b.amounts.mapValues { (_, a) -> BA(a.amount, a.unit.name, a.periodDays) }, b.makeup?.ages, b.skipped)
            },
            tripDays = data.cadence.takeIf { it != TripCadence.DEFAULT }?.days,
        ),
    )

    fun decode(text: String?): MealData {
        if (text.isNullOrBlank()) return MealData()
        val r = runCatching { json.decodeFromString(Root.serializer(), text) }.getOrNull() ?: return MealData()
        val legacy = r.tastes.mapValues { (_, m) -> m.mapNotNull { (k, v) -> legacyToStars[v]?.let { k to it } }.toMap() }
        // new-form ratings win; dishes rated only by an older version keep their word rating
        val ratings = (legacy.keys + r.ratings.keys).associateWith { id -> legacy[id].orEmpty() + r.ratings[id].orEmpty() }
        return MealData(
            members = r.members.map {
                FamilyMember(
                    it.id, it.name, it.avatar, it.birthYear, it.birthMonth?.takeIf { m -> m in 1..12 },
                    enumOrNull<Gender>(it.gender), enumOrNull<FlavorPref>(it.flavor), it.dislikes, it.linkedUserId,
                )
            },
            dishes = r.dishes.map {
                Dish(
                    it.id, it.name, it.ingredients, it.nutritionCode, it.servings.coerceAtLeast(1), it.cookServings?.coerceAtLeast(1),
                    it.instructions, enumOrNull<Flavor>(it.flavor), it.listServings, it.listSkip.toSet(),
                    if (it.hasMemory) ListMemory(it.variants, it.known.toSet(), it.skipped.toSet()) else null,
                    kind = enumOrNull<DishKind>(it.kind) ?: DishKind.MEAL,
                    servingsKnown = !it.servingsUnknown,
                    sourceUrl = it.sourceUrl,
                )
            },
            ratings = ratings,
            plan = MealPlan(
                cycleWeeks = r.cycleWeeks.coerceIn(1, 2),
                meals = r.meals.mapNotNull { p ->
                    enumOrNull<MealSlot>(p.slot)?.let { PlannedMeal(p.day, it, p.dishId, p.audience.toSet(), p.servings) }
                },
                anchorEpochDay = r.anchorEpochDay,
                overrides = r.overrides.mapNotNull { o ->
                    enumOrNull<MealSlot>(o.slot)?.let { DatedMeal(o.epochDay, it, o.dishId, o.audience.toSet(), o.servings) }
                },
                cooked = r.cooked.toSet(),
            ),
            pantry = r.pantry.toSet(),
            buy = r.buy.toSet(),
            packs = r.packs,
            baseline = r.baseline?.let { b ->
                HouseholdBaseline(
                    filledAtMillis = b.filledAt,
                    amounts = b.amounts.mapNotNull { (name, a) ->
                        val unit = enumOrNull<QuantityUnit>(a.unit) ?: return@mapNotNull null
                        if (!a.amount.isFinite() || a.amount < 0.0 || a.days <= 0) return@mapNotNull null
                        name to BaselineAmount(a.amount, unit, a.days)
                    }.toMap(),
                    makeup = b.ages?.let { HouseholdMakeup(it.filter { age -> age in 0..120 }) },
                    skipped = b.skipped,
                )
            },
            cadence = TripCadence.ofDays(r.tripDays),
        )
    }
}
