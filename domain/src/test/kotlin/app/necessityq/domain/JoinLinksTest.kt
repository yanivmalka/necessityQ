package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinLinksTest {
    @Test
    fun `reads the code from the app link and from the web page`() {
        assertEquals("AB12CD", JoinLinks.codeFrom("necessityq://join?code=ab12cd"))
        assertEquals("AB12CD", JoinLinks.codeFrom(JoinLinks.link("ab12cd")))
        assertEquals("AB12CD", JoinLinks.codeFrom("necessityq://join?x=1&code=AB12CD#top"))
    }

    @Test
    fun `other links and bad codes are not joins`() {
        assertNull(JoinLinks.codeFrom(null))
        assertNull(JoinLinks.codeFrom("https://example.com/join.html?code=AB12CD"))
        assertNull(JoinLinks.codeFrom("necessityq://join"))
        assertNull(JoinLinks.codeFrom("necessityq://join?code=<script>"))
    }

    @Test
    fun `the invitation carries the link and the code`() {
        val text = JoinLinks.shareText("הבית שלנו", "ab12cd")
        assertTrue(text.contains(JoinLinks.link("AB12CD")))
        assertTrue(text.contains("AB12CD"))
    }
}
