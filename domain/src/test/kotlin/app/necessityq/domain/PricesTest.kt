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
        // one left-to-right unit (between LRM marks): cheap on the left, dear on the right, ₪ at the far right
        assertEquals("\u200E5.90\u00A0₪\u200E", PriceData.formatRange(5.9, 5.9))
        assertEquals("\u200E5.50–6.90\u00A0₪\u200E", PriceData.formatRange(5.5, 6.9))
        assertEquals("\u200E5.50–6.90\u00A0₪\u200E", PriceData.formatRange(6.9, 5.5))
        assertEquals("\u200E~150–184\u00A0₪\u200E", PriceData.formatRange(150.2, 184.0, decimals = 0, prefix = "~"))
        assertEquals("\u200E3.50\u00A0₪\u200E", PriceData.formatPrice(3.5))
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
        BranchProduct(code, code, "", null, if (weighted) "ק\"ג" else "", weighted, false, price, null, "", price, price)

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

class WeightUnitTest {
    private fun weighed(code: String, price: Double, unit: String, measure: String) =
        BranchProduct(code, "זיתים", "", null, unit, true, true, price, null, "2026-11-01", price, price, measure = measure)
    private fun line(q: Quantity?) = ListLine(
        id = "olives", itemId = "olives", name = "זיתים", variant = null, category = Category.entries.first(), quantity = q, note = null,
        status = LineStatus.NEEDED, source = LineSource.MANUAL, addedBy = "u", addedAtMillis = 0, updatedAtMillis = 0,
    )

    @Test
    fun readsThePriceUnitsOfTheFiles() {
        for (u in listOf("קילוגרם", "ק\"ג", "ק״ג", "1קילוגרם", "1 ק\"ג", " קילו ", "לק\"ג", "1000 גרם")) assertEquals(u, 1.0, WeightUnits.kilos(u)!!, 1e-9)
        for (u in listOf("100 גרם", "100גרם", "100  גרם", "ל-100 גרם", "100 ג'")) assertEquals(u, 0.1, WeightUnits.kilos(u)!!, 1e-9)
        // not a weight, or ambiguous: no guess
        for (u in listOf("", "גרם", "100 ק\"ג", "יחידה", "ליטר", "100 מ\"ל")) assertNull(u, WeightUnits.kilos(u))
    }

    @Test
    fun aPricePer100GramsIsTenTimesThatPerKilo() {
        assertEquals(49.0, weighed("o", 4.9, "גרם", "100 גרם").pricePerKilo!!, 1e-9)
        assertEquals(7.9, weighed("t", 7.9, "קילו", "קילוגרם").pricePerKilo!!, 1e-9)
        // older snapshot (no unit of measure): the package unit decides, and a bare "גרם" is not guessed
        assertEquals(7.9, weighed("t", 7.9, "ק\"ג", "").pricePerKilo!!, 1e-9)
        assertNull(weighed("o", 4.9, "גרם", "").pricePerKilo)
        // packaged goods have no price per kilo here
        assertNull(BranchProduct("7290000000001", "קוטג'", "", 250.0, "גרם", false, false, 5.9, null, "", 5.9, 5.9, measure = "100 גרם").pricePerKilo)
    }

    @Test
    fun weighedLineCostsUseThePricePerKilo() {
        val olives = weighed("o", 4.9, "גרם", "100 גרם")
        val half = line(Quantity(500.0, QuantityUnit.GRAM))
        // 4.90 per 100 g × 0.5 kg = 24.50, not 2.45
        val chosen = LineCosts.weighted(half, ItemPriceInfo("b", 4.9, 4.9, listOf(olives), olives))!!
        assertEquals(24.5, chosen.low, 1e-9)
        val range = LineCosts.weighted(half, ItemPriceInfo("b", 4.9, 39.0, listOf(olives, weighed("k", 39.0, "ק\"ג", "קילוגרם")), null))!!
        assertEquals(19.5, range.low, 1e-9)
        assertEquals(24.5, range.high, 1e-9)
        val total = ListCost.of(listOf(half), emptyMap(), mapOf("olives" to ItemPriceInfo("b", 4.9, 4.9, listOf(olives), olives)))
        assertEquals(24.5, total.low, 1e-9)
    }

    @Test
    fun anUnknownPriceUnitIsNotPricedByWeight() {
        val odd = weighed("x", 4.9, "גרם", "100 ק\"ג")
        val half = line(Quantity(500.0, QuantityUnit.GRAM))
        assertNull(LineCosts.weighted(half, ItemPriceInfo("b", 4.9, 4.9, listOf(odd), odd)))
        assertNull(LineCosts.weighted(half, ItemPriceInfo("b", 4.9, 4.9, listOf(odd), null)))
        // a product with a known unit still prices the line; the unknown one is left out
        val known = weighed("k", 30.0, "ק\"ג", "ק\"ג")
        assertEquals(15.0, LineCosts.weighted(half, ItemPriceInfo("b", 4.9, 30.0, listOf(odd, known), null))!!.high, 1e-9)
        // the list total leaves the line unpriced rather than multiplying 4.90 by the weight
        for (preferred in listOf(odd, null)) {
            val e = ListCost.of(listOf(half), emptyMap(), mapOf("olives" to ItemPriceInfo("b", 4.9, 4.9, listOf(odd), preferred)))
            assertEquals(0, e.pricedLines)
            assertEquals(0.0, e.low, 1e-9)
        }
    }

    private fun index(branch: String, vararg products: BranchProduct) = ProductIndex(branch, 0, products.toList())

    @Test
    fun theItemRangeIsPerKiloAndTheCheapestPerKiloComesFirst() {
        val olives100g = weighed("o", 4.9, "גרם", "100 גרם").copy(name = "זיתים ירוקים")
        val olivesKg = weighed("k", 39.0, "ק\"ג", "קילוגרם").copy(name = "זיתים שחורים")
        val info = ItemPrices.info(index("b", olives100g, olivesKg), "זיתים", null, null)!!
        assertEquals(true, info.perKilo)
        assertEquals(39.0, info.low, 1e-9)
        assertEquals(49.0, info.high, 1e-9)
        // 4.90 per 100 g is 49 per kg: dearer than 39 per kg
        assertEquals(listOf("k", "o"), info.candidates.map { it.code })
        // a quantity-less line counts a kilo at the per-kilo range
        val e = ListCost.of(listOf(line(null)), emptyMap(), mapOf("olives" to info))
        assertEquals(39.0, e.low, 1e-9)
        assertEquals(49.0, e.high, 1e-9)
    }

    @Test
    fun anItemByWeightWithUnknownUnitsIsNotReadPerKilo() {
        val known = weighed("k", 39.0, "ק\"ג", "קילוגרם").copy(name = "זיתים שחורים")
        val odd = weighed("x", 4.9, "גרם", "").copy(name = "זיתים ירוקים") // older snapshot: "גרם" alone
        // the unknown unit is left out of the range and listed last
        val mixed = ItemPrices.info(index("b", odd, known), "זיתים", null, null)!!
        assertEquals(true, mixed.perKilo)
        assertEquals(39.0 to 39.0, mixed.low to mixed.high)
        assertEquals(listOf("k", "x"), mixed.candidates.map { it.code })
        // none known: the published prices, not marked per kilo, and not added to the list total
        val unknown = ItemPrices.info(index("b", odd), "זיתים", null, null)!!
        assertEquals(false, unknown.perKilo)
        assertEquals(4.9, unknown.low, 1e-9)
        assertEquals(0, ListCost.of(listOf(line(null)), emptyMap(), mapOf("olives" to unknown)).pricedLines)
        // packaged goods are unchanged
        val jar = BranchProduct("7290000000001", "זיתים בצנצנת", "", 500.0, "גרם", false, false, 12.9, null, "", 12.9, 12.9, measure = "100 גרם")
        val packaged = ItemPrices.info(index("b", jar), "זיתים", null, null)!!
        assertEquals(false, packaged.perKilo)
        assertEquals(12.9, packaged.low, 1e-9)
    }

    @Test
    fun branchesAreComparedPerKilo() {
        // branch a prices olives per 100 g, branch b per kilo; internal codes, so only the typical prices compare
        val here = ItemPriceInfo("a", 49.0, 49.0, listOf(weighed("a1", 4.9, "גרם", "100 גרם")), null, perKilo = true)
        val other = ItemPriceInfo("b", 39.0, 39.0, listOf(weighed("b1", 39.0, "ק\"ג", "ק\"ג")), null, perKilo = true)
        val c = PriceComparisons.item(here, mapOf("b" to other))!!
        assertEquals(CompareBasis.TYPICAL, c.basis)
        // 49 / 39 − 1 ≈ +26% (not 4.90 / 39 − 1 ≈ −87%)
        assertEquals(49.0 / 39.0 - 1, c.diff, 1e-9)
        // a branch whose unit is not known is not compared
        val unknown = ItemPriceInfo("c", 4.9, 4.9, listOf(weighed("c1", 4.9, "גרם", "")), null)
        assertNull(PriceComparisons.item(here, mapOf("c" to unknown)))
    }

    @Test
    fun theSameBarcodeIsComparedPerKilo() {
        // one chain publishes the olives per 100 g, another per kg
        val here = weighed("2900001", 4.9, "גרם", "100 גרם").copy(internalCode = false)
        val perKg = weighed("2900001", 39.0, "ק\"ג", "קילוגרם").copy(internalCode = false)
        assertEquals(3.9, PriceComparisons.inUnitOf(here, perKg)!!, 1e-9)
        assertEquals(49.0, PriceComparisons.inUnitOf(perKg, here)!!, 1e-9)
        val c = PriceComparisons.product(here, "a", mapOf("b" to mapOf("2900001" to perKg)))!!
        // 4.90 per 100 g against 3.90 per 100 g: dearer by ~26%, not 87% cheaper
        assertEquals(4.9 / 3.9 - 1, c.diff, 1e-9)
        assertEquals(PriceLevel.EXPENSIVE, c.level)
        assertEquals(3.9, c.others.single().low, 1e-9)
        // not comparable: unknown unit, or by weight against by the package
        val unknown = here.copy(measure = "", unit = "גרם")
        val packaged = BranchProduct("2900001", "זיתים", "", 500.0, "גרם", false, false, 12.9, null, "", 12.9, 12.9)
        assertNull(PriceComparisons.product(here, "a", mapOf("b" to mapOf("2900001" to unknown))))
        assertNull(PriceComparisons.product(unknown, "a", mapOf("b" to mapOf("2900001" to perKg))))
        assertNull(PriceComparisons.product(here, "a", mapOf("b" to mapOf("2900001" to packaged))))
    }

    @Test
    fun sharedProductsAreComparedPerKilo() {
        val a = weighed("2900001", 4.9, "גרם", "100 גרם").copy(internalCode = false)
        val b = weighed("2900001", 39.0, "ק\"ג", "קילוגרם").copy(internalCode = false)
        val here = ItemPriceInfo("a", 49.0, 49.0, listOf(a), null, perKilo = true)
        val other = ItemPriceInfo("b", 39.0, 39.0, listOf(b), null, perKilo = true)
        val c = PriceComparisons.item(here, mapOf("b" to other))!!
        assertEquals(CompareBasis.SHARED_PRODUCTS, c.basis)
        assertEquals(49.0 / 39.0 - 1, c.diff, 1e-9)
        // the shared product's unit is not known at b: falls back to the typical prices, which leave it out too
        val unknownAtB = ItemPriceInfo("b", 4.9, 4.9, listOf(b.copy(measure = "", unit = "גרם")), null)
        assertNull(PriceComparisons.item(here, mapOf("b" to unknownAtB)))
    }
}
