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
        assertEquals(Features.SIMPLE, Features.decodeOff(Features.SIMPLE.encodeOff(), Features.SIMPLE.encodeShopOff()))
        val noChat = Features.ALL.with(Feature.CHAT, false)
        assertEquals(noChat, Features.decodeOff(noChat.encodeOff(), noChat.encodeShopOff()))
        // a name from a newer version is ignored
        assertEquals(noChat, Features.decodeOff("CHAT,SOMETHING_NEW"))
    }

    @Test
    fun `before the shop had switches of its own it follows the list`() {
        val f = Features.decodeOff("PRICES")
        assertFalse(f.isOn(Feature.PRICES, ListMode.PLAN))
        assertFalse(f.isOn(Feature.PRICES, ListMode.SHOP))
    }

    @Test
    fun `a feature of both stages is switched per stage`() {
        val f = Features.ALL.with(Feature.PRICES, ListMode.SHOP, false)
        assertTrue(f.isOn(Feature.PRICES, ListMode.PLAN))
        assertFalse(f.isOn(Feature.PRICES, ListMode.SHOP))
        assertTrue(Feature.PRICES in f)
        assertEquals(f, Features.decodeOff(f.encodeOff(), f.encodeShopOff()))
    }

    @Test
    fun `app-wide features change everywhere and list-only ones are off in the shop`() {
        val noChat = Features.ALL.with(Feature.CHAT, ListMode.SHOP, false)
        assertFalse(noChat.isOn(Feature.CHAT, ListMode.PLAN))
        assertFalse(Feature.CHAT in noChat)
        assertFalse(Features.ALL.isOn(Feature.RUNNING_LOW, ListMode.SHOP))
        assertTrue(Features.ALL.isOn(Feature.RUNNING_LOW, ListMode.PLAN))
        assertTrue(Feature.of(ListMode.SHOP).none { it.scope == FeatureScope.PLAN || it.scope == FeatureScope.APP })
        assertTrue(Feature.RUNNING_LOW in Feature.of(ListMode.PLAN))
    }

    @Test
    fun `every feature explains itself`() {
        Feature.entries.forEach { assertTrue(it.name, it.description.length > 20) }
    }

    @Test
    fun `features that are off leave the tile`() {
        val tile = TileLayout.ALL
        assertEquals(tile, Features.ALL.tile(tile, ListMode.SHOP))
        val simple = Features.SIMPLE.tile(tile, ListMode.SHOP)
        assertFalse(TileElement.PRICE in simple)
        assertFalse(TileElement.CHAT in simple)
        assertFalse(TileElement.DRAG in simple)
        assertFalse(TileElement.LAST_BOUGHT in simple)
        assertTrue(TileElement.CHECK in simple)
        assertTrue(TileElement.QUANTITY in simple)
    }

    @Test
    fun `nothing is checked off while making the list`() {
        assertFalse(TileElement.CHECK in Features.ALL.tile(TileLayout.ALL, ListMode.PLAN))
    }

    @Test
    fun `showing a part turns on the feature it needs in that stage`() {
        val f = Features.SIMPLE.allowing(TileLayout(setOf(TileElement.PRICE)), ListMode.PLAN)
        assertTrue(f.isOn(Feature.PRICES, ListMode.PLAN))
        assertFalse(f.isOn(Feature.PRICES, ListMode.SHOP))
        assertTrue(TileElement.PRICE in f.tile(TileLayout(setOf(TileElement.PRICE)), ListMode.PLAN))
    }
}
