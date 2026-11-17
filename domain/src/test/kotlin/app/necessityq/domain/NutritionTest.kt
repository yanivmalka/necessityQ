package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionTest {
    private val tomato = NutritionFood(
        1001, "עגבנייה, טרייה", kcal = 18.0, protein = 0.9,
        measures = listOf(FoodMeasure("יחידה קטנה", 90.0), FoodMeasure("יחידה בינונית", 123.0), FoodMeasure("כוס", 180.0)),
    )
    private val tomatoPaste = NutritionFood(1002, "עגבנייה, רסק, משומר", kcal = 82.0)
    private val egg = NutritionFood(3003, "ביצה, שלמה, טרייה", kcal = 143.0, measures = listOf(FoodMeasure("יחידה", 55.0)))
    private val oil = NutritionFood(4004, "שמן זית, כתית", kcal = 884.0, measures = listOf(FoodMeasure("כוס", 216.0)))
    private val shakshuka = NutritionFood(
        2002, "שקשוקה", kcal = 95.0,
        parts = listOf(
            FoodPart(1001, "עגבנייה, טרייה", 55.0),
            FoodPart(3003, "ביצה, שלמה", 30.0),
            FoodPart(9, "מים", 10.0),
            FoodPart(4004, "שמן זית, כתית", 4.5),
            FoodPart(8, "פלפל שחור", 0.2),
        ),
    )
    private val index = NutritionIndex(listOf(tomato, tomatoPaste, egg, oil, shakshuka))

    @Test fun `singular and plural forms share a stem`() {
        assertEquals(HebrewStem.stem("עגבנייה"), HebrewStem.stem("עגבניות"))
        assertEquals(HebrewStem.stem("ביצה"), HebrewStem.stem("ביצים"))
        assertEquals("זית", HebrewStem.stem("זית"))
    }

    @Test fun `search finds by every word and ranks the plain food first`() {
        assertEquals(listOf(1001, 1002), index.search("עגבניות").map { it.code })
        assertEquals(listOf(1002), index.search("רסק עגבניות").map { it.code })
        assertEquals(listOf(2002), index.search("שקשוקה", compositeOnly = true).map { it.code })
        assertTrue(index.search("  ").isEmpty())
    }

    @Test fun `a shopping item matches the fresh food`() {
        assertEquals(1001, index.match("עגבניות")?.code)
        assertEquals(3003, index.match("ביצים")?.code)
        assertNull(index.match("קוטג' 5%"))
    }

    @Test fun `household amounts convert to grams`() {
        assertEquals(246.0, index.grams("עגבניות", Quantity.pieces(2))!!, 0.01)
        assertEquals(500.0, index.grams("עגבניות", Quantity(0.5, QuantityUnit.KILOGRAM))!!, 0.01)
        assertEquals(90.0, index.grams("שמן זית", Quantity(100.0, QuantityUnit.MILLILITER))!!, 0.01)
        assertEquals(100.0, index.grams("קוטג'", Quantity(100.0, QuantityUnit.MILLILITER))!!, 0.01)
        assertNull(index.grams("קוטג'", Quantity.pieces(1)))
    }

    @Test fun `a composite food becomes a dish without water and traces`() {
        val dish = index.dishFrom(shakshuka, "d1")
        assertEquals("שקשוקה", dish.name)
        assertEquals(listOf("עגבנייה", "ביצה", "שמן זית"), dish.ingredients)
        assertEquals(2002, dish.nutritionCode)
        assertEquals(listOf("עגבנייה"), index.dishFrom(tomato, "d2").ingredients)
    }

    @Test fun `summary shows the main values per 100 g`() {
        assertEquals("18 קק\"ל · חלבון 0.9 גרם ל-100 גרם", tomato.summary())
        assertEquals("", NutritionFood(1, "x").summary())
    }
}
