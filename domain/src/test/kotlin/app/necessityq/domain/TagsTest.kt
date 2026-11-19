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

    @Test fun `compact tags show only the kind, with a count when there are several`() {
        val parts = listOf(
            RecipePart("שקשוקה", null), RecipePart("פשטידה", null),
            RecipePart("ארוחת שישי", null, fromMenu = true),
        )
        val compact = ItemTags.of(line.copy(recipeParts = parts), null, favorite = false, compact = true)
        assertEquals(listOf("מתכון | 2", "תפריט"), compact.filter { it.kind == TagKind.RECIPE }.map { it.label })
        val full = ItemTags.of(line.copy(recipeParts = parts), null, favorite = false)
        assertEquals(listOf("מתכון: שקשוקה", "מתכון: פשטידה", "תפריט: ארוחת שישי"), full.filter { it.kind == TagKind.RECIPE }.map { it.label })
    }

    @Test fun `a single legacy recipe name is just the kind in compact form`() {
        val tags = ItemTags.of(line.copy(recipe = "שקשוקה"), null, favorite = false, compact = true)
        assertEquals("מתכון", tags.first { it.kind == TagKind.RECIPE }.label)
    }
}
