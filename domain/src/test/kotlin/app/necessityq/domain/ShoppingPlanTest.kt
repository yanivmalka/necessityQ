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
                Promotion("1b", "70% עד גמר המלאי", setOf("x"), discountRate = 0.7, stockLimited = true, clubOnly = true),
                Promotion("2", "ישן", setOf("x"), discountRate = 0.3, end = "2026-11-01T23:59:59"),
                Promotion("3", "מועדון", setOf("x"), discountRate = 0.4, clubOnly = true),
                Promotion("5", "עוד לא התחיל", setOf("x"), discountRate = 0.5, start = "2026-12-01T00:00:00"),
                Promotion("6", "על כל הסל", setOf("x"), discountRate = 0.5, wholeBasket = true),
                // on a product that is not in the branch's price file now (out of stock)
                Promotion("7", "אזל", setOf("y"), discountRate = 0.6),
                Promotion("4", "10%", setOf("x"), discountRate = 0.1),
            ),
        )
        val infos = mapOf("A" to mapOf("x" to info("A", "x", 10.0)))
        // stock-dependent: offered, with its warning (the best for everyone here is 20%)
        val offers = PromoOffers.forList(listOf(line("x")), infos, mapOf("A" to promos), now, includeMembers = false)
        assertEquals(listOf("1"), offers.map { it.promotion.id })
        assertEquals("עד גמר המלאי — ייתכן שייגמר", offers.single().promotion.stockWarning)
        val members = PromoOffers.forList(listOf(line("x")), infos, mapOf("A" to promos), now, includeMembers = true)
        assertEquals(listOf("1b"), members.map { it.promotion.id })
        assertEquals(3.0, members.single().promoUnit, 0.001)
        // …but a plan never counts it: the quote takes the 10% that holds for everyone
        val quote = ShoppingPlanner.quote(line("x"), info("A", "x", 10.0), promos, now, includeMembers = false)!!
        assertEquals(9.0, quote.cost, 0.001)
    }

    @Test
    fun unitPriceReadsBothPublishingHabits() {
        assertEquals(5.0, Promotion("1", "", setOf(), minQty = 2.0, discountedPrice = 10.0).unitPrice(7.0)!!, 0.001)
        // "2 ב-…" published per unit: 5 for 2 of a 12 product would be 2.5 — read as 5 a unit
        assertEquals(5.0, Promotion("1", "", setOf(), minQty = 2.0, discountedPrice = 5.0).unitPrice(20.0)!!, 0.001)
        assertNull(Promotion("1", "", setOf(), discountedPrice = 9.0).unitPrice(8.0))
    }

    @Test
    fun wholeBasketPromotionTakesItsDiscountWhenTheStopReachesTheMinimum() {
        val off10 = Promotion("b", "10 ₪ הנחה מעל 200", emptySet(), discountedPrice = 10.0, minBasket = 200.0, wholeBasket = true)
        // B is 205 (reaches 200: 195 with the discount), C 187 (under 200: no discount)
        val plan = ShoppingPlanner.plan(lines, quotes, "A", threshold = 50.0, maxStores = 1, basket = mapOf("B" to listOf(off10)))!!
        val b = plan.singles.first { it.branchIds == listOf("B") }.stops.single()
        assertEquals(205.0, b.itemsTotal, 0.001)
        assertEquals(10.0, b.basketDiscount, 0.001)
        assertEquals(195.0, b.total, 0.001)
        assertEquals(0.0, plan.singles.first { it.branchIds == listOf("C") }.stops.single().basketDiscount, 0.001)
        // a rate, and the minimum
        val pct = Promotion("p", "5%", emptySet(), discountRate = 0.05, wholeBasket = true)
        assertEquals(10.0, pct.basketDiscount(200.0)!!, 0.001)
        assertNull(off10.basketDiscount(150.0))
        assertNull(Promotion("i", "", setOf("x"), discountedPrice = 10.0).basketDiscount(500.0))
        // members only, and only running ones
        val now = "2026-11-21T10:00:00"
        val bp = BranchPromotions("B", 0, listOf(off10, off10.copy(id = "c", coupon = true), off10.copy(id = "old", end = "2026-01-01T00:00:00")))
        assertEquals(listOf("b"), PromoOffers.basket(bp, now, includeMembers = false).map { it.id })
        assertEquals(listOf("b", "c"), PromoOffers.basket(bp, now, includeMembers = true).map { it.id })
    }
}
