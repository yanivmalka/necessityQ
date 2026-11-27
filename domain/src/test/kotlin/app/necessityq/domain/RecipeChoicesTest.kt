package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The first time a recipe goes on the list the household chooses kinds and pack sizes; the next time it is remembered. */
class RecipeChoicesTest {
    private val cake = Dish("c", "עוגה", listOf("2 כוסות קמח", "1 כוס חלב", "3 ביצים", "בצל"), servings = 4)
    private fun base(text: String) = RecipeLine.parse(text)!!.item

    @Test fun `only plain ingredients are asked about`() {
        assertEquals(listOf("לבן", "תופח", "מלא", "כוסמין"), ItemOptions.kindsFor(base("קמח")))
        assertEquals(listOf("3%", "1%", "ללא לקטוז"), ItemOptions.kindsFor(base("חלב")))
        // the recipe already says which
        assertTrue(ItemOptions.kindsFor(base("חלב 3%")).isEmpty())
        // nothing to choose
        assertTrue(ItemOptions.kindsFor(base("מלפפונים")).isEmpty())
    }

    @Test fun `every item with kinds is in the catalogue`() {
        val catalogue = Catalog.items.map { HebrewText.matchKey(it.name) }.toSet()
        assertEquals(emptySet<String>(), ItemOptions.names - catalogue)
    }

    @Test fun `a chosen kind becomes the item on the list, and the base keeps its id`() {
        val flour = base("קמח")
        val chosen = cake.copy(listMemory = ListMemory(variants = mapOf(flour.id to "תופח")))
        val ing = Recipes.scaled(chosen, 4).first { it.base.name == "קמח" }
        assertEquals("קמח תופח", ing.item.displayName)
        assertEquals(flour.id, ing.baseId)
        // before any choice the recipe's own item is used
        assertEquals("קמח", Recipes.scaled(cake, 4).first { it.base.name == "קמח" }.item.displayName)
    }

    @Test fun `a kind named in the recipe line is not replaced`() {
        val dish = Dish("d", "לחם", listOf("500 גרם קמח מלא"), servings = 4)
        val milk = base("חלב 3%")
        val ing = Recipes.scaled(dish.copy(listMemory = ListMemory(variants = mapOf(milk.id to "1%"))), 4).single()
        assertEquals("קמח מלא", ing.item.displayName)
    }

    @Test fun `what was left off last time stays off, ingredients added since follow the pantry`() {
        val flour = base("קמח")
        val eggs = base("ביצים")
        val memory = ListMemory(known = setOf(flour.id, eggs.id, base("חלב").id), skipped = setOf(eggs.id))
        val dish = cake.copy(listMemory = memory)
        val data = MealData(dishes = listOf(dish))
        val all = Recipes.scaled(dish, 4)
        val left = all.filter { data.leavesOut(dish, it) }.map { it.base.name }
        assertEquals(listOf("ביצים"), left)
        // an ingredient the recipe did not have then is judged as at home or not
        val withSalt = dish.copy(ingredients = dish.ingredients + "מלח")
        assertTrue(Recipes.scaled(withSalt, 4).filter { data.leavesOut(withSalt, it) }.any { it.base.name == "מלח" })
        // and with no memory at all it is just the pantry
        assertFalse(Recipes.scaled(cake, 4).any { data.leavesOut(cake, it) && it.base.name == "קמח" })
    }

    @Test fun `the menu follows the remembered choices`() {
        val flour = base("קמח")
        val dish = cake.copy(listMemory = ListMemory(variants = mapOf(flour.id to "מלא"), known = setOf(base("ביצים").id), skipped = setOf(base("ביצים").id)))
        val data = MealData(dishes = listOf(dish), plan = MealPlan(meals = listOf(PlannedMeal(0, MealSlot.DINNER, dish.id))))
        val names = MealPlanner.ingredientsFor(data, MealPlan.DEFAULT_ANCHOR, 7).map { it.first.item.displayName }
        assertTrue("קמח מלא" in names)
        assertFalse(names.any { it.startsWith("ביצים") })
    }

    @Test fun `the pack size the household buys decides how much to buy`() {
        // milk comes by the liter, the liter and a half and two liters; this household only buys liters
        assertEquals(Quantity(1.5, QuantityUnit.LITER), PackSizes.toBuy("חלב", Quantity(1.1, QuantityUnit.LITER)))
        assertEquals(Quantity(2.0, QuantityUnit.LITER), PackSizes.toBuy("חלב", Quantity(1.1, QuantityUnit.LITER), preferred = 1000))
        // a size the item is not sold in is ignored
        assertEquals(Quantity(1.5, QuantityUnit.LITER), PackSizes.toBuy("חלב", Quantity(1.1, QuantityUnit.LITER), preferred = 777))
        assertEquals(listOf(1.0, 1.5, 2.0), PackSizes.sizesFor("חלב").map { it.amount })
        assertEquals("חלב", PackSizes.keyFor("חלב", "3%"))
        assertEquals("סוכר חום", PackSizes.keyFor("סוכר", "חום"))
    }

    @Test fun `a line shows its pack size choice`() {
        val line = ListLine(
            "1", "חלב__3pct", "חלב", "3%", Category.DAIRY, Quantity(1.1, QuantityUnit.LITER), null, LineStatus.NEEDED, LineSource.RECIPE, "u", 0, 0,
        )
        assertEquals(Quantity(1.5, QuantityUnit.LITER), line.toBuy())
        assertEquals(Quantity(2.0, QuantityUnit.LITER), line.toBuy(mapOf("חלב" to 1000L)))
    }

    @Test fun `brown sugar is bought by the half kilo`() {
        assertEquals(Quantity(500.0, QuantityUnit.GRAM), PackSizes.toBuy("סוכר", Quantity(300.0, QuantityUnit.GRAM), "חום"))
        assertEquals(Quantity(1.0, QuantityUnit.KILOGRAM), PackSizes.toBuy("סוכר", Quantity(300.0, QuantityUnit.GRAM), "לבן"))
    }

    @Test fun `choices and pack sizes are kept`() {
        val data = MealData().withPack("חלב", 1000L).withPack("קמח", 1000L)
        assertEquals(mapOf("חלב" to 1000L, "קמח" to 1000L), data.packs)
        assertEquals(mapOf("קמח" to 1000L), data.withPack("חלב", null).packs)
    }
}
