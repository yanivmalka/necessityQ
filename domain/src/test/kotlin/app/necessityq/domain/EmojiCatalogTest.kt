package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiCatalogTest {
    @Test fun `every emoji is offered once`() {
        val all = EmojiCatalog.categories.flatMap { it.emojis }
        assertEquals(all.size, all.toSet().size)
        assertTrue(all.none { it.isBlank() })
    }

    @Test fun `the quick reactions are all in the picker`() {
        val all = EmojiCatalog.categories.flatMap { it.emojis }.toSet()
        assertTrue(Reactions.quick.all { it in all })
    }

    @Test fun `each category's icon is one of its own emoji`() {
        EmojiCatalog.categories.forEach { assertTrue(it.name, it.icon in it.emojis) }
    }
}
