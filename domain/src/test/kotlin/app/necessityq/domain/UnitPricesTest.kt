package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitPricesTest {
    private fun packaged(code: String, price: Double) =
        BranchProduct(code, "קוטג'", "", null, "", false, false, price, null, "", price, price)

    private fun byWeight(code: String, price: Double, measure: String) =
        BranchProduct(code, "עגבניות", "", null, measure, true, true, price, null, "", price, price, measure = measure)

    private fun info(candidates: List<BranchProduct>, preferred: BranchProduct? = null, low: Double, high: Double, perKilo: Boolean = false) =
        ItemPriceInfo("b", low, high, candidates, preferred, perKilo)

    @Test fun `packaged goods are shown per unit, not for the amount on the list`() {
        val p = info(listOf(packaged("1", 5.9), packaged("2", 7.8)), low = 5.9, high = 7.8)
        assertEquals(UnitPrice(5.9, 7.8, "ליח'"), UnitPrices.of(p, WeightPriceUnit.KILO))
        val chosen = info(listOf(packaged("1", 5.9)), preferred = packaged("1", 5.9), low = 5.9, high = 5.9)
        assertEquals(UnitPrice(5.9, 5.9, "ליח'"), UnitPrices.of(chosen, WeightPriceUnit.HUNDRED_GRAMS))
    }

    @Test fun `by weight per kilo or per 100 grams, as chosen`() {
        val p = info(listOf(byWeight("1", 8.0, "ק\"ג")), low = 8.0, high = 12.0, perKilo = true)
        assertEquals(UnitPrice(8.0, 12.0, "לק\"ג"), UnitPrices.of(p, WeightPriceUnit.KILO))
        val per100 = UnitPrices.of(p, WeightPriceUnit.HUNDRED_GRAMS)!!
        assertEquals(0.8, per100.low, 1e-9)
        assertEquals(1.2, per100.high, 1e-9)
        assertEquals("ל-100 ג'", per100.per)

        // a product published per 100 g, shown per kilo
        val olives = byWeight("2", 4.9, "100 גרם")
        val chosen = info(listOf(olives), preferred = olives, low = 49.0, high = 49.0, perKilo = true)
        assertEquals(49.0, UnitPrices.of(chosen, WeightPriceUnit.KILO)!!.low, 1e-9)
        assertEquals(4.9, UnitPrices.of(chosen, WeightPriceUnit.HUNDRED_GRAMS)!!.low, 1e-9)
    }

    @Test fun `by weight with an unknown price unit shows no price`() {
        val p = info(listOf(byWeight("1", 8.0, "יח")), low = 8.0, high = 8.0)
        assertNull(UnitPrices.of(p, WeightPriceUnit.KILO))
    }

    @Test fun `a comparison by weight is shown in the chosen unit`() {
        val c = PriceComparison("a", 0.1, CompareBasis.TYPICAL, BranchPriceRange("a", 10.0, 12.0), emptyList(), unitKilos = 1.0)
        assertEquals(BranchPriceRange("a", 1.0, 1.2).low, c.shown(c.here, WeightPriceUnit.HUNDRED_GRAMS).low, 1e-9)
        val packagedC = c.copy(unitKilos = null)
        assertEquals(c.here, packagedC.shown(c.here, WeightPriceUnit.HUNDRED_GRAMS))
        // published per 100 g, shown per kilo
        val per100 = c.copy(here = BranchPriceRange("a", 4.9, 4.9), unitKilos = 0.1)
        assertEquals(49.0, per100.shown(per100.here, WeightPriceUnit.KILO).low, 1e-9)
    }
}
