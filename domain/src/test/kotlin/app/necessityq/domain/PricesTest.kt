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
        assertEquals(listOf("2", "3"), PriceData.search(products, "תנובה").map { it.code })
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
}
