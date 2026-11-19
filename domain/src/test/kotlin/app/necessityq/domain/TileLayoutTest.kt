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
    fun `each stage has its own default`() {
        val plan = TileLayout.defaultFor(ListMode.PLAN)
        val shop = TileLayout.defaultFor(ListMode.SHOP)
        assertTrue(TileElement.LAST_BOUGHT in plan)
        assertFalse(TileElement.LAST_BOUGHT in shop)
        assertFalse(TileElement.ADDED_BY in plan)
        assertTrue(TileElement.PRICE in shop)
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
