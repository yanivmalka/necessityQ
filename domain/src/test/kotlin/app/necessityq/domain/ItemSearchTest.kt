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

    private fun names(items: List<ItemIdentity>) = items.map { it.displayName }

    @Test
    fun `bread is bread, not crackers that begin with the same letters`() {
        val mine = names(Autocomplete.complete("לחם", emptyList()))
        assertEquals("לחם", mine.first())
        assertFalse(mine.toString(), mine.contains("לחמית"))
        val store = names(ItemSearch.storeItems(shelf, "לחם"))
        assertFalse(store.toString(), store.any { it.startsWith("לחמית") })
        assertTrue(store.toString(), store.contains("לחם אחיד פרוס"))
        // "פירורי לחם" is crumbs: it may come, but after the breads
        assertTrue(store.toString(), store.indexOf("לחם אחיד פרוס") < store.indexOf("פירורי לחם").let { if (it < 0) Int.MAX_VALUE else it })
    }

    @Test
    fun `a word still being typed may begin longer words`() {
        val mine = names(Autocomplete.complete("לח", emptyList()))
        assertTrue(mine.toString(), mine.containsAll(listOf("לחם", "לחמית")))
        assertTrue(names(ItemSearch.storeItems(shelf, "לח")).any { it.startsWith("לחמית") })
    }

    @Test
    fun `drumsticks are not chocolate milk`() {
        val mine = names(Autocomplete.complete("שוקיים", emptyList()))
        assertEquals("שוקיים", mine.first())
        assertFalse(mine.toString(), mine.any { it.startsWith("שוקו") })
        val store = names(ItemSearch.storeItems(shelf, "שוקיים"))
        assertEquals(listOf("שוקיים עוף טרי"), store)
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

        assertEquals("פלפל צבעוני", Autocomplete.complete("פלפלים בכל מיני צבעים", emptyList()).first().displayName)
        // the coloured ones first; black pepper (a spice) is not one of them
        val store = names(ItemSearch.storeItems(shelf, "פלפלים בכל מיני צבעים"))
        assertEquals(setOf("פלפל אדום", "פלפל צהוב", "פלפל ירוק חריף"), store.take(3).toSet())

        // and its price is the coloured peppers' (the mix narrows nothing)
        val index = ProductIndex("b", 0, shelf)
        assertTrue(ItemPrices.candidates(index, "פלפל", ItemIdentity.COLORFUL).any { it.code == "8" })
    }

    @Test
    fun `milk is not halva, and a percent typed is kept`() {
        val store = names(ItemSearch.storeItems(shelf, "חלב"))
        assertFalse(store.toString(), store.any { it.startsWith("חלבה") })
        assertEquals(listOf("קוטג' 5%"), names(ItemSearch.storeItems(shelf, "קוטג 5%")))
        assertEquals("קוטג' 5%", Autocomplete.complete("קוטג 5%", emptyList()).first().displayName)
    }

    @Test
    fun `the household's own items are found by whole words too`() {
        val stats = listOf(
            stats("לחמית"),
            stats("לחם מלא"),
            stats("קוטג 9%"),
        )
        val bread = names(Autocomplete.complete("לחם", stats))
        assertFalse(bread.toString(), bread.contains("לחמית"))
        assertTrue(bread.toString(), bread.containsAll(listOf("לחם", "לחם מלא")))
        // a different kind than the one typed is not offered as it
        assertEquals(listOf("קוטג' 5%"), names(Autocomplete.complete("קוטג 5%", stats)))
    }

    @Test
    fun `extra words that match nothing still find the item`() {
        assertEquals("פלפל", Autocomplete.complete("פלפלים טריים מהשוק", emptyList()).first().displayName)
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
