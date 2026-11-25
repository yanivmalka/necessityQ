package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ingredient lines of recipes: what to buy and how much, not the kitchen words around it. */
class RecipeLineTest {
    private fun parse(line: String) = RecipeLine.parse(line)!!
    private fun name(line: String) = parse(line).item.name
    private fun qty(line: String) = parse(line).quantity

    @Test fun `a cup of milk is milk, in milliliters`() {
        assertEquals("חלב", name("1 כוס חלב"))
        assertEquals(Quantity(240.0, QuantityUnit.MILLILITER), qty("1 כוס חלב"))
        assertEquals("חלב", name("כוס חלב"))
        assertEquals(Quantity(240.0, QuantityUnit.MILLILITER), qty("כוס חלב"))
    }

    @Test fun `spoons of sugar are sugar in grams, not pieces`() {
        assertEquals("סוכר", name("2 כפיות סוכר"))
        assertEquals(Quantity(8.0, QuantityUnit.GRAM), qty("2 כפיות סוכר"))
        assertEquals("סוכר חום", name("2 כפות סוכר חום"))
        assertEquals(Quantity(28.0, QuantityUnit.GRAM), qty("2 כפות סוכר חום"))
    }

    @Test fun `a cup of flour is grams and a cup of oil is milliliters`() {
        assertEquals(Quantity(260.0, QuantityUnit.GRAM), qty("2 כוסות קמח"))
        assertEquals(Quantity(65.0, QuantityUnit.GRAM), qty("½ כוס קמח"))
        assertEquals(Quantity(65.0, QuantityUnit.GRAM), qty("1/2 כוס קמח"))
        assertEquals(Quantity(45.0, QuantityUnit.MILLILITER), qty("3 כפות שמן זית"))
        assertEquals("שמן זית", name("3 כפות שמן זית"))
    }

    @Test fun `fractions in words and numbers`() {
        assertEquals(Quantity(285.0, QuantityUnit.GRAM), qty("1 וחצי כוסות אורז"))
        assertEquals("אורז", name("1 וחצי כוסות אורז"))
        assertEquals(Quantity(65.0, QuantityUnit.GRAM), qty("חצי כוס קמח"))
        assertEquals(Quantity(2.5, QuantityUnit.PIECE), qty("2 1/2 בצלים")?.let { Quantity(it.amount, QuantityUnit.PIECE) })
    }

    @Test fun `unknown foods in spoons have no amount instead of a wrong one`() {
        assertNull(qty("כף רסק עגבניות"))
        assertEquals("רסק עגבניות", name("כף רסק עגבניות"))
        assertNull(qty("קורט מלח"))
        assertEquals("מלח", name("1 קורט מלח"))
        assertEquals("פטרוזיליה", name("חופן פטרוזיליה"))
    }

    @Test fun `preparation words are not part of the name`() {
        assertEquals("בצל", name("בצל גדול קצוץ דק"))
        assertEquals("בצל", name("2 בצלים בינוניים"))
        assertEquals(Quantity.pieces(2), qty("2 בצלים בינוניים"))
        assertEquals("ביצים", name("2 ביצים מוקצפות"))
        assertEquals("גבינה צהובה", name("200 גרם גבינה צהובה מגוררת"))
        assertEquals(Quantity(200.0, QuantityUnit.GRAM), qty("200 גרם גבינה צהובה מגוררת"))
        assertEquals("תפוחי אדמה", name("1 ק\"ג תפוחי אדמה, קלופים וחתוכים לקוביות"))
        assertEquals(Quantity(1.0, QuantityUnit.KILOGRAM), qty("1 ק\"ג תפוחי אדמה, קלופים וחתוכים לקוביות"))
        assertEquals(Category.PRODUCE, parse("1 ק\"ג תפוחי אדמה, קלופים").item.category)
    }

    @Test fun `how much to use is not part of the name`() {
        assertEquals("שמן", name("שמן לטיגון"))
        assertEquals("פטרוזיליה", name("לקישוט: פטרוזיליה"))
        assertEquals("מלח ופלפל", name("מלח ופלפל לפי הטעם"))
        assertEquals("מים", name("מעט מים"))
    }

    @Test fun `ranges take the larger amount`() {
        assertEquals(Quantity.pieces(4), qty("3-4 עגבניות"))
        assertEquals("עגבניות", name("3-4 עגבניות"))
    }

    @Test fun `garlic cloves become heads of garlic`() {
        assertEquals("שום", name("2 שיני שום כתושות"))
        assertEquals(Quantity.pieces(1), qty("2 שיני שום כתושות"))
        assertEquals(Quantity.pieces(2), qty("12 שיני שום"))
    }

    @Test fun `cans and packs keep their count and lose the container word`() {
        assertEquals("טונה", name("1 פחית טונה"))
        assertEquals(Quantity.pieces(1), qty("1 פחית טונה"))
        assertEquals("פסטה", name("1 חבילת פסטה (500 גרם)"))
        assertEquals(Quantity(1.0, QuantityUnit.PACKAGE), qty("1 חבילת פסטה (500 גרם)"))
        assertEquals("כוסברה", name("1 צרור כוסברה"))
    }

    @Test fun `english kitchen units`() {
        assertEquals("salt", name("1 tsp salt").lowercase())
        assertEquals("flour", name("2 cups flour").lowercase())
    }

    @Test fun `shopping units and plain lines still read as on the list`() {
        assertEquals("ביצים", name("4 ביצים"))
        assertEquals(Quantity.pieces(4), qty("4 ביצים"))
        assertEquals(Quantity(500.0, QuantityUnit.GRAM), qty("500 גרם בשר טחון"))
        assertEquals("בשר טחון", name("500 גרם בשר טחון"))
        assertEquals("גזר", name("גזר"))
        assertNull(qty("גזר"))
    }

    @Test fun `a blank or empty line has nothing to buy`() {
        assertNull(RecipeLine.parse(""))
        assertNull(RecipeLine.parse("לקישוט:"))
        assertNull(RecipeLine.parse("2 כוסות"))
    }
}
