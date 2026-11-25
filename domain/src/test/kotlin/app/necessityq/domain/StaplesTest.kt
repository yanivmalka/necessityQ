package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaplesTest {
    private fun item(text: String) = RecipeLine.parse(text)!!.item
    private fun home(text: String, q: Quantity? = null) = Staples.assumedAtHome(item(text), q)

    @Test fun `salt, pepper, water and spices are always at home`() {
        assertTrue(home("מלח"))
        assertTrue(home("מלח ופלפל"))
        assertTrue(home("מים"))
        assertTrue(home("פפריקה מתוקה"))
        assertTrue(home("אבקת אפיה"))
        assertTrue(home("פלפל שחור"))
    }

    @Test fun `a vegetable called pepper is shopping, and so is mineral water`() {
        assertFalse(home("פלפל"))
        assertFalse(home("מים מינרליים"))
    }

    @Test fun `a little sugar, flour or oil is at home, a baking day is not`() {
        assertTrue(home("סוכר", Quantity(8.0, QuantityUnit.GRAM)))
        assertTrue(home("שמן זית", Quantity(45.0, QuantityUnit.MILLILITER)))
        assertTrue(home("שמן"))
        assertFalse(home("קמח", Quantity(500.0, QuantityUnit.GRAM)))
        assertFalse(home("סוכר", Quantity(400.0, QuantityUnit.GRAM)))
    }

    @Test fun `other food is shopping`() {
        assertFalse(home("חלב", Quantity(240.0, QuantityUnit.MILLILITER)))
        assertFalse(home("ביצים", Quantity.pieces(4)))
    }

    @Test fun `the household can say it needs a staple, or that it has an ordinary item`() {
        val salt = item("מלח")
        val milk = item("חלב")
        var data = MealData()
        assertTrue(data.isAtHome(salt, null))
        assertFalse(data.isAtHome(milk, null))
        data = data.withPantry(salt.id, atHome = false)
        assertFalse(data.isAtHome(salt, null))
        data = data.withPantry(milk.id, atHome = true)
        assertTrue(data.isAtHome(milk, null))
        data = data.withPantry(salt.id, atHome = true)
        assertTrue(data.isAtHome(salt, null))
        assertEquals(setOf(salt.id, milk.id), data.pantry)
        assertTrue(data.buy.isEmpty())
    }

    @Test fun `a recipe leaves its staples off the list`() {
        val cake = Dish("c", "עוגה", listOf("3 כוסות קמח", "1 כוס סוכר", "1 כוס חלב", "מלח", "2 כפיות אבקת אפיה", "3 ביצים"), servings = 8)
        val data = MealData(dishes = listOf(cake))
        val rows = RecipeListPreview.of(emptyList(), cake, 8, data.pantry, data.buy)
        val atHome = rows.filter { it.kind == RecipeListPreview.Kind.AT_HOME }.map { it.item.name }
        assertEquals(listOf("מלח", "אבקת אפיה"), atHome)
        val buy = rows.filter { it.kind != RecipeListPreview.Kind.AT_HOME }.map { it.item.name }
        assertEquals(listOf("קמח", "סוכר", "חלב", "ביצים"), buy)
    }
}
