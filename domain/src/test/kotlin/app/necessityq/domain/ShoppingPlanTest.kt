package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingPlanTest {
    private fun line(itemId: String, n: Double = 1.0) = ListLine(
        id = itemId, itemId = itemId, name = itemId, variant = null, category = Category.entries.first(),
        quantity = Quantity(n, QuantityUnit.PIECE), note = null,
        status = LineStatus.NEEDED, source = LineSource.MANUAL, addedBy = "u", addedAtMillis = 0, updatedAtMillis = 0,
    )
    private fun prod(code: String, price: Double) = BranchProduct(code, code, "", null, "", false, false, price, null, "", price, price)
    private fun info(branch: String, code: String, price: Double) = ItemPriceInfo(branch, price, price, listOf(prod(code, price)), null)
    private fun q(v: Double) = LineQuote(v, v)

    private val lines = listOf(line("a"), line("b"), line("c"))

    // A is the regular store; B is cheap on a, C is cheap on b
    private val quotes = mapOf(
        "A" to mapOf("a" to q(100.0), "b" to q(100.0), "c" to q(50.0)),
        "B" to mapOf("a" to q(40.0), "b" to q(110.0), "c" to q(55.0)),
        "C" to mapOf("a" to q(105.0), "b" to q(30.0), "c" to q(52.0)),
    )

    @Test
    fun staysAtTheRegularStoreBelowTheThresholdAndNamesTheBiggestGap() {
        val plan = ShoppingPlanner.plan(lines, quotes, "A", threshold = 500.0, maxStores = 3)!!
        assertEquals(TripAdvice.STAY, plan.advice)
        assertEquals(listOf("A"), plan.chosen.branchIds)
        // the biggest saving: all three stores, 250 → 40 + 30 + 50
        assertEquals(130.0, plan.biggestSaving, 0.001)
        assertTrue(plan.reasons.isEmpty())
    }

    @Test
    fun switchesToOneStoreWhenItSavesTheThreshold() {
        val plan = ShoppingPlanner.plan(lines, quotes, "A", threshold = 50.0, maxStores = 1)!!
        assertEquals(TripAdvice.SWITCH, plan.advice)
        // A 250, B 205, C 187
        assertEquals(listOf("C"), plan.chosen.branchIds)
        assertEquals(63.0, plan.saving, 0.001)
        assertEquals("b", plan.reasons.first().itemId)
    }

    @Test
    fun splitsWhenEveryExtraStopSavesTheThreshold() {
        val plan = ShoppingPlanner.plan(lines, quotes, "A", threshold = 50.0, maxStores = 2)!!
        assertEquals(TripAdvice.SPLIT, plan.advice)
        // B for a (40), C for b and c (30 + 52) = 122
        assertEquals(setOf("B", "C"), plan.chosen.branchIds.toSet())
        assertEquals(122.0, plan.chosen.total, 0.001)
        assertEquals("B", plan.assignment["a"])
        assertEquals("C", plan.assignment["b"])
        // at 70 a stop must earn 70: C alone (63) and B + C (128 < 140) don't, the regular store + C (70) does
        val strict = ShoppingPlanner.plan(lines, quotes, "A", threshold = 70.0, maxStores = 2)!!
        assertEquals(TripAdvice.SPLIT, strict.advice)
        assertEquals(listOf("A", "C"), strict.chosen.branchIds)
        assertEquals(listOf("b"), strict.reasons.map { it.itemId })
    }

    @Test
    fun comparesOnlyItemsPricedEverywhereButAssignsAll() {
        val partial = quotes + ("B" to quotes.getValue("B") - "c")
        val plan = ShoppingPlanner.plan(lines, partial, "A", threshold = 10.0, maxStores = 1)!!
        assertEquals(2, plan.compared)
        assertEquals(listOf("c"), plan.notCompared)
        assertTrue("c" in plan.assignment)
        assertNull(ShoppingPlanner.plan(emptyList(), quotes, "A", 10.0, 1))
    }

    @Test
    fun promotionLowersTheQuoteOnlyFromItsMinimumQuantity() {
        val promo = Promotion("1", "2 ב-10", setOf("x"), minQty = 2.0, discountedPrice = 10.0)
        val promos = BranchPromotions("A", 0, listOf(promo))
        val i = info("A", "x", 7.0)
        val one = ShoppingPlanner.quote(line("x", 1.0), i, promos, "2026-11-21T10:00:00", false)!!
        assertEquals(7.0, one.cost, 0.001)
        val three = ShoppingPlanner.quote(line("x", 3.0), i, promos, "2026-11-21T10:00:00", false)!!
        assertEquals(17.0, three.cost, 0.001)
        assertEquals(promo, three.promotion)
    }

    @Test
    fun offersSkipStockLimitedEndedAndMembersOnlyPromotions() {
        val now = "2026-11-21T10:00:00"
        val promos = BranchPromotions(
            "A", 0,
            listOf(
                Promotion("1", "20%", setOf("x"), discountRate = 0.2, stockLimited = true),
                Promotion("2", "ישן", setOf("x"), discountRate = 0.3, end = "2026-11-01T23:59:59"),
                Promotion("3", "מועדון", setOf("x"), discountRate = 0.4, clubOnly = true),
                Promotion("4", "10%", setOf("x"), discountRate = 0.1),
            ),
        )
        val infos = mapOf("A" to mapOf("x" to info("A", "x", 10.0)))
        val offers = PromoOffers.forList(listOf(line("x")), infos, mapOf("A" to promos), now, includeMembers = false)
        assertEquals(listOf("4"), offers.map { it.promotion.id })
        val members = PromoOffers.forList(listOf(line("x")), infos, mapOf("A" to promos), now, includeMembers = true)
        assertEquals(listOf("3"), members.map { it.promotion.id })
        assertEquals(6.0, members.single().promoUnit, 0.001)
    }

    @Test
    fun unitPriceReadsBothPublishingHabits() {
        assertEquals(5.0, Promotion("1", "", setOf(), minQty = 2.0, discountedPrice = 10.0).unitPrice(7.0)!!, 0.001)
        // "2 ב-…" published per unit: 5 for 2 of a 12 product would be 2.5 — read as 5 a unit
        assertEquals(5.0, Promotion("1", "", setOf(), minQty = 2.0, discountedPrice = 5.0).unitPrice(20.0)!!, 0.001)
        assertNull(Promotion("1", "", setOf(), discountedPrice = 9.0).unitPrice(8.0))
    }
}
