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
}
