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

    @Test fun `menu ranking respects never, dislikes and audience`() {
        val d1 = Dish("a", "שקשוקה", listOf("ביצים"))
        val d2 = Dish("b", "פסטה", listOf("פסטה"))
        val d3 = Dish("c", "פסטה פטריות", listOf("פסטה", "פטריות"))
        val data = MealData(
            members = listOf(FamilyMember("x", "אבא"), FamilyMember("y", "ילד", dislikes = listOf("פטרייה"))),
            dishes = listOf(d1, d2, d3),
            ratings = mapOf("x" to mapOf("a" to 5), "y" to mapOf("a" to Rating.NEVER, "b" to 4)),
        )
        assertEquals(listOf("b"), MealPlanner.rank(data, emptySet()).map { it.first.id })
        assertEquals("a", MealPlanner.rank(data, setOf("x")).first().first.id)
        assertEquals("a", MealPlanner.fromBought(data, setOf(ItemIdentity.fromText("ביצים").id)).first().first.id)
    }

    @Test fun `cycle on real dates with one-off changes and cooked marks`() {
        val data = MealData(
            dishes = listOf(Dish("a", "שקשוקה", listOf("4 ביצים"), servings = 2), Dish("b", "פסטה", listOf("500 גרם פסטה"))),
            plan = MealPlan(2, listOf(PlannedMeal(0, MealSlot.DINNER, "a"), PlannedMeal(8, MealSlot.DINNER, "b"))),
        )
        val sunday = java.time.LocalDate.of(2026, 11, 15)
        val anchored = data.copy(plan = MealPlanner.anchorThisWeek(data.plan, sunday.plusDays(3)))
        assertEquals(sunday.toEpochDay(), anchored.plan.anchorEpochDay)
        assertEquals("a", MealPlanner.mealsOn(anchored, sunday.toEpochDay()).single().dishId)
        assertEquals("b", MealPlanner.mealsOn(anchored, sunday.plusDays(8).toEpochDay()).single().dishId)
        assertEquals("a", MealPlanner.mealsOn(anchored, sunday.plusDays(14).toEpochDay()).single().dishId)

        // only this date: skip the cycle meal
        val skipped = MealPlanner.setMeal(anchored, sunday.toEpochDay(), MealSlot.DINNER, null, emptySet(), null, onlyThisDate = true)
        assertTrue(MealPlanner.mealsOn(skipped, sunday.toEpochDay()).isEmpty())
        assertEquals("a", MealPlanner.mealsOn(skipped, sunday.plusDays(14).toEpochDay()).single().dishId)

        // 4 servings of a 2-serving recipe doubles the eggs; cooked meals are not shopped for
        val forFour = MealPlanner.setMeal(anchored, sunday.toEpochDay(), MealSlot.DINNER, "a", emptySet(), 4, onlyThisDate = true)
        val need = MealPlanner.ingredientsFor(forFour, sunday.toEpochDay(), 7)
        assertEquals(8.0, need.single().first.quantity!!.amount, 1e-9)
        val cooked = MealPlanner.setCooked(forFour, sunday.toEpochDay(), MealSlot.DINNER, true)
        assertTrue(MealPlanner.ingredientsFor(cooked, sunday.toEpochDay(), 7).isEmpty())
        assertEquals(1, MealPlanner.recentlyCooked(cooked, sunday.plusDays(2)).size)
    }

    @Test fun `recipe scaling and list sync`() {
        val dish = Dish("a", "שקשוקה", listOf("4 ביצים", "500 גרם עגבניות", "בצל"), servings = 4)
        val six = Recipes.scaled(dish, 6).associate { it.item.id to it.quantity }
        fun id(text: String) = ItemIdentity.fromText(text).id
        assertEquals(6.0, six.getValue(id("ביצים"))!!.amount, 1e-9)
        assertEquals(750.0, six.getValue(id("עגבניות"))!!.baseAmount, 1e-9)
        assertEquals(null, six.getValue(id("בצל")))
        assertEquals(2.0, Recipes.scale(Quantity.pieces(3), 0.5)!!.amount, 1e-9)

        // eggs were merged with 2 eggs already needed: 4 → 6 servings adds 2 more, keeps the other 2
        val eggs = ItemIdentity.fromText("ביצים")
        val lines = listOf(
            ListLine("l1", eggs.id, eggs.name, eggs.variant, eggs.category, Quantity.pieces(6), null, LineStatus.NEEDED, LineSource.MANUAL, "u", 0, 0),
        )
        val changes = RecipeListSync.plan(lines, dish, 4, 6)
        val update = changes.filterIsInstance<RecipeListSync.Change.Update>().single()
        assertEquals("l1", update.lineId)
        assertEquals(8.0, update.quantity!!.amount, 1e-9)
        // tomatoes and onion are not on the list → added again
        assertEquals(2, changes.filterIsInstance<RecipeListSync.Change.Add>().size)
    }

    @Test fun `member age label`() {
        val today = java.time.LocalDate.of(2026, 11, 17)
        assertEquals("8 שנים", FamilyMember("k", "נועם", birthYear = 2018, birthMonth = 3).ageLabel(today))
        assertEquals("5 חודשים", FamilyMember("b", "תינוק", birthYear = 2026, birthMonth = 6).ageLabel(today))
        assertEquals(null, FamilyMember("p", "אבא").ageLabel(today))
    }

    @Test fun `adding a recipe ingredient already on the list adds to it and records the dish`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val s = ShoppingService(repo, clock, Ids())
        s.addFromText("h", "u", "3 תפוחי אדמה", emptyList())
        val potatoes = ItemIdentity.fromText("תפוחי אדמה")
        s.add("h", "u", potatoes, Quantity.pieces(2), LineSource.RECIPE, s.snapshot("h").first().lines, recipe = "פירה")
        val line = s.snapshot("h").first().lines.single()
        assertEquals(5.0, line.quantity!!.amount, 1e-9)
        assertEquals(listOf(RecipePart("פירה", Quantity.pieces(2))), line.recipeParts)
        assertTrue(line.isFromRecipe && line.isManual)
        assertTrue(ItemTags.of(line, null, false).any { it.label == "מתכון: פירה · 2 יח'" })
    }

    @Test fun `produce priced per kilo is sold by weight even without the flag`() {
        fun p(code: String, unit: String, internal: Boolean) = BranchProduct(code, "תפוחי אדמה", "", 1.0, unit, false, internal, 4.9, null, "", 4.9, 4.9)
        assertTrue(p("7290000000001", "ק\"ג", internal = true).soldByWeight)
        assertTrue(p("4011", "קילוגרם", internal = false).soldByWeight)
        assertTrue(!p("7290000000002", "ק\"ג", internal = false).soldByWeight)
        assertTrue(!p("4011", "יחידה", internal = false).soldByWeight)
    }
}
