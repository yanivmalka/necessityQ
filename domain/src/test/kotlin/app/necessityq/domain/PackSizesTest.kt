package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What is bought is what the shop sells: the need rounds up to whole packs. */
class PackSizesTest {
    private fun liters(x: Double) = Quantity(x, QuantityUnit.LITER)
    private fun kilos(x: Double) = Quantity(x, QuantityUnit.KILOGRAM)
    private fun grams(x: Double) = Quantity(x, QuantityUnit.GRAM)
    private fun ml(x: Double) = Quantity(x, QuantityUnit.MILLILITER)

    @Test fun `a liter and a bit of milk is a liter and a half`() {
        assertEquals(liters(1.5), PackSizes.toBuy("חלב", liters(1.1)))
        assertEquals(liters(1.5), PackSizes.toBuy("חלב", ml(1100.0)))
        assertEquals(liters(1.0), PackSizes.toBuy("חלב", ml(240.0)))
        assertEquals(liters(1.0), PackSizes.toBuy("חלב", liters(1.0)))
    }

    @Test fun `milk past a liter and a half is the fewest packs that cover it`() {
        assertEquals(liters(2.0), PackSizes.toBuy("חלב", liters(1.6)))
        assertEquals(liters(2.5), PackSizes.toBuy("חלב", liters(2.1)))
        assertEquals(liters(3.0), PackSizes.toBuy("חלב", liters(2.6)))
    }

    @Test fun `flour only comes by the kilo`() {
        assertEquals(kilos(1.0), PackSizes.toBuy("קמח", kilos(0.5)))
        assertEquals(kilos(1.0), PackSizes.toBuy("קמח", grams(260.0)))
        assertEquals(kilos(2.0), PackSizes.toBuy("קמח", kilos(1.5)))
        assertEquals(kilos(2.0), PackSizes.toBuy("קמח", kilos(1.1)))
        assertEquals(kilos(1.0), PackSizes.toBuy("קמח", kilos(1.0)))
    }

    @Test fun `names that start with the packaged item count`() {
        assertEquals(kilos(1.0), PackSizes.toBuy("קמח מלא", grams(300.0)))
        assertEquals(kilos(1.0), PackSizes.toBuy("אורז בסמטי", grams(600.0)))
        assertEquals(grams(500.0), PackSizes.toBuy("אורז בסמטי", grams(300.0)))
    }

    @Test fun `eggs by the half dozen, dozen and thirty`() {
        assertEquals(Quantity.pieces(6), PackSizes.toBuy("ביצים", Quantity.pieces(4)))
        assertEquals(Quantity.pieces(12), PackSizes.toBuy("ביצים", Quantity.pieces(8)))
        assertEquals(Quantity.pieces(18), PackSizes.toBuy("ביצים", Quantity.pieces(14)))
        assertEquals(Quantity.pieces(30), PackSizes.toBuy("ביצים", Quantity.pieces(25)))
    }

    @Test fun `cream by the carton`() {
        assertEquals(ml(500.0), PackSizes.toBuy("שמנת מתוקה", ml(300.0)))
        assertEquals(ml(250.0), PackSizes.toBuy("שמנת מתוקה", ml(250.0)))
    }

    @Test fun `loose goods and unknown items are not rounded`() {
        assertEquals(kilos(1.5), PackSizes.toBuy("עגבניות", kilos(1.5)))
        assertEquals(grams(300.0), PackSizes.toBuy("גבינה צהובה", grams(300.0)))
        assertEquals(grams(230.0), PackSizes.toBuy("משהו שאין עליו מידע", grams(230.0)))
    }

    @Test fun `an amount in another dimension than the packs is left alone`() {
        assertEquals(Quantity.pieces(2), PackSizes.toBuy("חלב", Quantity.pieces(2)))
        assertEquals(null, PackSizes.toBuy("חלב", null))
    }

    @Test fun `a very large need still ends in whole packs`() {
        assertEquals(kilos(50.0), PackSizes.toBuy("קמח", kilos(49.2)))
        assertEquals(kilos(1000.0), PackSizes.toBuy("קמח", kilos(999.5)))
    }

    @Test fun `which items are packaged`() {
        assertTrue(PackSizes.isPackaged("חלב"))
        assertFalse(PackSizes.isPackaged("עגבניות"))
    }

    @Test fun `a line from a recipe shows what to buy, a typed line shows what was typed`() {
        fun line(source: LineSource, parts: List<RecipePart> = emptyList()) = ListLine(
            "1", "קמח", "קמח", null, Category.PANTRY, kilos(0.5), null, LineStatus.NEEDED, source, "u", 0, 0, recipeParts = parts,
        )
        assertEquals(kilos(1.0), line(LineSource.RECIPE).toBuy())
        assertEquals(kilos(1.0), line(LineSource.MENU).toBuy())
        assertEquals(kilos(0.5), line(LineSource.MANUAL).toBuy())
    }

    @Test fun `every catalogue item is either given pack sizes or left as it is on purpose`() {
        val undecided = Catalog.items.map { HebrewText.matchKey(it.name) }
            .filter { it !in PackSizes.packagedNames && it !in PackSizes.notPacked }
        assertEquals("catalogue items with no decision in PackSizes", emptyList<String>(), undecided)
        val both = Catalog.items.map { HebrewText.matchKey(it.name) }.filter { it in PackSizes.packagedNames && it in PackSizes.notPacked }
        assertEquals(emptyList<String>(), both)
    }

    @Test fun `aliases reach the rule of their catalogue item`() {
        assertEquals(liters(1.0), PackSizes.toBuy("שמן חמניות", ml(300.0)))
        assertEquals(grams(500.0), PackSizes.toBuy("ספגטי", grams(350.0)))
        assertEquals(grams(500.0), PackSizes.toBuy("קוטג", grams(300.0)))
    }

    @Test fun `dairy`() {
        assertEquals(grams(250.0), PackSizes.toBuy("קוטג'", grams(200.0)))
        assertEquals(grams(500.0), PackSizes.toBuy("קוטג'", grams(300.0)))
        assertEquals(grams(200.0), PackSizes.toBuy("גבינת שמנת", grams(150.0)))
        assertEquals(grams(400.0), PackSizes.toBuy("גבינת שמנת", grams(250.0)))
        assertEquals(grams(400.0), PackSizes.toBuy("מוצרלה", grams(300.0)))
        assertEquals(grams(400.0), PackSizes.toBuy("יוגורט", grams(245.0)))
        assertEquals(grams(200.0), PackSizes.toBuy("חמאה", grams(125.0)))
        assertEquals(grams(400.0), PackSizes.toBuy("חמאה", grams(250.0)))
    }

    @Test fun `pantry`() {
        assertEquals(grams(140.0), PackSizes.toBuy("רסק עגבניות", grams(50.0)))
        assertEquals(grams(420.0), PackSizes.toBuy("רסק עגבניות", grams(300.0))) // three small cans cover it with less left over than one big one
        assertEquals(grams(500.0), PackSizes.toBuy("רסק עגבניות", grams(450.0)))
        assertEquals(grams(280.0), PackSizes.toBuy("רסק עגבניות", grams(200.0)))
        assertEquals(grams(250.0), PackSizes.toBuy("דבש", grams(100.0)))
        assertEquals(grams(100.0), PackSizes.toBuy("שוקולד", grams(90.0)))
        assertEquals(grams(200.0), PackSizes.toBuy("שוקולד", grams(150.0)))
        assertEquals(kilos(1.0), PackSizes.toBuy("טחינה", grams(600.0)))
        assertEquals(grams(200.0), PackSizes.toBuy("קפה", grams(150.0)))
    }

    @Test fun `drinks and frozen`() {
        assertEquals(liters(1.5), PackSizes.toBuy("מים מינרליים", liters(1.2)))
        assertEquals(ml(750.0), PackSizes.toBuy("יין", ml(500.0)))
        assertEquals(grams(800.0), PackSizes.toBuy("ירקות קפואים", grams(500.0)))
        assertEquals(liters(1.0), PackSizes.toBuy("גלידה", ml(600.0)))
    }

    @Test fun `hummus, tea, tortillas and pitas`() {
        assertEquals(grams(250.0), PackSizes.toBuy("חומוס", grams(200.0)))
        assertEquals(grams(400.0), PackSizes.toBuy("חומוס", grams(300.0)))
        assertEquals(grams(500.0), PackSizes.toBuy("חומוס", grams(450.0)))
        assertEquals(Quantity.pieces(25), PackSizes.toBuy("תה", Quantity.pieces(10)))
        assertEquals(Quantity.pieces(50), PackSizes.toBuy("תה", Quantity.pieces(30)))
        assertEquals(Quantity.pieces(6), PackSizes.toBuy("טורטיות", Quantity.pieces(4)))
        assertEquals(Quantity.pieces(8), PackSizes.toBuy("טורטיות", Quantity.pieces(7)))
        assertEquals(Quantity.pieces(10), PackSizes.toBuy("טורטיות", Quantity.pieces(9)))
        assertEquals(Quantity.pieces(6), PackSizes.toBuy("פיתות", Quantity.pieces(4)))
        assertEquals(Quantity.pieces(10), PackSizes.toBuy("פיתה", Quantity.pieces(8)))
        assertEquals(Quantity.pieces(12), PackSizes.toBuy("פיתות", Quantity.pieces(11)))
    }
}
