package app.necessityq.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityNormsTest {
    private fun level(name: String, category: Category, q: Quantity, usual: Quantity? = null) =
        QuantityNorms.check(name, category, q, usual)!!.level

    @Test fun `each item has its own sense of a normal amount`() {
        assertEquals(QuantityLevel.USUAL, level("מלפפונים", Category.PRODUCE, Quantity.pieces(30)))
        assertEquals(QuantityLevel.LARGE, level("גבינה צהובה", Category.DAIRY, Quantity.pieces(10)))
        assertEquals(QuantityLevel.USUAL, level("ביצים", Category.DAIRY, Quantity.pieces(30)))
        assertEquals(QuantityLevel.USUAL, level("פיתות", Category.BAKERY, Quantity.pieces(20)))
        assertEquals(QuantityLevel.LARGE, level("לחם", Category.BAKERY, Quantity.pieces(8)))
        assertEquals(QuantityLevel.ABSURD, level("גבינה לבנה", Category.DAIRY, Quantity.pieces(750)))
    }

    @Test fun `unknown names use a keyword, then the category`() {
        // "גבינות" is not in the catalogue, but it is cheese
        assertEquals(QuantityLevel.LARGE, level("גבינות", Category.DAIRY, Quantity.pieces(10)))
        assertEquals(QuantityLevel.USUAL, level("שזיפים", Category.PRODUCE, Quantity.pieces(20)))
        assertEquals(QuantityLevel.LARGE, level("שזיפים", Category.PRODUCE, Quantity.pieces(40)))
    }

    @Test fun `weight and volume are judged in their own units`() {
        assertEquals(QuantityLevel.USUAL, level("גבינה צהובה", Category.DAIRY, Quantity(750.0, QuantityUnit.GRAM)))
        assertEquals(QuantityLevel.LARGE, level("עגבניות", Category.PRODUCE, Quantity(8.0, QuantityUnit.KILOGRAM)))
        assertEquals(QuantityLevel.USUAL, level("חלב", Category.DAIRY, Quantity(6.0, QuantityUnit.LITER)))
        val check = QuantityNorms.check("עגבניות", Category.PRODUCE, Quantity(8.0, QuantityUnit.KILOGRAM))!!
        assertEquals(Quantity(4.0, QuantityUnit.KILOGRAM), check.usualUpTo)
    }

    @Test fun `a household that always buys more is not warned about its routine`() {
        assertEquals(QuantityLevel.LARGE, level("גבינה צהובה", Category.DAIRY, Quantity.pieces(5)))
        assertEquals(QuantityLevel.USUAL, level("גבינה צהובה", Category.DAIRY, Quantity.pieces(5), usual = Quantity.pieces(4)))
    }

    @Test fun `a confirmed amount is no longer flagged, a larger one again is`() {
        val line = ListLine(
            id = "l", itemId = "c", name = "גבינה צהובה", variant = null, category = Category.DAIRY,
            quantity = Quantity.pieces(10), note = null, status = LineStatus.NEEDED, source = LineSource.AUTO,
            addedBy = "u", addedAtMillis = 1, updatedAtMillis = 1,
        )
        assertTrue(ItemTags.of(line, null, false).any { it.kind == TagKind.LARGE_AMOUNT })
        val approved = line.copy(approvedQuantity = Quantity.pieces(10))
        assertNull(QuantityNorms.warningFor(approved, null))
        assertTrue(ItemTags.of(approved, null, false).none { it.kind == TagKind.LARGE_AMOUNT })
        assertTrue(QuantityNorms.warningFor(approved.copy(quantity = Quantity.pieces(12)), null) != null)
    }

    @Test fun `only a slip is cut down when adding, a large amount is kept and flagged`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        var n = 0
        val service = ShoppingService(repo, object : Clock { override fun nowMillis() = 1L }, object : IdGenerator { override fun newId() = "id${++n}" })
        service.addFromText("h", "u", "10 גבינה צהובה", emptyList())
        service.addFromText("h", "u", "30 מלפפונים", emptyList())
        service.addFromText("h", "u", "750 גבינה לבנה", emptyList())
        val lines = service.snapshot("h").first().lines.associateBy { it.name }
        assertEquals(Quantity.pieces(10), lines.getValue("גבינה צהובה").quantity)
        assertTrue(ItemTags.of(lines.getValue("גבינה צהובה"), null, false).any { it.kind == TagKind.LARGE_AMOUNT })
        assertTrue(ItemTags.of(lines.getValue("מלפפונים"), null, false).none { it.kind == TagKind.LARGE_AMOUNT })
        assertEquals(Quantity.pieces(1), lines.getValue("גבינה לבנה").quantity)

        service.changeQuantityApproved("h", lines.getValue("גבינה צהובה"), Quantity.pieces(10))
        val after = service.snapshot("h").first().lines.first { it.name == "גבינה צהובה" }
        assertTrue(ItemTags.of(after, null, false).none { it.kind == TagKind.LARGE_AMOUNT })
    }
}
