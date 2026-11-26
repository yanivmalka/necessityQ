package app.necessityq.ui

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.necessityq.AppContainer
import app.necessityq.domain.LineStatus
import app.necessityq.domain.ListLine
import app.necessityq.domain.ListSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The critical paths of the screen-state holder, on the JVM, in single-device mode (no Firebase): add an item,
 * check it off, undo, delete and restore, clear the bought ones. The state is read from the same streams
 * the screens use.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppViewModelTest {

    private lateinit var vm: AppViewModel
    private lateinit var collector: Job

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        runBlocking { container.household.createHousehold("הבית של הבדיקה") }
        vm = AppViewModel(container)
        // the streams are shared only while somebody collects them, as the screens do
        val scope = CoroutineScope(Dispatchers.Unconfined)
        collector = scope.launch {
            launch { vm.snapshot.collect {} }
            launch { vm.purchases.collect {} }
        }
        await("the household and the list to load") { vm.snapshot.value }
    }

    @After fun tearDown() {
        collector.cancel()
        Dispatchers.resetMain()
    }

    private fun <T : Any> await(what: String, read: () -> T?): T {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            read()?.let { return it }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        throw AssertionError("timed out waiting for $what")
    }

    private fun ListSnapshot.all(): List<ListLine> = (lines + removed).distinctBy { it.id }

    private fun line(named: String): ListLine? = vm.snapshot.value?.all()?.firstOrNull { it.name.contains(named) }

    private fun add(text: String): ListLine {
        vm.query.value = text
        vm.submitQuery()
        return await("\"$text\" on the list") { line(text.substringAfterLast(' ')) }
    }

    @Test fun `typing a name and submitting puts it on the list and clears the box`() {
        val milk = add("חלב")
        assertEquals(LineStatus.NEEDED, milk.status)
        assertEquals("", vm.query.value)
    }

    @Test fun `a blank box adds nothing`() {
        vm.query.value = "   "
        vm.submitQuery()
        assertTrue(vm.snapshot.value!!.lines.isEmpty())
    }

    @Test fun `checking off marks the line bought and records the purchase`() {
        val milk = add("חלב")
        vm.checkOff(milk)
        val bought = await("the line to be bought") { line("חלב")?.takeIf { it.status == LineStatus.BOUGHT } }
        assertEquals(LineStatus.BOUGHT, bought.status)
        await("the purchase") { vm.purchases.value.firstOrNull { it.lineId == milk.id } }
    }

    @Test fun `the undo that comes with a check-off reopens the line`() {
        val events = mutableListOf<AppViewModel.UndoEvent>()
        val listener = CoroutineScope(Dispatchers.Unconfined).launch { vm.undo.collect { events += it } }
        val milk = add("חלב")
        vm.checkOff(milk)
        await("the line to be bought") { line("חלב")?.takeIf { it.status == LineStatus.BOUGHT } }
        assertNotNull(events.lastOrNull())

        events.last().undo()

        await("the line to be open again") { line("חלב")?.takeIf { it.status == LineStatus.NEEDED } }
        listener.cancel()
    }

    @Test fun `deleting moves the line to the bin and restoring brings it back`() {
        val milk = add("חלב")
        vm.delete(milk)
        val removed = await("the line in the bin") { line("חלב")?.takeIf { it.status == LineStatus.REMOVED } }

        vm.restore(removed)

        await("the line back on the list") { line("חלב")?.takeIf { it.status == LineStatus.NEEDED } }
    }

    @Test fun `clearing the bought lines sends them to the bin and leaves the open ones`() {
        val milk = add("חלב")
        add("לחם")
        vm.checkOff(milk)
        await("the line to be bought") { line("חלב")?.takeIf { it.status == LineStatus.BOUGHT } }

        vm.clearBought()

        await("the bought line in the bin") { line("חלב")?.takeIf { it.status == LineStatus.REMOVED } }
        assertEquals(LineStatus.NEEDED, line("לחם")?.status)
    }

    @Test fun `a note is saved on the line`() {
        val milk = add("חלב")
        vm.changeNote(milk, "בקרטון")
        await("the note") { line("חלב")?.takeIf { it.note == "בקרטון" } }
    }
}
