package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PricesTest {
    private fun branch(id: String, lat: Double?, lng: Double?, name: String = id) =
        PriceBranch(id, "c", "רשת", id, name, "", "ירושלים", lat, lng)

    private fun product(code: String, name: String, price: Double, brand: String = "", internal: Boolean = false) =
        BranchProduct(code, name, brand, null, "", false, internal, price, null, "2026-11-01", price, price)

    @Test
    fun nearbySortsByDistanceAndDropsFarBranches() {
        val user = 31.7683 to 35.2137 // city centre
        val list = listOf(
            branch("far", 32.0853, 34.7818), // Tel Aviv
            branch("near", 31.7690, 35.2140),
            branch("mid", 31.7500, 35.2100),
            branch("unknown", null, null),
        )
        val result = PriceData.nearby(list, user.first, user.second, radiusKm = 15.0)
        assertEquals(listOf("near", "mid", "unknown"), result.map { it.branch.id })
        assertNull(result.last().distanceMeters)
        assertEquals(listOf("far", "mid", "near", "unknown"), PriceData.nearby(list, null, null).map { it.branch.id })
    }

    @Test
    fun searchIgnoresQuotesAndNeedsEveryWord() {
        val products = listOf(
            product("1", "קוטג' 5% 250 גרם", 5.9, "טרה"),
            product("2", "קוטג׳ 9% 250 גרם", 6.4, "תנובה"),
            product("3", "חלב 3% 1 ליטר", 6.2, "תנובה"),
            product("7290000042015", "חלב 1% 1 ליטר", 6.1),
        )
        assertEquals(listOf("1", "2"), PriceData.search(products, "קוטג").map { it.code })
        assertEquals(listOf("1"), PriceData.search(products, "קוטג 5%").map { it.code })
        assertEquals(listOf("3", "2"), PriceData.search(products, "תנובה").map { it.code })
        assertEquals(listOf("7290000042015"), PriceData.search(products, "7290000042015").map { it.code })
        assertEquals(emptyList<BranchProduct>(), PriceData.search(products, "  "))
    }

    @Test
    fun rangeAcrossBranchesSkipsInternalCodes() {
        val prices = mapOf(
            "a" to BranchPrices("a", 0, 90, listOf(product("729", "חלב", 6.2), product("12", "עגבניות", 7.9, internal = true))),
            "b" to BranchPrices("b", 0, 90, listOf(product("729", "חלב", 5.9), product("12", "עגבניות", 4.9, internal = true))),
            "c" to BranchPrices("c", 0, 90, listOf(product("729", "חלב", 5.9))),
        )
        val r = PriceData.acrossBranches("729", prices)!!
        assertEquals(5.9, r.low, 0.0)
        assertEquals(6.2, r.high, 0.0)
        assertEquals(listOf("b", "c"), r.cheapestBranchIds)
        assertNull(PriceData.acrossBranches("12", prices))
    }

    @Test
    fun formats() {
        assertEquals("₪5.90", PriceData.formatRange(5.9, 5.9))
        assertEquals("₪5.50–₪6.90", PriceData.formatRange(5.5, 6.9))
        assertEquals("1.2 ק\"מ", PriceData.formatDistance(1234.0))
        assertEquals("350 מ'", PriceData.formatDistance(351.0))
    }

    @Test
    fun quotesShowEachBranchWithItsLastChange() {
        fun b(id: String, chain: String) = PriceBranch(id, chain, chain, id, id, "", "ירושלים", null, null)
        fun p(price: Double, history: List<Pair<String, Double>>, internal: Boolean = false) =
            BranchProduct("729", "חלב", "", null, "", false, internal, price, null, "", price, price, history)
        val branches = listOf(b("a", "x"), b("b", "y"), b("c", "y"), b("d", "y"))
        val prices = mapOf(
            "a" to BranchPrices("a", 0, 90, listOf(p(6.0, listOf("2026-10-01" to 5.5, "2026-11-01" to 6.0)))),
            "b" to BranchPrices("b", 0, 90, listOf(p(5.0, listOf("2026-10-01" to 5.9, "2026-11-01" to 5.0)))),
            "c" to BranchPrices("c", 0, 90, listOf(p(5.2, emptyList()))),
        )
        val q = PriceData.quotes("729", internalCode = false, homeChain = "x", branches = branches, prices = prices)
        assertEquals(listOf("a", "b", "c"), q.map { it.branch.id })
        assertEquals(0.5, q[0].change!!, 0.001)
        assertEquals(-0.9, q[1].change!!, 0.001)
        assertNull(q[2].change)
        // a chain-internal code is not looked up in other chains
        assertEquals(listOf("a"), PriceData.quotes("729", internalCode = true, homeChain = "x", branches = branches, prices = prices).map { it.branch.id })
    }
}

class ItemPricesTest {
    private fun p(code: String, name: String, price: Double, brand: String = "") =
        BranchProduct(code, name, brand, null, "", false, false, price, null, "2026-11-01", price, price)

    private val index = ProductIndex(
        "b", 0,
        listOf(
            p("1", "קוטג' 5% 250 גרם", 5.9, "טרה"),
            p("2", "קוטג׳ 5% 250 גרם", 6.4, "תנובה"),
            p("3", "קוטג' 9% 250 גרם", 6.9, "תנובה"),
            p("4", "קוטג 5% 500 גרם", 11.9, "שטראוס"),
            p("5", "קוטג' 15% מועשר", 7.8, "גד"),
            p("6", "פסטה ברוטב קוטג'", 14.9),
            p("7", "חלב 3% 1 ליטר", 6.2, "תנובה"),
        ),
    )

    @Test
    fun genericNameMatchesAllCottageButNotDishesMentioningIt() {
        val c = ItemPrices.candidates(index, "קוטג'", null)
        assertEquals(listOf("1", "2", "3", "5", "4"), c.map { it.code })
    }

    @Test
    fun variantPercentIsAWholeNumber() {
        assertEquals(listOf("1", "2", "4"), ItemPrices.candidates(index, "קוטג'", "5%").map { it.code })
    }

    @Test
    fun rangeTrimsOutliersWithEnoughProducts() {
        assertEquals(5.9 to 11.9, ItemPrices.typicalRange(listOf(5.9, 6.4, 11.9)))
        val many = listOf(1.0, 5.9, 6.0, 6.2, 6.4, 6.8, 7.0, 7.2, 7.8, 30.0)
        assertEquals(5.9 to 7.8, ItemPrices.typicalRange(many)) // ten products: one trimmed at each end
        assertNull(ItemPrices.typicalRange(emptyList()))
    }

    @Test
    fun preferredProductAndFilters() {
        val info = ItemPrices.info(index, "קוטג'", null, preferredCode = "3")!!
        assertEquals("3", info.preferred?.code)
        assertEquals(5.9, info.low, 0.0)
        assertEquals(listOf("תנובה", "טרה", "גד", "שטראוס"), ItemPrices.brands(info.candidates))
        assertEquals(listOf("5%", "9%", "15%"), ItemPrices.percents(info.candidates))
        assertNull(ItemPrices.info(index, "במבה", null, null))
    }
}

class ListCostTest {
    private fun line(itemId: String, q: Quantity?) = ListLine(
        id = itemId, itemId = itemId, name = itemId, variant = null, category = Category.entries.first(), quantity = q, note = null,
        status = LineStatus.NEEDED, source = LineSource.MANUAL, addedBy = "u", addedAtMillis = 0, updatedAtMillis = 0,
    )
    private fun prod(code: String, price: Double, weighted: Boolean = false) =
        BranchProduct(code, code, "", null, "", weighted, false, price, null, "", price, price)

    @Test
    fun combinesOwnPricesPreferredProductsAndRanges() {
        val lines = listOf(
            line("milk", Quantity(2.0, QuantityUnit.PIECE)),
            line("cottage", Quantity(2.0, QuantityUnit.PACKAGE)),
            line("tomato", Quantity(500.0, QuantityUnit.GRAM)),
            line("unknown", null),
        )
        val infos = mapOf(
            "cottage" to ItemPriceInfo("b", 5.9, 7.8, listOf(prod("1", 5.9), prod("2", 7.8)), null),
            "tomato" to ItemPriceInfo("b", 7.9, 7.9, listOf(prod("t", 7.9, weighted = true)), prod("t", 7.9, weighted = true)),
        )
        val e = ListCost.of(lines, mapOf("milk" to 6.0), infos)
        assertEquals(12.0 + 11.8 + 3.95, e.low, 1e-9)
        assertEquals(12.0 + 15.6 + 3.95, e.high, 1e-9)
        assertEquals(3, e.pricedLines)
        assertEquals(4, e.openLines)
        assertEquals(true, e.isRange)
    }

    @Test
    fun produceMatchesSingularNamesAndPrefersTheFreshItem() {
        fun kilo(code: String, name: String, price: Double) =
            BranchProduct(code, name, "", 1.0, "ק\"ג", true, true, price, price, "2026-11-01", price, price)
        val index = ProductIndex(
            "b", 0,
            listOf(
                kilo("101", "עגבניה", 6.9),
                kilo("102", "עגבניה שרי", 12.9),
                kilo("103", "מלפפון", 5.9),
                kilo("104", "בצל יבש", 3.9),
                kilo("105", "תפוח אדמה לבן", 4.5),
                BranchProduct("7290001", "מלפפון חמוץ במלח", "", null, "", false, false, 9.9, null, "", 9.9, 9.9),
                BranchProduct("7290002", "רסק עגבניות", "", null, "", false, false, 3.5, null, "", 3.5, 3.5),
            ),
        )
        assertEquals(listOf("101", "102"), ItemPrices.candidates(index, "עגבניות", null).map { it.code })
        assertEquals(listOf("103"), ItemPrices.candidates(index, "מלפפונים", null).map { it.code })
        assertEquals(listOf("104"), ItemPrices.candidates(index, "בצל", null).map { it.code })
        assertEquals(listOf("105"), ItemPrices.candidates(index, "תפוחי אדמה", null).map { it.code })
        // a packaged product still matches its own name
        assertEquals(listOf("7290002"), ItemPrices.candidates(index, "רסק עגבניות", null).map { it.code })
        val all = listOf(kilo("101", "עגבניה", 6.9), kilo("103", "מלפפון", 5.9))
        assertEquals(listOf("101"), PriceData.search(all, "עגבניות").map { it.code })
        assertEquals(listOf("103"), PriceData.search(all, "מלפפונים").map { it.code })
    }
}
