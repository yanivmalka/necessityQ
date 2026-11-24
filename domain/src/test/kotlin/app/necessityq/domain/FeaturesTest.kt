package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {
    @Test
    fun `stored as the features that are off`() {
        assertEquals("", Features.ALL.encodeOff())
        assertEquals(Features.ALL, Features.decodeOff(null))
        assertEquals(Features.SIMPLE, Features.decodeOff(Features.SIMPLE.encodeOff()))
        val noChat = Features.ALL.with(Feature.CHAT, false)
        assertEquals(noChat, Features.decodeOff(noChat.encodeOff()))
        // a name from a newer version is ignored
        assertEquals(noChat, Features.decodeOff("CHAT,SOMETHING_NEW"))
    }

    @Test
    fun `features that are off leave the tile`() {
        val tile = TileLayout.ALL
        assertEquals(tile, Features.ALL.tile(tile))
        val simple = Features.SIMPLE.tile(tile)
        assertFalse(TileElement.PRICE in simple)
        assertFalse(TileElement.CHAT in simple)
        assertFalse(TileElement.DRAG in simple)
        assertFalse(TileElement.LAST_BOUGHT in simple)
        assertTrue(TileElement.CHECK in simple)
        assertTrue(TileElement.QUANTITY in simple)
    }
}
