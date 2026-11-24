package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanReminderTest {
    @Test
    fun `round-trips and reads the older five-field lines`() {
        val r = ScanReminder("729000", "חלב\tתנובה", 6.9, "b1", 1000L, "חלב 3%")
        assertEquals(r.copy(name = "חלב תנובה"), ScanReminder.decode(r.encode()))
        val old = ScanReminder.decode("729000\tקוטג' 5%\t\t\t42")!!
        assertNull(old.price)
        assertNull(old.branchId)
        assertNull(old.itemName)
        assertEquals("קוטג' 5%", old.listName)
        assertNull(ScanReminder.decode("garbage"))
    }

    @Test
    fun `while shopping only the branch's reminders are shown`() {
        val a = ScanReminder("1", "a", null, "b1", 0)
        val b = ScanReminder("2", "b", null, "b2", 0)
        val c = ScanReminder("3", "c", null, null, 0)
        assertEquals(listOf(a, b, c), ScanReminder.shown(listOf(a, b, c), shopping = false, branchId = "b1"))
        assertEquals(listOf(a), ScanReminder.shown(listOf(a, b, c), shopping = true, branchId = "b1"))
        assertEquals(emptyList<ScanReminder>(), ScanReminder.shown(listOf(a, b, c), shopping = true, branchId = null))
    }
}
