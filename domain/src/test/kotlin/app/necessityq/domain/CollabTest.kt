package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollabTest {
    private fun line(id: String, name: String, category: Category = Category.DAIRY, added: Long, position: Long = 0) = ListLine(
        id = id, itemId = id, name = name, variant = null, category = category, quantity = null, note = null,
        status = LineStatus.NEEDED, source = LineSource.MANUAL, addedBy = "u", addedAtMillis = added,
        updatedAtMillis = added, position = position,
    )

    @Test fun `alphabetical sort uses one group in hebrew order`() {
        val lines = listOf(line("1", "קוטג'", added = 1), line("2", "אורז", Category.PANTRY, added = 2), line("3", "חלב", added = 3))
        val arranged = ListOrdering.arrange(lines, ListSort.ALPHA)
        assertEquals(1, arranged.size)
        assertNull(arranged[0].first)
        assertEquals(listOf("אורז", "חלב", "קוטג'"), arranged[0].second.map { it.name })
    }

    @Test fun `added sort keeps the order products were added`() {
        val lines = listOf(line("1", "ב", added = 30), line("2", "א", added = 10), line("3", "ג", added = 20))
        assertEquals(listOf("2", "3", "1"), ListOrdering.arrange(lines, ListSort.ADDED)[0].second.map { it.id })
    }

    @Test fun `groups sort follows manual position but untouched lines keep add order`() {
        val lines = listOf(line("a", "א", added = 10), line("b", "ב", added = 20), line("c", "ג", added = 30, position = 15))
        assertEquals(listOf("a", "c", "b"), ListOrdering.arrange(lines, ListSort.GROUPS)[0].second.map { it.id })
    }

    @Test fun `position between neighbours`() {
        assertEquals(15L, ListOrdering.positionBetween(10, 20))
        assertTrue(ListOrdering.positionBetween(null, 20) < 20)
        assertTrue(ListOrdering.positionBetween(10, null) > 10)
    }

    @Test fun `quantity steps by unit and never drops below one step`() {
        assertEquals(Quantity.pieces(1), QuantitySteps.step(null, +1))
        assertNull(QuantitySteps.step(null, -1))
        assertEquals(Quantity.pieces(3), QuantitySteps.step(Quantity.pieces(2), +1))
        assertEquals(Quantity.pieces(1), QuantitySteps.step(Quantity.pieces(1), -1))
        assertEquals(Quantity(600.0, QuantityUnit.GRAM), QuantitySteps.step(Quantity(500.0, QuantityUnit.GRAM), +1))
        assertEquals(Quantity(1.0, QuantityUnit.KILOGRAM), QuantitySteps.step(Quantity(1.5, QuantityUnit.KILOGRAM), -1))
    }

    @Test fun `nearest store within range`() {
        val a = Store("a", "סופר א", StoreType.SUPERMARKET, 31.7683, 35.2137)
        val b = Store("b", "מכולת ב", StoreType.GROCERY, 31.7800, 35.2200)
        assertEquals("a", Stores.nearest(listOf(a, b), 31.7684, 35.2138)?.id)
        assertNull(Stores.nearest(listOf(a, b), 32.0, 35.0))
    }
}
