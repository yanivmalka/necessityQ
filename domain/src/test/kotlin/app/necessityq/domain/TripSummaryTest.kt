package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TripSummaryTest {
    private fun line(id: String, status: LineStatus, missing: Boolean = false) = ListLine(
        id = id, itemId = id, name = id, variant = null, category = Category.entries.first(),
        quantity = null, note = null, status = status, source = LineSource.MANUAL, addedBy = "u",
        addedAtMillis = 0, updatedAtMillis = 0, missing = missing,
    )

    @Test
    fun `counts bought, missing and not bought - removed lines are not counted`() {
        val lines = List(12) { line("b$it", LineStatus.BOUGHT) } +
            List(2) { line("m$it", LineStatus.NEEDED, missing = true) } +
            List(3) { line("n$it", LineStatus.NEEDED) } +
            line("r", LineStatus.REMOVED)
        val summary = TripSummary.of(lines)
        assertEquals(TripSummary(12, 2, 3), summary)
        assertEquals("נקנו 12 · 2 חסרים נשארים ברשימה · 3 לא נקנו", summary.text())
    }

    @Test
    fun `singular forms and empty parts`() {
        assertEquals("נקנה פריט אחד · חסר אחד נשאר ברשימה", TripSummary(1, 1, 0).text())
        assertEquals("אחד לא נקנה", TripSummary(0, 0, 1).text())
        assertEquals("הקנייה הסתיימה", TripSummary(0, 0, 0).text())
    }
}
