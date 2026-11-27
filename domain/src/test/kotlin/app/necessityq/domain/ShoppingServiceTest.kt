package app.necessityq.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ShoppingServiceTest {

    private class FakeClock(var now: Long) : Clock {
        override fun nowMillis() = now
    }

    private class SeqIds : IdGenerator {
        private var n = 0
        override fun newId() = "id${++n}"
    }

    private val zone = ZoneOffset.UTC
    private val day0 = LocalDate.of(2026, 3, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    private val dayMs = 24L * 60 * 60 * 1000

    @Test fun `full loop - add, merge, check off, learn cadence, suggest`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val clock = FakeClock(day0)
        val service = ShoppingService(repo, clock, SeqIds(), zone)
        val hh = "h"

        // four weekly trips with milk
        repeat(4) { week ->
            clock.now = day0 + week * 7 * dayMs
            val lines = service.snapshot(hh).first().lines
            service.addFromText(hh, "u1", "חלב תנובה 3%", lines)
            val line = service.snapshot(hh).first().grouped.flatMap { it.second }.single()
            service.checkOff(hh, "u1", line)
            service.clearBought(hh, service.snapshot(hh).first().lines)
        }

        clock.now = day0 + 27 * dayMs // 6 days after the last trip
        val snap = service.snapshot(hh).first()
        val id = ItemIdentity.fromText("חלב 3%").id
        assertEquals(7.0, snap.stats.getValue(id).cadenceDays!!, 0.01)
        assertEquals(listOf(id), snap.runningLow.map { it.itemId })

        // accepting adds it; adding again merges instead of duplicating
        service.acceptSuggestion(hh, "u2", snap.runningLow.single(), snap.lines)
        val afterAccept = service.snapshot(hh).first()
        service.addFromText(hh, "u2", "2 חלב 3%", afterAccept.lines)
        val needed = service.snapshot(hh).first().grouped.flatMap { it.second }
        assertEquals(1, needed.size)
        assertEquals(LineSource.SUGGESTED, needed.single().source)
        assertTrue(service.snapshot(hh).first().runningLow.isEmpty())
    }

    @Test fun `deleted item does not count in statistics or purchases, cleared one does`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val clock = FakeClock(day0)
        val service = ShoppingService(repo, clock, SeqIds(), zone)
        val hh = "h"
        service.addFromText(hh, "u", "חלב 3%", service.snapshot(hh).first().lines)
        service.addFromText(hh, "u", "לחם", service.snapshot(hh).first().lines)
        val lines = service.snapshot(hh).first().grouped.flatMap { it.second }
        val milk = lines.first { it.name.contains("חלב") }
        val bread = lines.first { it.name.contains("לחם") }
        service.checkOff(hh, "u", milk)
        service.checkOff(hh, "u", bread)

        val bought = service.snapshot(hh).first().lines.filter { it.status == LineStatus.BOUGHT }
        service.delete(hh, bought.first { it.id == milk.id })        // deleted: must not count
        service.clearBought(hh, listOf(bought.first { it.id == bread.id })) // cleared after buying: counts

        val snap = service.snapshot(hh).first()
        assertEquals(setOf(bread.itemId), snap.stats.keys)
        assertEquals(listOf(bread.itemId), service.purchases(hh).first().map { it.itemId })
        assertEquals(2, snap.removed.size) // both are in the bin, under "removed"
    }

    @Test fun `check off then reopen removes the purchase`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = ShoppingService(repo, FakeClock(day0), SeqIds(), zone)
        service.addFromText("h", "u1", "10 עגבניות", emptyList())
        val line = service.snapshot("h").first().lines.single()
        service.checkOff("h", "u1", line)
        service.checkOff("h", "u2", line) // a second member taps at the same time: still one purchase
        assertEquals(1, repo.current.value.purchases.size)
        service.reopen("h", line)
        assertTrue(repo.current.value.purchases.isEmpty())
        assertEquals(LineStatus.NEEDED, repo.current.value.lines.getValue(line.id).status)
    }

    @Test fun `an absurd amount for a home is added as one`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = ShoppingService(repo, FakeClock(day0), SeqIds(), zone)
        service.addFromText("h", "u1", "300 קוטג'", emptyList())
        service.addFromText("h", "u1", "12 ביצים", emptyList())
        service.addFromText("h", "u1", "750 גרם גבינה צהובה", emptyList())
        val lines = service.snapshot("h").first().lines.associateBy { it.name }
        assertEquals(Quantity.pieces(1), lines.getValue("קוטג'").quantity)
        assertEquals(Quantity.pieces(12), lines.getValue("ביצים").quantity)
        assertEquals(Quantity(750.0, QuantityUnit.GRAM), lines.getValue("גבינה צהובה").quantity)
    }

    @Test fun `rename changes the identity and keeps the quantity`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = ShoppingService(repo, FakeClock(day0), SeqIds(), zone)
        service.addFromText("h", "u1", "2 קוטג 5%", emptyList())
        val line = service.snapshot("h").first().lines.single()
        val renamed = service.rename("h", line, "קוטג 9%")!!
        val after = service.snapshot("h").first().lines.single()
        assertEquals(renamed.id, after.itemId)
        assertEquals("9%", after.variant)
        assertEquals(line.quantity, after.quantity)
        assertEquals(null, service.rename("h", after, "קוטג' 9%"))
    }
}
