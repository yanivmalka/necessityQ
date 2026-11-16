package app.necessityq.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealsAndBinTest {
    private class Ids : IdGenerator { var n = 0; override fun newId() = "id${++n}" }
    private val clock = object : Clock { override fun nowMillis() = 1_000_000L }

    @Test fun `deleted and cleared lines go to the bin and come back`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val s = ShoppingService(repo, clock, Ids())
        s.addFromText("h", "u", "חלב", emptyList())
        s.addFromText("h", "u", "לחם", s.snapshot("h").first().lines)
        val (milk, bread) = s.snapshot("h").first().lines.sortedBy { it.name }.let { it[1] to it[0] }
        s.delete("h", milk)
        s.checkOff("h", "u", bread)
        s.clearBought("h", s.snapshot("h").first().lines)
        val snap = s.snapshot("h").first()
        assertEquals(2, snap.removed.size)
        assertTrue(snap.grouped.isEmpty() && snap.bought.isEmpty())
        s.restore("h", snap.removed.first { it.name == "חלב" })
        assertEquals(1, s.snapshot("h").first().grouped.size)
        s.purge("h", s.snapshot("h").first().lines)
        assertEquals(0, s.snapshot("h").first().removed.size)
    }

    @Test fun `pick order learns collection sequence`() {
        fun p(item: String, t: Long) = Purchase(item + t, item, item, null, Category.OTHER, null, t, "u", null, "store")
        val trips = listOf(p("bread", 1), p("milk", 2), p("meat", 3), p("bread", 86_400_001), p("milk", 86_400_002))
        val ranks = PickOrder.ranks(trips, "store", java.time.ZoneOffset.UTC)
        assertTrue(ranks.getValue("bread") < ranks.getValue("milk"))
    }

    @Test fun `menu ranking respects never and audience`() {
        val d1 = Dish("a", "שקשוקה", listOf("ביצים"))
        val d2 = Dish("b", "פסטה", listOf("פסטה"))
        val data = MealData(
            members = listOf(FamilyMember("x", "אבא"), FamilyMember("y", "ילד")),
            dishes = listOf(d1, d2),
            tastes = mapOf("x" to mapOf("a" to Taste.LOVE), "y" to mapOf("a" to Taste.NEVER, "b" to Taste.LIKE)),
        )
        assertEquals(listOf("b"), MealPlanner.rank(data, emptySet()).map { it.first.id })
        assertEquals("a", MealPlanner.rank(data, setOf("x")).first().first.id)
        val plan = data.copy(plan = MealPlan(1, listOf(PlannedMeal(0, MealSlot.DINNER, "a"), PlannedMeal(1, MealSlot.DINNER, "b"))))
        assertEquals(listOf("ביצים", "פסטה"), MealPlanner.ingredientsForWeek(plan, 0).map { it.first })
        assertEquals("a", MealPlanner.fromBought(data, setOf(ItemIdentity.fromText("ביצים").id)).single().first.id)
    }
}
