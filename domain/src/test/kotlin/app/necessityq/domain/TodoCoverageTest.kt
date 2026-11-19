package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TodoCoverageTest {
    private val zone = ZoneOffset.UTC
    private val engine = ConsumptionStatsEngine(zone)
    private val start = LocalDate.of(2026, 1, 4)
    private var n = 0

    private fun purchase(item: String, day: LocalDate, hour: Int = 20, todo: Boolean = true): Purchase {
        val identity = ItemIdentity.fromText(item)
        return Purchase(
            id = (if (todo) Purchase.TODO_PURCHASE_PREFIX else "p_app_") + (n++),
            itemId = identity.id,
            name = identity.name,
            variant = identity.variant,
            category = identity.category,
            quantity = Quantity.pieces(1),
            boughtAtMillis = day.atTime(hour, 0).toInstant(zone).toEpochMilli(),
            boughtBy = "u1",
            lineId = null,
        )
    }

    @Test fun `no To Do history means no coverage`() {
        assertNull(TodoCoverage.of(listOf(purchase("חלב", start, todo = false)), start, zone))
    }

    @Test fun `after-midnight and next-day check-offs belong to the same trip`() {
        val purchases = listOf(
            purchase("חלב", start, hour = 21),
            purchase("לחם", start.plusDays(1), hour = 0), // synced after leaving the store
            purchase("ביצים", start.plusDays(1), hour = 12), // synced the next day
        )
        val coverage = TodoCoverage.of(purchases, start.plusDays(2), zone)!!
        assertEquals(listOf(start), coverage.trips)
        purchases.forEach { assertEquals(start, coverage.tripOf(it)) }
    }

    @Test fun `a week without records means an undocumented trip`() {
        val purchases = listOf(0L, 7L, 21L, 28L).map { purchase("חלב", start.plusDays(it)) }
        val coverage = TodoCoverage.of(purchases, start.plusDays(28), zone)!!
        assertEquals(listOf(TodoCoverage.Gap(start.plusDays(7), start.plusDays(21), 1)), coverage.gaps)
        assertEquals(7.0, coverage.tripIntervalDays, 0.01)
    }

    @Test fun `ten days is still one trip interval`() {
        val purchases = listOf(0L, 10L, 17L).map { purchase("חלב", start.plusDays(it)) }
        assertTrue(TodoCoverage.of(purchases, start.plusDays(17), zone)!!.gaps.isEmpty())
    }

    @Test fun `intervals spanning an undocumented trip are not taken as the cadence`() {
        // weekly milk, but the records of weeks 2, 5 and 6 are missing
        val days = listOf(0L, 7L, 21L, 28L, 49L, 56L)
        val purchases = days.map { purchase("חלב", start.plusDays(it)) }
        val stats = engine.compute(purchases, start.plusDays(56)).values.single()
        assertEquals(7.0, stats.cadenceDays!!, 0.01)
    }

    @Test fun `cadence falls back to the share of documented trips`() {
        // weekly bread, but the trip of day 7 was not documented (0 -> 14 = one undocumented trip)
        val bread = (0L..70L step 7).filter { it != 7L }.map { purchase("לחם", start.plusDays(it)) }
        // oil was documented in the first and the last trip only; their interval spans the gap
        val oil0 = listOf(purchase("שמן", start), purchase("שמן", start.plusDays(70)))
        val stats = engine.compute(bread + oil0, start.plusDays(70))
        val oil = stats[ItemIdentity.fromText("שמן").id]!!
        assertNotNull(oil.cadenceDays)
        // 1 repeat over the 9 documented trips after the first, at ~7 days per real trip
        assertEquals(63.0, oil.cadenceDays!!, 0.5)
    }

    @Test fun `last purchase before undocumented weeks is not reported as long overdue`() {
        // weekly milk documented in To Do until day 28, then To Do stopped; today is day 90
        val purchases = (0L..28L step 7).map { purchase("חלב", start.plusDays(it)) }
        val today = start.plusDays(90)
        val stats = engine.compute(purchases, today).values.single()
        assertEquals(start.plusDays(28), stats.lastPurchase)
        assertTrue(stats.lastPurchaseEstimated)
        assertFalse("must not be overdue", stats.nextDue!!.isBefore(today))
        assertTrue(stats.dueRatio(today)!! <= 1.0)
        assertTrue(stats.daysSinceLast(today) < 7)
    }

    @Test fun `once the app records again, its purchases end the undocumented stretch`() {
        val todo = (0L..28L step 7).map { purchase("חלב", start.plusDays(it)) }
        val app = listOf(purchase("לחם", start.plusDays(70), todo = false))
        val today = start.plusDays(84)
        val stats = engine.compute(todo + app, today)
        val milk = stats[ItemIdentity.fromText("חלב").id]!!
        // milk was presumably bought in the undocumented trips up to day 70, but not on the app trips since
        assertTrue(milk.lastPurchaseEstimated)
        assertTrue(milk.nextDue!! in start.plusDays(70)..start.plusDays(77))
        assertTrue("due by now", milk.dueRatio(today)!! >= 1.0)
        val reason = SuggestionEngine.runningLowReason(milk.cadenceDays!!, milk.daysSinceLast(today), milk.lastPurchaseEstimated)
        assertTrue(reason, reason.contains("לא תועד"))
    }

    @Test fun `purchases recorded in the app are unaffected`() {
        val purchases = (0L..42L step 14).map { purchase("קמח", start.plusDays(it), todo = false) }
        val stats = engine.compute(purchases, start.plusDays(42)).values.single()
        assertEquals(14.0, stats.cadenceDays!!, 0.01)
        assertFalse(stats.lastPurchaseEstimated)
    }
}
