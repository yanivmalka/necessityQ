package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What is read locally, what is sent to be read, and how a reading becomes list items. */
class QuickAddIntentTest {
    @Test
    fun `known items, amounts and kinds are read locally`() {
        for (text in listOf("חלב", "2 קוטג 5%", "פלפלים בכל מיני צבעים", "חצי קילו גבינה צהובה", "שוקיים", "עגבניה", "3")) {
            assertFalse(text, QuickAddIntent.needsReading(text, emptyList()))
        }
    }

    @Test
    fun `purposes, unknown products and misspellings are sent to be read`() {
        for (text in listOf("משהו לכריכים לילדים", "מה שצריך לשקשוקה", "חלב סויה", "חלבב", "אננס")) {
            assertTrue(text, QuickAddIntent.needsReading(text, emptyList()))
        }
    }

    @Test
    fun `what the household already buys is known`() {
        assertFalse(QuickAddIntent.needsReading("חלב סויה", listOf(ItemIdentity.fromText("חלב סויה"))))
    }

    @Test
    fun `a reading becomes list items with the list's spelling, category and amount`() {
        val items = QuickAddIntent.proposals(
            listOf(
                InterpretedItem("עגבניה", null, null, null, "PRODUCE"),
                InterpretedItem("אננס", null, 1.0, "piece", "PRODUCE"),
                InterpretedItem("מים מינרליים", null, 6.0, "liter", "DRINKS"),
                InterpretedItem("פלפל", "אדום", null, null, "PRODUCE"),
                InterpretedItem("עגבניות", null, null, null, "PRODUCE"),
                InterpretedItem("  ", null, null, null, "OTHER"),
            ),
        )
        assertEquals(listOf("עגבניות", "אננס", "מים מינרליים", "פלפל אדום"), items.map { it.identity.displayName })
        assertEquals(Category.PRODUCE, items[1].identity.category)
        assertEquals(Quantity(6.0, QuantityUnit.LITER), items[2].quantity)
        assertEquals(null, items[0].quantity)
    }
}
