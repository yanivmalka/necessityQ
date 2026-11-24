package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NameListTest {
    @Test
    fun `names the items in a sentence`() {
        assertEquals("", HebrewText.nameList(emptyList()))
        assertEquals("חלב", HebrewText.nameList(listOf("חלב")))
        assertEquals("חלב וקוטג'", HebrewText.nameList(listOf("חלב", "קוטג'")))
        assertEquals("חלב, קוטג' ולחם", HebrewText.nameList(listOf("חלב", "קוטג'", "לחם")))
        // one past the limit is still named, rather than "ועוד 1"
        assertEquals("חלב, קוטג', לחם וביצים", HebrewText.nameList(listOf("חלב", "קוטג'", "לחם", "ביצים")))
        assertEquals("חלב, קוטג', לחם ועוד 3", HebrewText.nameList(listOf("חלב", "קוטג'", "לחם", "ביצים", "גבינה", "עגבניות")))
    }
}
