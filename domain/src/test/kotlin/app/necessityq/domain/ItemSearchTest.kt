package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quick add finds the item meant: whole words, forms of a word, and describing words read as the kind. */
class ItemSearchTest {
    private fun product(code: String, name: String, brand: String = "") =
        BranchProduct(code, name, brand, null, "", false, false, 10.0, null, "2026-11-01", 10.0, 10.0)

    private val shelf = listOf(
        product("1", "לחמית קלאסית 200 גרם", "אסם"),
        product("2", "לחם אחיד פרוס 750 גרם", "אנג'ל"),
        product("3", "לחם מחמצת"),
        product("4", "פירורי לחם 500 גרם"),
        product("5", "שוקו בשקית 1 ליטר", "תנובה"),
        product("6", "שוקולד חלב 100 גרם", "עלית"),
        product("7", "שוקיים עוף טרי"),
        product("8", "פלפל אדום"),
        product("9", "פלפל צהוב"),
        product("10", "פלפל ירוק חריף"),
        product("11", "פלפל שחור גרוס 100 גרם"),
        product("12", "חלבה בטעם וניל"),
        product("13", "חלב 3% 1 ליטר", "תנובה"),
        product("14", "קוטג' 5% 250 גרם", "טרה"),
        product("15", "קוטג' 9% 250 גרם", "תנובה"),
    )

    private fun stats(text: String): ItemStats {
        val identity = ItemIdentity.fromText(text)
        val day = java.time.LocalDate.of(2026, 11, 1)
        return ItemStats(
            identity.id, identity, purchaseDays = 3, firstPurchase = day.minusDays(30), lastPurchase = day,
            cadenceDays = 10.0, spreadDays = 1.0, typicalQuantity = null, lastQuantity = null, nextDue = day.plusDays(10),
            confidence = 0.8, classification = Classification.ROUTINE, frequencyScore = 1.0,
        )
    }

    private fun names(items: List<Completion>) = items.map { it.identity.displayName }

    private fun groups(text: String, stats: List<ItemStats> = emptyList(), products: List<BranchProduct>? = shelf) =
        QuickAddCompletions.build(text, stats, products)

    private fun List<CompletionGroup>.all() = flatMap { names(it.items) }
    private fun List<CompletionGroup>.of(category: Category) = firstOrNull { it.category == category }?.let { names(it.items) }.orEmpty()

    @Test
    fun `bread first in its own section, and related products in theirs`() {
        val g = groups("לחם")
        assertEquals(Category.BAKERY, g.first().category)
        assertTrue(g.first().main)
        val bread = names(g.first().items)
        assertEquals("לחם", bread.first())
        assertTrue(bread.toString(), bread.contains("לחם אחיד פרוס"))
        assertFalse(bread.toString(), bread.any { it.startsWith("לחמית") })
        // not dropped: crackers and crumbs are offered, each under its own section, after the bread
        assertTrue(g.toString(), g.of(Category.BREAKFAST_SNACKS).any { it.startsWith("לחמית") })
        assertTrue(g.toString(), g.of(Category.PANTRY).any { it.startsWith("פירורי לחם") })
        assertEquals(1, g.count { it.main })
    }

    @Test
    fun `a word still being typed may begin longer words`() {
        assertTrue(groups("לח").all().containsAll(listOf("לחם", "לחמית")))
    }

    @Test
    fun `drumsticks are not chocolate milk`() {
        val g = groups("שוקיים")
        assertEquals(Category.MEAT_FISH, g.first().category)
        assertEquals("שוקיים", names(g.first().items).first())
        assertTrue(g.of(Category.MEAT_FISH).toString(), g.of(Category.MEAT_FISH).contains("שוקיים עוף טרי"))
        assertFalse(g.all().toString(), g.all().any { it.startsWith("שוקו") })
        assertEquals(Category.MEAT_FISH, ItemIdentity.fromText("שוקי עוף").category)
    }

    @Test
    fun `peppers in all kinds of colours are a mix of coloured peppers`() {
        val typed = QuickEntryParser.parse("פלפלים בכל מיני צבעים")!!
        assertEquals("פלפל", typed.item.name)
        assertEquals(ItemIdentity.COLORFUL, typed.item.variant)
        assertEquals(Category.PRODUCE, typed.item.category)
        assertEquals("פלפל צבעוני", ItemIdentity.fromText("פלפלים צבעוניים").displayName)
        assertEquals("פלפל צבעוני", ItemIdentity.fromText("פלפל בצבעים שונים").displayName)

        val g = groups("פלפלים בכל מיני צבעים")
        val produce = names(g.first().items)
        assertEquals(Category.PRODUCE, g.first().category)
        assertEquals("פלפל צבעוני", produce.first())
        assertTrue(produce.toString(), produce.containsAll(listOf("פלפל אדום", "פלפל צהוב", "פלפל ירוק חריף")))
        // black pepper is a spice: offered under the pantry, not with the vegetables
        assertFalse(produce.toString(), produce.any { it.startsWith("פלפל שחור") })
        assertTrue(g.toString(), g.of(Category.PANTRY).any { it.startsWith("פלפל שחור") })

        // and its price is the coloured peppers' (the mix narrows nothing)
        val index = ProductIndex("b", 0, shelf)
        assertTrue(ItemPrices.candidates(index, "פלפל", ItemIdentity.COLORFUL).any { it.code == "8" })
    }

    @Test
    fun `milk is not halva, and a percent typed is kept`() {
        val milk = groups("חלב")
        assertEquals(Category.DAIRY, milk.first().category)
        assertFalse(milk.first().items.toString(), names(milk.first().items).any { it.startsWith("חלבה") })
        val cottage = groups("קוטג 5%")
        assertEquals("קוטג' 5%", names(cottage.first().items).first())
        assertFalse(cottage.all().toString(), cottage.all().any { it.contains("9%") })
    }

    @Test
    fun `the household's own items are found by whole words too, and come first on a tie`() {
        val stats = listOf(stats("לחמית"), stats("לחם מלא"), stats("קוטג 9%"))
        val g = groups("לחם", stats, products = null)
        val bread = names(g.first().items)
        assertTrue(bread.toString(), bread.containsAll(listOf("לחם", "לחם מלא")))
        assertFalse(bread.toString(), bread.contains("לחמית"))
        assertTrue(g.of(Category.BREAKFAST_SNACKS).contains("לחמית"))
        assertEquals(Completion.Source.MINE, g.first().items.first { it.identity.name == "לחם מלא" }.source)
        // a different kind than the one typed is not offered as it
        assertEquals(listOf("קוטג' 5%"), groups("קוטג 5%", stats, products = null).all())
    }

    @Test
    fun `extra words that match nothing still find the item`() {
        assertEquals("פלפל", groups("פלפלים טריים מהשוק", products = null).first().items.first().identity.displayName)
    }

    @Test
    fun `forms of a word`() {
        assertTrue(ItemSearch.sameForm("עגבניות", "עגבנייה"))
        assertTrue(ItemSearch.sameForm("שוקיים", "שוקי"))
        assertTrue(ItemSearch.sameForm("תפוחי", "תפוח"))
        assertFalse(ItemSearch.sameForm("שוקיים", "שוקו"))
        assertFalse(ItemSearch.sameForm("לחם", "לחמית"))
        assertFalse(ItemSearch.sameForm("חלב", "חלבה"))
        assertFalse(ItemSearch.sameForm("לבן", "לבנה"))
    }
}
