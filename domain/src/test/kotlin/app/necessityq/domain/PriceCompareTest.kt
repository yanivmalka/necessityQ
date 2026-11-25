package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceCompareTest {
    private fun p(code: String, name: String, price: Double, internal: Boolean = false, unit: String = "", weighted: Boolean = false) =
        BranchProduct(code, name, "", null, unit, weighted, internal, price, null, "2026-11-01", price, price)

    private fun info(branch: String, vararg products: BranchProduct, preferred: BranchProduct? = null) =
        ItemPriceInfo(branch, products.minOf { it.price }, products.maxOf { it.price }, products.sortedBy { it.price }, preferred)

    @Test
    fun `same barcode dearer than the other branches`() {
        val c = PriceComparisons.product(p("729", "קוטג' 5%", 10.0), "a", mapOf("b" to mapOf("729" to p("729", "קוטג' 5%", 8.0))))!!
        assertEquals(0.25, c.diff, 1e-9)
        assertEquals(PriceLevel.EXPENSIVE, c.level)
        assertEquals("יקר ב-25% מהסניפים שלך", c.label)
        assertEquals("▲ 25%", c.shortLabel)
        assertEquals("b", c.cheapestBranchId)
    }

    @Test
    fun `no comparison when no other branch sells it or the code is the chain's own`() {
        assertNull(PriceComparisons.product(p("729", "x", 10.0), "a", mapOf("b" to emptyMap())))
        val internal = p("12", "x", 10.0, internal = true)
        assertNull(PriceComparisons.product(internal, "a", mapOf("b" to mapOf("12" to p("12", "x", 5.0)))))
    }

    @Test
    fun `the gap in shekels decides from which amount a comparison is shown`() {
        val c = PriceComparisons.product(p("729", "x", 10.0), "a", mapOf("b" to mapOf("729" to p("729", "x", 8.0))))!!
        assertEquals(2.0, c.amountGap, 1e-9)
        assertTrue(c.reaches(0))
        assertTrue(c.reaches(2))
        assertTrue(!c.reaches(3))
    }

    @Test
    fun `a small gap is about the same`() {
        val c = PriceComparisons.product(p("729", "x", 10.4), "a", mapOf("b" to mapOf("729" to p("729", "x", 10.0))))!!
        assertEquals(PriceLevel.FAIR, c.level)
        assertTrue(!c.notable)
    }

    @Test
    fun `item compared by the products sold at both branches`() {
        val here = info("a", p("1", "קוטג'", 6.0), p("2", "קוטג'", 12.0), p("3", "קוטג'", 30.0))
        val other = info("b", p("1", "קוטג'", 5.0), p("2", "קוטג'", 10.0), p("4", "קוטג'", 3.0))
        val c = PriceComparisons.item(here, mapOf("b" to other))!!
        assertEquals(CompareBasis.SHARED_PRODUCTS, c.basis)
        assertEquals(0.2, c.diff, 1e-9)
        assertEquals(PriceLevel.EXPENSIVE, c.level)
    }

    @Test
    fun `item compared by typical prices when no product is shared`() {
        val here = info("a", p("1", "x", 5.0), p("2", "x", 6.0), p("3", "x", 7.0))
        val other = info("b", p("4", "x", 6.0), p("5", "x", 7.5), p("6", "x", 9.0))
        val c = PriceComparisons.item(here, mapOf("b" to other))!!
        assertEquals(CompareBasis.TYPICAL, c.basis)
        assertEquals(6.0 / 7.5 - 1, c.diff, 1e-9)
        assertEquals(PriceLevel.CHEAP, c.level)
        assertEquals("זול ב-20% מהסניפים שלך", c.label)
        // too few products on one side: not compared
        assertNull(PriceComparisons.item(here, mapOf("b" to info("b", p("4", "x", 9.0)))))
    }

    @Test
    fun `the chosen product is compared by its barcode`() {
        val chosen = p("729", "קוטג' 5% תנובה", 8.0)
        val here = info("a", chosen, p("1", "קוטג' 5%", 5.0), preferred = chosen)
        val c = PriceComparisons.item(here, mapOf("b" to info("b", p("2", "קוטג' 5%", 5.0))), mapOf("b" to mapOf("729" to p("729", "קוטג' 5% תנובה", 10.0))))!!
        assertEquals(CompareBasis.SAME_PRODUCT, c.basis)
        assertEquals(-0.2, c.diff, 1e-9)
    }

    @Test
    fun `median over several branches`() {
        val here = info("a", p("1", "x", 10.0))
        val others = mapOf(
            "b" to info("b", p("1", "x", 5.0)),
            "c" to info("c", p("1", "x", 10.0)),
            "d" to info("d", p("1", "x", 10.0)),
        )
        val c = PriceComparisons.item(here, others)!!
        assertEquals(0.0, c.diff, 1e-9)
        assertEquals(listOf("b", "c", "d"), c.others.map { it.branchId })
    }

    @Test
    fun `search across branches merges by barcode`() {
        val a = listOf(p("729", "קוטג' 5%", 6.0), p("12", "קוטג' 5% במשקל", 4.0, internal = true))
        val b = listOf(p("729", "קוטג' 5%", 5.0), p("730", "קוטג' 5% לייט", 7.0))
        val branches = listOf("a" to a, "b" to b)
        val codes = mapOf("a" to PriceComparisons.byCode(a), "b" to PriceComparisons.byCode(b))
        val found = ProductSearch.across(branches, codes, "קוטג 5%")
        assertEquals(listOf("729", "730", "12"), found.map { it.product.code })
        val cottage = found.first()
        assertEquals(setOf("a", "b"), cottage.at.keys)
        assertEquals(setOf("b"), cottage.cheapestBranchIds)
        assertEquals(0.2, cottage.spread, 1e-9)
        assertEquals(setOf("a"), found.last().at.keys)
    }
}
