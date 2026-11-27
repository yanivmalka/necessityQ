package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One answer to "how much is one" per item: the pack the household buys, used everywhere. */
class PacksTest {
    private fun product(name: String, size: Double? = null, unit: String = "", weighted: Boolean = false, price: Double = 10.0, code: String = name) =
        BranchProduct(code, name, "", size, unit, weighted, false, price, null, "", price, price)

    private fun liters(x: Double) = Quantity(x, QuantityUnit.LITER)
    private fun grams(x: Double) = Quantity(x, QuantityUnit.GRAM)

    @Test fun `a product's pack from its file and its name`() {
        assertEquals(Pack(30), ProductPacks.of(product("ביצים L", 30.0, "יחידה")))
        assertEquals(Pack(12), ProductPacks.of(product("ביצים גדולות 12 יח'")))
        assertEquals(Pack(1, liters(2.0)), ProductPacks.of(product("חלב 3% 2 ליטר")))
        assertEquals(Pack(1, liters(2.0)), ProductPacks.of(product("חלב טרי 3%", 2.0, "ליטר")))
        assertEquals(Pack(8, grams(150.0)), ProductPacks.of(product("יוגורט 8 יח' 150 גרם")))
        assertEquals(Pack(6, liters(1.5)), ProductPacks.of(product("מים מינרליים 6X1.5 ליטר")))
        assertEquals(Pack(1, grams(250.0)), ProductPacks.of(product("קוטג' 5% 250 גרם")))
        assertNull(ProductPacks.of(product("עגבניות", 1.0, "ק\"ג", weighted = true)))
        assertNull(ProductPacks.of(product("לחם אחיד")))
    }

    @Test fun `the household's pack is its product, its choice, else what it always buys`() {
        assertEquals(Pack(30), HouseholdPacks.resolve("ביצים", null, null, null, Quantity.pieces(30)))
        assertEquals(Pack(12), HouseholdPacks.resolve("ביצים", null, null, null, Quantity.pieces(24)))
        assertEquals(Pack(1, liters(2.0)), HouseholdPacks.resolve("חלב", "3%", null, null, liters(2.0)))
        assertEquals(Pack(1, liters(1.0)), HouseholdPacks.resolve("חלב", null, null, 1000, liters(2.0)))
        assertEquals(Pack(12), HouseholdPacks.resolve("ביצים", null, product("ביצים 12 יח'"), null, Quantity.pieces(30)))
        assertNull(HouseholdPacks.resolve("ביצים", null, null, null, Quantity.pieces(7)))
        assertNull(HouseholdPacks.resolve("עגבניות", null, null, null, Quantity(1.0, QuantityUnit.KILOGRAM)))
    }

    @Test fun `amounts are whole packs, shown with them, stepped by them`() {
        val yoghurt = Pack(8)
        assertEquals(Quantity.pieces(16), yoghurt.nearest(Quantity.pieces(19)))
        assertEquals(Quantity.pieces(24), yoghurt.covering(Quantity.pieces(19)))
        assertEquals(Quantity.pieces(8), yoghurt.nearest(Quantity.pieces(3)))
        assertEquals("2 מארזים של 8", yoghurt.describe(Quantity.pieces(16)))
        assertEquals("19 יח'", yoghurt.describe(Quantity.pieces(19)))
        assertEquals(Quantity.pieces(24), yoghurt.step(Quantity.pieces(16), +1))
        assertEquals(Quantity.pieces(8), yoghurt.step(Quantity.pieces(16), -1))
        assertEquals(Quantity.pieces(8), yoghurt.step(Quantity.pieces(8), -1))

        val eggs = Pack(30)
        assertEquals("מארז 30", eggs.describe(Quantity.pieces(30)))
        assertEquals("2 מארזים של 30", eggs.describe(Quantity(2.0, QuantityUnit.PACKAGE)))

        val milk = Pack(1, liters(2.0))
        assertEquals("2 ליטר", milk.describe(liters(2.0)))
        assertEquals("4 ליטר · 2 × 2 ליטר", milk.describe(liters(4.0)))
        assertEquals(liters(4.0), milk.step(liters(2.0), +1))
        assertEquals(liters(2.0), milk.nearest(liters(1.5)))
        assertEquals(Quantity(2.0, QuantityUnit.PACKAGE), milk.step(Quantity(1.0, QuantityUnit.PACKAGE), +1))
    }

    @Test fun `the branch's products in the household's size first, each with its size`() {
        val trays = listOf(product("ביצים 12 יח'", code = "a"), product("ביצים L 30 יח'", code = "b"), product("ביצים M 30 יח'", code = "c"))
        val offers = HouseholdPacks.offers(trays, Pack(30), null)
        assertEquals(listOf("b", "c", "a"), offers.map { it.product.code })
        assertEquals(listOf(true, true, false), offers.map { it.fits })
        assertEquals("מארז 12", offers.last().pack!!.label())

        // no pack yet: "חלב 2 ליטר" on the list puts the 2 L cartons first, not two of 1 L
        val milk = listOf(product("חלב 3% 1 ליטר", code = "1"), product("חלב 3% 2 ליטר", code = "2"))
        val byList = HouseholdPacks.offers(milk, null, liters(2.0))
        assertEquals(listOf("2", "1"), byList.map { it.product.code })
        assertEquals(listOf(true, false), byList.map { it.fits })
        // nothing to go by: the order is kept, nothing marked
        assertFalse(HouseholdPacks.offers(milk, null, null).any { it.fits })
    }

    @Test fun `a household that buys trays of 30 is shown trays of 30 only, of every grade`() {
        val trays = listOf(product("ביצים 12 יח'", code = "a"), product("ביצים L 30 יח'", code = "b"), product("ביצים M 30 יח'", code = "c"))
        val offers = HouseholdPacks.offers(trays, Pack(30), null)
        assertEquals(listOf("b", "c"), HouseholdPacks.visible(offers, null, otherSizes = false).map { it.product.code })
        // asked for: every size
        assertEquals(listOf("b", "c", "a"), HouseholdPacks.visible(offers, null, otherSizes = true).map { it.product.code })
        // the chosen product is always shown, whatever its size
        assertEquals(listOf("b", "c", "a"), HouseholdPacks.visible(offers, "a", otherSizes = false).map { it.product.code })
        // no size known: nothing is hidden
        val free = HouseholdPacks.offers(trays, null, null)
        assertEquals(3, HouseholdPacks.visible(free, null, otherSizes = false).size)
        // the size on the list counts too: "חלב 2 ליטר" hides the 1 L cartons
        val milk = listOf(product("חלב 3% 1 ליטר", code = "1"), product("חלב 3% 2 ליטר", code = "2"))
        assertEquals(listOf("2"), HouseholdPacks.visible(HouseholdPacks.offers(milk, null, liters(2.0)), null, false).map { it.product.code })
    }

    @Test fun `computed amounts follow the household's pack`() {
        val id = ItemIdentity.fromText("יוגורט")
        val free = TripSizer(emptyList(), 4.0).size(id, null).quantity!!
        val packed = TripSizer(emptyList(), 4.0, chosen = PackChoices(products = mapOf(id.id to Pack(8)))).size(id, null).quantity!!
        assertTrue(free.toString(), free.amount % 8.0 != 0.0)
        assertEquals(0.0, packed.amount % 8.0, 0.0)
        assertEquals(Pack(8), TripSizer(emptyList(), 4.0, chosen = PackChoices(products = mapOf(id.id to Pack(8)))).packFor(id))
    }

    @Test fun `a product's price is per its pack`() {
        val line = ListLine(
            id = "m", itemId = "m", name = "חלב", variant = null, category = Category.DAIRY, quantity = liters(4.0), note = null,
            status = LineStatus.NEEDED, source = LineSource.MANUAL, addedBy = "u", addedAtMillis = 0, updatedAtMillis = 0,
        )
        val carton = product("חלב 3% 2 ליטר", price = 12.0)
        val cost = ListCost.of(listOf(line), emptyMap(), mapOf("m" to ItemPriceInfo("b", 12.0, 12.0, listOf(carton), carton)))
        assertEquals(24.0, cost.low, 1e-9)
    }

    @Test fun `a big bare number of a thing sold by weight is grams`() {
        assertEquals(grams(750.0), QuickEntryParser.parse("750 גבינה לבנה")!!.quantity)
        assertEquals(Quantity.pieces(2), QuickEntryParser.parse("2 גבינה לבנה")!!.quantity)
        assertEquals(Quantity.pieces(30), QuickEntryParser.parse("30 מלפפונים")!!.quantity)
        assertEquals(Quantity.pieces(80), QuickEntryParser.parse("80 הפתעות לפסוקים")!!.quantity)
    }
}
