package app.necessityq.data

import app.necessityq.domain.Dish
import app.necessityq.domain.FamilyMember
import app.necessityq.domain.MealData
import app.necessityq.domain.MealPlan
import app.necessityq.domain.MealSlot
import app.necessityq.domain.PlannedMeal
import app.necessityq.domain.Taste
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON form of [MealData]; unknown/legacy values are skipped rather than failing. */
object MealJson {
    @Serializable private data class M(val id: String, val name: String)
    @Serializable private data class D(val id: String, val name: String, val ingredients: List<String> = emptyList())
    @Serializable private data class P(val day: Int, val slot: String, val dishId: String, val audience: List<String> = emptyList())
    @Serializable private data class Root(
        val members: List<M> = emptyList(),
        val dishes: List<D> = emptyList(),
        val tastes: Map<String, Map<String, String>> = emptyMap(),
        val cycleWeeks: Int = 1,
        val meals: List<P> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(data: MealData): String = json.encodeToString(
        Root.serializer(),
        Root(
            members = data.members.map { M(it.id, it.name) },
            dishes = data.dishes.map { D(it.id, it.name, it.ingredients) },
            tastes = data.tastes.mapValues { (_, m) -> m.mapValues { it.value.name } },
            cycleWeeks = data.plan.cycleWeeks,
            meals = data.plan.meals.map { P(it.day, it.slot.name, it.dishId, it.audience.toList()) },
        ),
    )

    fun decode(text: String?): MealData {
        if (text.isNullOrBlank()) return MealData()
        val r = runCatching { json.decodeFromString(Root.serializer(), text) }.getOrNull() ?: return MealData()
        return MealData(
            members = r.members.map { FamilyMember(it.id, it.name) },
            dishes = r.dishes.map { Dish(it.id, it.name, it.ingredients) },
            tastes = r.tastes.mapValues { (_, m) ->
                m.mapNotNull { (k, v) -> runCatching { Taste.valueOf(v) }.getOrNull()?.let { k to it } }.toMap()
            },
            plan = MealPlan(
                r.cycleWeeks.coerceIn(1, 2),
                r.meals.mapNotNull { p ->
                    runCatching { MealSlot.valueOf(p.slot) }.getOrNull()?.let { PlannedMeal(p.day, it, p.dishId, p.audience.toSet()) }
                },
            ),
        )
    }
}
