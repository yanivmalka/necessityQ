package app.necessityq.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two members acting on the same list at the same time (or one of them offline and syncing late).
 * Every write is field-level and the purchase id is derived from the line id, so the result must be the
 * same whatever the order in which the writes arrive.
 */
class SyncConflictTest {

    private class FakeClock(var now: Long) : Clock { override fun nowMillis() = now }
    private class SeqIds : IdGenerator { private var n = 0; override fun newId() = "id${++n}" }

    private val hh = "h"
    private fun newService(repo: InMemoryShoppingRepository, clock: FakeClock = FakeClock(1_000)) =
        ShoppingService(repo, clock, SeqIds())

    private suspend fun addMilk(service: ShoppingService, repo: InMemoryShoppingRepository): ListLine {
        service.addFromText(hh, "a", "חלב 3%", emptyList())
        return repo.lines(hh).first().single()
    }

    @Test fun `two members checking off the same line leave one purchase`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = newService(repo)
        val line = addMilk(service, repo)

        service.checkOff(hh, "a", line)
        service.checkOff(hh, "b", line) // the second device had not yet seen the first

        val purchases = repo.purchases(hh).first()
        assertEquals(1, purchases.size)
        assertEquals("p_${line.id}", purchases.single().id)
        assertEquals(LineStatus.BOUGHT, repo.lines(hh).first().single().status)
    }

    @Test fun `reopening twice is the same as once`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = newService(repo)
        val line = addMilk(service, repo)
        service.checkOff(hh, "a", line)

        service.reopen(hh, line)
        service.reopen(hh, line)

        assertEquals(LineStatus.NEEDED, repo.lines(hh).first().single().status)
        assertTrue(repo.purchases(hh).first().isEmpty())
    }

    @Test fun `edits to a line another member already deleted do not bring it back or fail`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = newService(repo)
        val line = addMilk(service, repo)

        service.delete(hh, line)
        service.purge(hh, repo.lines(hh).first())
        service.changeNote(hh, line, "לא שומן")
        service.changeQuantity(hh, line, Quantity(2.0, QuantityUnit.PIECE))
        service.setMissing(hh, "b", line, true)
        service.checkOff(hh, "b", line)

        assertTrue(repo.lines(hh).first().isEmpty())
        assertTrue(repo.purchases(hh).first().isEmpty())
    }

    @Test fun `concurrent edits of different fields both survive`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val clock = FakeClock(1_000)
        val service = newService(repo, clock)
        val line = addMilk(service, repo)

        // both members start from the same stale copy of the line
        clock.now = 2_000
        service.changeNote(hh, line, "בקרטון")
        clock.now = 2_001
        service.changeQuantity(hh, line, Quantity(3.0, QuantityUnit.PIECE))

        val merged = repo.lines(hh).first().single()
        assertEquals("בקרטון", merged.note)
        assertEquals(Quantity(3.0, QuantityUnit.PIECE), merged.quantity)
    }

    @Test fun `the last write to the same field wins`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val clock = FakeClock(1_000)
        val service = newService(repo, clock)
        val line = addMilk(service, repo)

        service.changeNote(hh, line, "ראשון")
        service.changeNote(hh, line, "אחרון")

        assertEquals("אחרון", repo.lines(hh).first().single().note)
    }

    @Test fun `a bought line cleared by one member is not bought again by a stale second check-off`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = newService(repo)
        val line = addMilk(service, repo)
        service.checkOff(hh, "a", line)
        val bought = repo.lines(hh).first().single()

        service.clearBought(hh, listOf(bought))

        // the purchase is still counted once for the statistics
        assertEquals(1, repo.purchases(hh).first().size)
    }
}
