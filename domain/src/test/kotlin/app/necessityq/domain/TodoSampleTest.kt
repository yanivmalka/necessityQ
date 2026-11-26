package app.necessityq.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TodoSampleTest {
    private val zone = ZoneOffset.UTC
    private val now = LocalDate.of(2026, 11, 16).atStartOfDay(zone).toInstant().toEpochMilli()

    private class Ids : IdGenerator {
        private var n = 0
        override fun newId() = "id${++n}"
    }

    private fun service(repo: InMemoryShoppingRepository) =
        ShoppingService(repo, object : Clock { override fun nowMillis() = now }, Ids(), zone)

    @Test fun `resource is bundled and big enough to give real statistics`() {
        assertTrue(TodoSample.purchases.size > 1400)
        assertTrue(TodoSample.openLines.size in 30..60)
    }

    @Test fun `import is idempotent`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = service(repo)
        val (lines, purchases) = service.importTodoSample("h", "u", emptyList())
        assertTrue(lines > 20)
        assertTrue(purchases > 1400)
        val before = repo.current.value
        val again = service.importTodoSample("h", "u", service.snapshot("h").first().lines)
        assertEquals(0, again.first)
        assertEquals(before.lines.size, repo.current.value.lines.size)
        assertEquals(before.purchases.size, repo.current.value.purchases.size)
    }

    @Test fun `history yields routine cadences and suggestions`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = service(repo)
        service.importTodoSample("h", "u", emptyList())
        val snap = service.snapshot("h").first()
        val milk = snap.stats[ItemIdentity.fromText("חלב").id]
        assertTrue("milk should have a cadence", milk?.cadenceDays != null)
        assertTrue(snap.stats.values.count { it.cadenceDays != null } > 40)
        assertEquals(12, snap.frequent.size)
    }

    @Test fun `undocumented weeks are inferred and nothing looks months overdue`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = service(repo)
        service.importTodoSample("h", "u", emptyList())
        val today = LocalDate.of(2026, 11, 16)
        val coverage = TodoCoverage.of(repo.current.value.purchases.values.toList(), today, zone)!!
        assertTrue("weekly shopping with gaps in the records", coverage.missingTrips >= 10)
        assertTrue(coverage.tripIntervalDays in 5.0..8.0)
        val snap = service.snapshot("h").first()
        val stale = snap.stats.values.filter { it.nextDue?.isBefore(today.minusDays(30)) == true }
        assertTrue("overdue by more than a month: ${stale.map { it.identity.displayName }}", stale.isEmpty())
    }

    @Test fun `resetting the history leaves the house with no memory but keeps the list`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = service(repo)
        service.importTodoSample("h", "u", emptyList())
        val before = service.snapshot("h").first()
        assertTrue(repo.purchases("h").first().isNotEmpty())
        val open = before.lines.count { it.status == LineStatus.NEEDED }
        val result = service.resetHistory("h")
        assertTrue(result.purchases > 0)
        assertTrue(repo.purchases("h").first().isEmpty())
        assertTrue(repo.suppressions("h").first().isEmpty())
        assertTrue(service.snapshot("h").first().stats.isEmpty())
        assertEquals(open, service.snapshot("h").first().lines.count { it.status == LineStatus.NEEDED })
    }
}

