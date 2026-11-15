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

    @Test fun `import loads the list and the history, and is idempotent`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = service(repo)

        val first = service.importTodoSample("h", "u", emptyList())
        assertEquals(27 to 23, first)
        assertEquals(27, repo.current.value.lines.size)
        assertEquals(23, repo.current.value.purchases.size)

        val again = service.importTodoSample("h", "u", service.snapshot("h").first().lines)
        assertEquals(0 to 23, again)
        assertEquals(27, repo.current.value.lines.size)
        assertEquals(23, repo.current.value.purchases.size)
    }

    @Test fun `imported history feeds the statistics and quick-add chips`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = service(repo)
        service.importTodoSample("h", "u", emptyList())

        val snap = service.snapshot("h").first()
        assertEquals(23, snap.stats.size)
        // each item was bought on one trip only, so there is no cadence yet
        assertTrue(snap.stats.values.all { it.cadenceDays == null })
        // quick-add chips exclude what is already on the list (קוטג', בייגלה) and are capped
        val chips = snap.frequent.map { it.identity.name }
        assertTrue("חלב" in chips || "גבינה צהובה" in chips)
        assertTrue("קוטג'" !in chips && "בייגלה" !in chips)
        assertEquals(12, chips.size)
    }

    @Test fun `milk bought earlier merges with a typed entry of the same item`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = service(repo)
        service.importTodoSample("h", "u", emptyList())
        val cottageId = ItemIdentity.fromText("קוטג' תנובה").id
        assertTrue(repo.current.value.purchases.values.any { it.itemId == cottageId })
        assertTrue(repo.current.value.lines.values.any { it.itemId == cottageId && it.quantity == Quantity.pieces(2) })
    }
}
