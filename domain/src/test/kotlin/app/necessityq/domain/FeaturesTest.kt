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

    @Test
    fun `the simple list keeps the reminders, the recommended choice adds prices`() {
        assertTrue(Feature.RUNNING_LOW in Features.SIMPLE)
        assertFalse(Feature.MENU_TAB in Features.SIMPLE)
        assertTrue(Feature.PRICES in Features.RECOMMENDED)
        assertFalse(Feature.CHAT in Features.RECOMMENDED)
    }

    @Test
    fun `a tile part a feature hides is reported as hidden`() {
        val noPrices = Features.ALL.with(Feature.PRICES, false)
        assertEquals(setOf(TileElement.PRICE), noPrices.hiddenElements())
        assertEquals(emptySet<TileElement>(), Features.ALL.hiddenElements())
    }

    @Test
    fun `first-run answers turn on what they describe`() {
        assertEquals(setOf(Feature.RUNNING_LOW, Feature.FREQUENT), FirstRunAnswers.DEFAULT.features().on)
        val all = FirstRunAnswers(FirstRunQuestion.entries.toSet()).features()
        // every feature but the advanced one is reachable from the questions
        assertEquals(Feature.entries.toSet() - Feature.TILE_EXTRAS, all.on)
        assertEquals(Features(emptySet()), FirstRunAnswers(emptySet()).features())
    }
}
