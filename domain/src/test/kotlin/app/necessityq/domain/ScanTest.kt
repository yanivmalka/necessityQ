package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTest {
    private fun product(code: String, name: String, price: Double, brand: String = "") =
        BranchProduct(code, name, brand, null, "", false, false, price, null, "2026-11-01", price, price)

    private fun prices(branchId: String, vararg products: BranchProduct) = BranchPrices(branchId, 1L, 90, products.toList())

    private fun line(name: String, variant: String?, status: LineStatus = LineStatus.NEEDED, id: String = name) = ListLine(
        id = id,
        itemId = ItemIdentity(name, variant, Category.DAIRY).id,
        name = name,
        variant = variant,
        category = Category.DAIRY,
        quantity = null,
        note = null,
        status = status,
        source = LineSource.MANUAL,
        addedBy = "u",
        addedAtMillis = 0,
        updatedAtMillis = 0,
    )

    private val cottage5 = product("7290004127329", "קוטג' 5% 250 גרם", 6.2, brand = "תנובה")

    @Test fun `the check digit tells a good read from a bad one`() {
        assertTrue(Barcodes.hasValidCheckDigit("7290004127329"))
        assertTrue(Barcodes.hasValidCheckDigit("4006381333931"))
        assertTrue(Barcodes.hasValidCheckDigit("036000291452"))
        assertTrue(Barcodes.hasValidCheckDigit("96385074"))
        assertFalse(Barcodes.hasValidCheckDigit("7290004127328"))
        assertFalse(Barcodes.hasValidCheckDigit("036000291453"))
        assertFalse(Barcodes.hasValidCheckDigit("96385075"))
        assertTrue(Barcodes.hasValidCheckDigit("12345")) // other lengths cannot be checked
    }

    @Test
    fun normalizeKeepsDigitsAndDropsLeadingZeros() {
        assertEquals("7290004127329", Barcodes.normalize(" 7290004127329 "))
        assertEquals("72900041273", Barcodes.normalize("072900041273"))
        assertEquals("12345678", Barcodes.normalize("0012345678"))
        assertNull(Barcodes.normalize("https://example.com"))
        assertNull(Barcodes.normalize("12"))
        assertTrue(Barcodes.same("0729000", "729000"))
    }

    @Test
    fun genericIdentityDropsBrandAndPackageSize() {
        val identity = Scan.genericIdentity(cottage5)
        assertEquals("קוטג'", identity.name)
        assertEquals("5%", identity.variant)
    }

    @Test
    fun lookupFindsProductHereAndElsewhereAndTheOpenLine() {
        val all = mapOf(
            "a" to prices("a", cottage5),
            "b" to prices("b", cottage5.copy(price = 5.9)),
        )
        val lines = listOf(line("קוטג'", "9%", id = "nine"), line("קוטג'", "5%", id = "five"))
        val result = Scan.lookup("7290004127329", "a", all, lines, emptyList(), emptyMap())!!
        assertEquals(6.2, result.here!!.price, 0.001)
        assertEquals(listOf("b"), result.elsewhere.map { it.branchId })
        assertEquals("five", result.line?.id)
        assertEquals(listOf("b"), result.range!!.cheapestBranchIds)
        assertFalse(result.alreadyPreferred)
    }

    @Test
    fun preferredProductWinsOverNameMatching() {
        val all = mapOf("a" to prices("a", product("111111111", "משהו אחר", 3.0)))
        val lines = listOf(line("לבן", null, id = "leben"))
        val result = Scan.lookup("111111111", "a", all, lines, emptyList(), mapOf(lines[0].itemId to "111111111"))!!
        assertEquals("leben", result.line?.id)
        assertTrue(result.alreadyPreferred)
    }

    @Test
    fun unknownCodeStillReturnsAResultWithoutProduct() {
        val result = Scan.lookup("999999999", "a", mapOf("a" to prices("a", cottage5)), emptyList(), emptyList(), emptyMap())!!
        assertFalse(result.found)
        assertNull(result.identity)
        assertNull(Scan.lookup("not a code", "a", emptyMap(), emptyList(), emptyList(), emptyMap()))
    }

    @Test
    fun knownItemIsUsedWhenNotOnTheList() {
        val known = listOf(ItemIdentity("קוטג'", null, Category.DAIRY), ItemIdentity("קוטג'", "5%", Category.DAIRY))
        val result = Scan.lookup("7290004127329", "a", mapOf("a" to prices("a", cottage5)), emptyList(), known, emptyMap())!!
        assertNull(result.line)
        assertEquals("5%", result.identity?.variant)
    }

    private fun branch(id: String, lat: Double?, lng: Double?, approximate: Boolean = false, name: String = id) =
        PriceBranch(id, "c", "רמי לוי", id, name, "", "ירושלים", lat, lng, approximate)

    @Test
    fun branchAtPicksTheNearestExactBranchInRange() {
        val list = listOf(
            branch("talpiot", 31.7510, 35.2130),
            branch("centre-approx", 31.7683, 35.2137, approximate = true),
            branch("other", 31.7600, 35.2000),
        )
        assertEquals("talpiot", BranchLocator.branchAt(list, 31.7512, 35.2131)?.branch?.id)
        // standing on the city centre, which only an approximate branch claims: no match
        assertNull(BranchLocator.branchAt(list, 31.7683, 35.2137))
        // a poor fix widens the circle
        assertNotNull(BranchLocator.branchAt(list, 31.7535, 35.2130, accuracyMeters = 350.0))
        assertNull(BranchLocator.branchAt(list, 31.7535, 35.2130, accuracyMeters = 20.0))
    }

    @Test
    fun storeNameAddsTheChainOnlyWhenMissing() {
        assertEquals("רמי לוי תלפיות", BranchLocator.storeName(branch("x", null, null, name = "תלפיות")))
        assertEquals("רמי לוי - גבעת שאול", BranchLocator.storeName(branch("x", null, null, name = "רמי לוי - גבעת שאול")))
    }
}

class QuantityUnitSwitchTest {
    @Test
    fun convertsWithinADimension() {
        assertEquals(Quantity(1500.0, QuantityUnit.GRAM), QuantitySteps.switchUnit(Quantity(1.5, QuantityUnit.KILOGRAM), QuantityUnit.GRAM))
        assertEquals(Quantity(0.3, QuantityUnit.KILOGRAM), QuantitySteps.switchUnit(Quantity(300.0, QuantityUnit.GRAM), QuantityUnit.KILOGRAM))
        assertEquals(Quantity(3.0, QuantityUnit.PACKAGE), QuantitySteps.switchUnit(Quantity.pieces(3), QuantityUnit.PACKAGE))
    }

    @Test
    fun acrossDimensionsUsesTheUsualAmountOrADefault() {
        // 15 cucumbers → kilos: no conversion is invented
        assertEquals(Quantity(1.0, QuantityUnit.KILOGRAM), QuantitySteps.switchUnit(Quantity.pieces(15), QuantityUnit.KILOGRAM))
        assertEquals(Quantity(1.5, QuantityUnit.KILOGRAM), QuantitySteps.switchUnit(Quantity.pieces(15), QuantityUnit.KILOGRAM, usual = Quantity(1500.0, QuantityUnit.GRAM)))
        assertEquals(Quantity.pieces(1), QuantitySteps.switchUnit(null, QuantityUnit.PIECE))
        assertNull(QuantitySteps.switchUnit(Quantity.pieces(7), null))
    }

    @Test
    fun weighedProduceCountedInPiecesIsNotPriced() {
        assertNull(ListCost.packages(Quantity.pieces(7), weighted = true))
        assertEquals(1.5, ListCost.packages(Quantity(1.5, QuantityUnit.KILOGRAM), weighted = true)!!, 0.001)
        assertEquals(2.0, ListCost.packages(Quantity(2.0, QuantityUnit.PACKAGE), weighted = true)!!, 0.001)
    }
}

class TypingTest {
    private fun t(user: String, at: Long, item: String = "milk") = TypingState(user, user, item, item, at)

    @Test
    fun activeDropsMeAndStaleMarks() {
        val now = 100_000L
        val all = listOf(t("me", now), t("keren", now - 2_000), t("old", now - 20_000))
        assertEquals(listOf("keren"), Typing.active(all, "me", now).map { it.userId })
    }

    @Test
    fun labelAndHeartbeat() {
        assertEquals("קרן באמצע הקלדה…", Typing.label(listOf("קרן")))
        assertEquals("קרן ויניב באמצע הקלדה…", Typing.label(listOf("קרן", "יניב")))
        assertTrue(Typing.shouldAnnounce(null, 0, "milk", 1_000))
        assertFalse(Typing.shouldAnnounce("milk", 1_000, "milk", 2_000))
        assertTrue(Typing.shouldAnnounce("milk", 1_000, "milk", 4_500))
        assertTrue(Typing.shouldAnnounce("milk", 1_000, "bread", 1_500))
    }

    @Test
    fun recordingLabel() {
        val rec = TypingState("a", "קרן", "milk", "milk", 1, TypingKind.RECORDING)
        assertEquals("קרן באמצע הקלטה…", Typing.labelFor(listOf(rec)))
        assertEquals(
            "יניב באמצע הקלדה… · קרן באמצע הקלטה…",
            Typing.labelFor(listOf(t("יניב", 1), rec)),
        )
    }

    @Test
    fun trackerUsesTheReadersClockNotTheWritersClock() {
        val tracker = TypingTracker()
        // the writer's clock is a minute behind ours: irrelevant
        val first = tracker.observe(listOf(t("keren", 40_000)), 100_000)
        assertTrue("already there when listening started: stale", Typing.active(first, "me", 100_000).isEmpty())
        val beat = tracker.observe(listOf(t("keren", 43_000)), 103_000)
        assertEquals(listOf("keren"), Typing.active(beat, "me", 103_000).map { it.userId })
        // an unrelated snapshot does not refresh it; it fades once heartbeats stop
        val same = tracker.observe(listOf(t("keren", 43_000), t("yaniv", 50_000)), 106_000)
        assertEquals(103_000L, same.first { it.userId == "keren" }.atMillis)
        assertTrue(Typing.active(same, "me", 110_000).none { it.userId == "keren" })
        // deleted mark disappears
        assertTrue(tracker.observe(emptyList(), 107_000).isEmpty())
    }
}

class PieceWeightTest {
    private fun weighedCucumber(price: Double) =
        BranchProduct("c$price", "מלפפון", "", null, "ק\"ג", true, true, price, null, "2026-11-01", price, price)

    @Test
    fun estimatesWeightOfCountedProduce() {
        assertEquals(Quantity(1.7, QuantityUnit.KILOGRAM), PieceWeights.estimate("מלפפונים", Quantity.pieces(15)))
        assertEquals(Quantity(750.0, QuantityUnit.GRAM), PieceWeights.estimate("לימון", Quantity.pieces(7)))
        assertNull(PieceWeights.estimate("קוטג'", Quantity.pieces(2)))
        assertNull(PieceWeights.estimate("מלפפונים", Quantity(1.0, QuantityUnit.KILOGRAM)))
    }

    @Test
    fun pricesCountedProduceByEstimatedWeight() {
        val line = ListLine("l", "מלפפונים", "מלפפונים", null, Category.PRODUCE, Quantity.pieces(10), null, LineStatus.NEEDED, LineSource.MANUAL, "u", 0, 0)
        val info = ItemPriceInfo("b", 5.0, 7.0, listOf(weighedCucumber(5.0), weighedCucumber(7.0)), null)
        val cost = LineCosts.weighted(line, info)!!
        // 10 × 110 g = 1.1 kg
        assertEquals(5.5, cost.low, 0.001)
        assertEquals(7.7, cost.high, 0.001)
        assertEquals(1, ListCost.of(listOf(line), emptyMap(), mapOf(line.itemId to info)).pricedLines)
    }
}

class PriceVerdictTest {
    private fun p(price: Double, low: Double = price, high: Double = price, unit: Double? = null, code: String = "x") =
        BranchProduct(code, "קוטג' 5%", "", null, "", false, false, price, unit, "2026-11-01", low, high)

    @Test
    fun otherBranchesDecide() {
        assertEquals(PriceLevel.EXPENSIVE, PriceVerdicts.of(p(7.5), listOf(6.2, 6.4), emptyList())!!.level)
        assertEquals(PriceLevel.CHEAP, PriceVerdicts.of(p(5.0), listOf(6.2, 6.4), emptyList())!!.level)
        assertEquals(PriceLevel.FAIR, PriceVerdicts.of(p(6.3), listOf(6.2, 6.4), emptyList())!!.level)
    }

    @Test
    fun ownRangeAndSimilarProductsWhenAlone() {
        assertEquals(PriceLevel.EXPENSIVE, PriceVerdicts.of(p(7.0, low = 5.9, high = 7.0), emptyList(), emptyList())!!.level)
        val peers = listOf(p(6.0, unit = 24.0, code = "a"), p(6.2, unit = 25.0, code = "b"), p(6.1, unit = 24.5, code = "c"))
        assertEquals(PriceLevel.CHEAP, PriceVerdicts.of(p(4.5, unit = 18.0), emptyList(), peers)!!.level)
        assertNull(PriceVerdicts.of(p(6.0), emptyList(), emptyList()))
    }
}
