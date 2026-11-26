package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileLayoutTest {
    @Test
    fun `round-trips through its stored text`() {
        val layout = TileLayout(setOf(TileElement.PRICE, TileElement.QUANTITY, TileElement.CHAT))
        assertEquals(layout, TileLayout.decode(layout.encode()))
        assertEquals(TileLayout(emptySet()), TileLayout.decode(""))
        assertNull(TileLayout.decode(null))
    }

    @Test
    fun `unknown names from a newer version are ignored`() {
        assertEquals(TileLayout(setOf(TileElement.PRICE)), TileLayout.decode("PRICE,SOMETHING_NEW"))
    }

    @Test
    fun `toggle hides a shown part and shows a hidden one`() {
        val layout = TileLayout.ALL.toggle(TileElement.PRICE)
        assertFalse(TileElement.PRICE in layout)
        assertTrue(TileElement.PRICE in layout.toggle(TileElement.PRICE))
    }

    @Test
    fun `the default tile is simple - check, name, amount, price and the sign of a conversation`() {
        val expected = setOf(TileElement.CHECK, TileElement.QUANTITY, TileElement.PRICE, TileElement.CHAT)
        // nothing is checked off while making the list
        assertEquals(expected - TileElement.CHECK, TileLayout.defaultFor(ListMode.PLAN).shown)
        assertEquals(expected, TileLayout.defaultFor(ListMode.SHOP).shown)
        assertFalse(TileElement.LAST_BOUGHT in TileLayout.defaultFor(ListMode.PLAN))
        assertFalse(TileElement.STEPPER in TileLayout.defaultFor(ListMode.PLAN))
    }

    @Test
    fun `every badge belongs to a tile part`() {
        TagKind.entries.forEach { TileElement.forTag(it) }
        val layout = TileLayout(setOf(TileElement.SOURCE))
        assertTrue(layout.showsTag(TagKind.RECIPE))
        assertTrue(layout.showsTag(TagKind.AUTO))
        assertFalse(layout.showsTag(TagKind.ROUTINE))
        assertFalse(layout.showsTag(TagKind.MISSING))
    }
}
