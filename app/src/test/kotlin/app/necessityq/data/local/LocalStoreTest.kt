package app.necessityq.data.local

import app.necessityq.domain.Clock
import app.necessityq.domain.IdGenerator
import app.necessityq.domain.LineStatus
import app.necessityq.domain.Session
import app.necessityq.domain.ShoppingService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Single-device mode: what is written to the file comes back after a restart, and a bad file never crashes. */
class LocalStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private class FakeClock(var now: Long) : Clock { override fun nowMillis() = now }
    private class SeqIds : IdGenerator { private var n = 0; override fun newId() = "id${++n}" }

    private val hh = LocalStore.LOCAL_HOUSEHOLD
    private fun service(store: LocalStore) = ShoppingService(store.shopping, FakeClock(1_000), SeqIds())

    /** The store writes on a background thread; wait until [check] holds on the file. */
    private fun awaitFile(file: File, check: (String) -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (file.exists() && check(file.readText())) return
            Thread.sleep(25)
        }
        throw AssertionError("the state file was not written in time")
    }

    @Test fun `lines and purchases survive a restart`() = runBlocking {
        val file = File(tmp.root, "state.json")
        val first = LocalStore(file)
        val s = service(first)
        s.addFromText(hh, LocalStore.LOCAL_USER, "חלב 3%", emptyList())
        s.addFromText(hh, LocalStore.LOCAL_USER, "לחם", emptyList())
        val lines = first.shopping.lines(hh).first()
        s.checkOff(hh, LocalStore.LOCAL_USER, lines.first { it.name.contains("חלב") })
        awaitFile(file) { it.contains("BOUGHT") && it.contains("לחם") }

        val second = LocalStore(file)
        val restored = second.shopping.lines(hh).first()
        assertEquals(2, restored.size)
        assertEquals(LineStatus.BOUGHT, restored.first { it.name.contains("חלב") }.status)
        assertEquals(LineStatus.NEEDED, restored.first { it.name.contains("לחם") }.status)
        assertEquals(1, second.shopping.purchases(hh).first().size)
    }

    @Test fun `the household name is remembered and leaving forgets it`() = runBlocking {
        val file = File(tmp.root, "state.json")
        val store = LocalStore(file)
        store.household.createHousehold("  בית כהן  ")
        awaitFile(file) { it.contains("בית כהן") }

        val reopened = LocalStore(file).household.session.first() as Session.SignedIn
        assertEquals("בית כהן", reopened.household?.name)

        store.household.leaveHousehold()
        awaitFile(file) { !it.contains("בית כהן") }
        assertEquals(null, (LocalStore(file).household.session.first() as Session.SignedIn).household)
    }

    @Test fun `a blank household name gets the default`() = runBlocking {
        val store = LocalStore(File(tmp.root, "state.json"))
        assertEquals("הבית שלנו", store.household.createHousehold("   ").name)
    }

    @Test fun `a corrupted file starts an empty list instead of crashing`() = runBlocking {
        val file = File(tmp.root, "state.json").apply { writeText("{ not json") }
        val store = LocalStore(file)
        assertTrue(store.shopping.lines(hh).first().isEmpty())
        assertTrue(store.shopping.purchases(hh).first().isEmpty())
    }

    @Test fun `unknown values written by another version fall back to defaults`() = runBlocking {
        val file = File(tmp.root, "state.json").apply {
            writeText(
                """{"lines":[{"id":"l1","itemId":"milk","name":"חלב","category":"FUTURE_CATEGORY","status":"FUTURE_STATUS",
                |"source":"FUTURE_SOURCE","addedBy":"u","addedAt":1,"updatedAt":1,"someNewField":true}]}""".trimMargin(),
            )
        }
        val line = LocalStore(file).shopping.lines(hh).first().single()
        assertEquals("חלב", line.name)
        assertEquals(LineStatus.NEEDED, line.status)
    }

    @Test fun `a missing file is an empty list`() = runBlocking {
        val store = LocalStore(File(tmp.root, "does-not-exist.json"))
        assertTrue(store.shopping.lines(hh).first().isEmpty())
    }
}
