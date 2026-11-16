package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TagsTest {
    private val line = ListLine(
        id = "l", itemId = "milk", name = "חלב", variant = null, category = Category.DAIRY, quantity = null, note = null,
        status = LineStatus.NEEDED, source = LineSource.MANUAL, addedBy = "u", addedAtMillis = 1, updatedAtMillis = 1,
    )

    @Test fun `unknown item is special`() {
        assertEquals(listOf(TagKind.SPECIAL), ItemTags.of(line, null, favorite = false).map { it.kind })
    }

    @Test fun `flags come first and recipe is named`() {
        val tags = ItemTags.of(line.copy(missing = true, recipe = "שקשוקה"), null, favorite = true)
        assertEquals(listOf(TagKind.MISSING, TagKind.RECIPE, TagKind.SPECIAL, TagKind.FAVORITE), tags.map { it.kind })
        assertEquals("מתכון: שקשוקה", tags[1].label)
    }
}
