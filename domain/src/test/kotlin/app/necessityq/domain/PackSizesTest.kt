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

    // ---------- pieces and packs (eggs: 30 eggs are one tray, not 30 trays) ----------

    private fun pieces(x: Double) = Quantity(x, QuantityUnit.PIECE)
    private fun packs(x: Double) = Quantity(x, QuantityUnit.PACKAGE)

    @Test fun `packs stay packs when rounded to what the shop sells`() {
        assertEquals(packs(1.0), PackSizes.nearest("פיתות", packs(1.0)))
        assertEquals(packs(2.0), PackSizes.toBuy("פיתות", packs(2.0)))
        assertEquals(packs(1.0), PackSizes.nearest("ביצים", packs(1.0)))
        // pieces still round to whole packs of pieces
        assertEquals(pieces(12.0), PackSizes.toBuy("ביצים", pieces(10.0)))
    }

    @Test fun `thirty eggs are one tray of thirty`() {
        assertEquals("מארז 30", PackSizes.packLabel("ביצים", null, pieces(30.0)))
        assertEquals("2 מארזים של 12", PackSizes.packLabel("ביצים", null, pieces(24.0)))
        assertEquals("30 יח' (מארז 30)", PackSizes.describe("ביצים", null, pieces(30.0)))
        assertEquals(null, PackSizes.packLabel("ביצים", null, pieces(7.0)))
        assertEquals(null, PackSizes.packLabel("חלב", null, Quantity(1.0, QuantityUnit.LITER)))
        assertEquals(30L, PackSizes.piecesPerPack("ביצים", null, 30.0))
        assertEquals(12L, PackSizes.piecesPerPack("ביצים", null, 10.0))
    }

    @Test fun `a model's thirty packs of eggs are thirty eggs`() {
        assertEquals(pieces(30.0), PackSizes.asPieces("ביצים", null, packs(30.0)))
        assertEquals(packs(2.0), PackSizes.asPieces("ביצים", null, packs(2.0)))
        assertEquals(packs(30.0), PackSizes.asPieces("חלב", null, packs(30.0)))
    }

    @Test fun `pieces of a product sold by the pack are priced per pack`() {
        fun egg(name: String, size: Double?, unit: String) =
            BranchProduct("1", name, "", size, unit, false, false, 25.0, null, "", 25.0, 25.0)
        assertEquals(30.0, egg("ביצים L", 30.0, "יחידה").piecesPerPack!!, 1e-9)
        assertEquals(12.0, egg("ביצים גדולות 12 יח'", null, "").piecesPerPack!!, 1e-9)
        assertEquals(6.0, egg("מים מינרליים 6X1.5 ליטר", null, "").piecesPerPack!!, 1e-9)
        assertEquals(null, egg("חלב 3% 1 ליטר", 1.0, "ליטר").piecesPerPack)
        assertEquals(1.0, ListCost.packages(pieces(30.0), weighted = false, piecesPerPack = 30.0)!!, 1e-9)
        assertEquals(2.0, ListCost.packages(pieces(24.0), weighted = false, piecesPerPack = 12.0)!!, 1e-9)
        assertEquals(1.0, ListCost.packages(pieces(6.0), weighted = false, piecesPerPack = 30.0)!!, 1e-9)
        // without a pack size the count is as before
        assertEquals(30.0, ListCost.packages(pieces(30.0), weighted = false)!!, 1e-9)

        val line = ListLine(
            id = "e", itemId = "ביצים", name = "ביצים", variant = null, category = Category.DAIRY, quantity = pieces(30.0), note = null,
            status = LineStatus.NEEDED, source = LineSource.MANUAL, addedBy = "u", addedAtMillis = 0, updatedAtMillis = 0,
        )
        val tray = egg("ביצים L", 30.0, "יחידה")
        val chosen = ListCost.of(listOf(line), emptyMap(), mapOf("ביצים" to ItemPriceInfo("b", 25.0, 25.0, listOf(tray), tray)))
        assertEquals(25.0, chosen.low, 1e-9)
        // no product chosen: the item's usual packs (a tray of 30)
        val range = ListCost.of(listOf(line), emptyMap(), mapOf("ביצים" to ItemPriceInfo("b", 25.0, 25.0, listOf(tray), null)))
        assertEquals(25.0, range.low, 1e-9)
    }

    @Test fun `thirty trays of eggs is a slip`() {
        assertTrue(QuantityNorms.check("ביצים", Category.DAIRY, packs(30.0))!!.isUnusual)
        assertFalse(QuantityNorms.check("ביצים", Category.DAIRY, pieces(30.0))!!.isUnusual)
    }
}
