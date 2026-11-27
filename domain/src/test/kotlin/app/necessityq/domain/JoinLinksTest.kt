package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinLinksTest {
    private val token = "Ab3dE5gH7jK9mN2pQ4rS6tUv"

    @Test
    fun `reads the invitation from the app link and from the web page`() {
        assertEquals(token, JoinLinks.tokenFrom("necessityq://join?invite=$token"))
        assertEquals(token, JoinLinks.tokenFrom(JoinLinks.link(token)))
        assertEquals(token, JoinLinks.tokenFrom("necessityq://join?x=1&invite=$token#top"))
    }

    @Test
    fun `other links and bad tokens are not joins`() {
        assertNull(JoinLinks.tokenFrom(null))
        assertNull(JoinLinks.tokenFrom("https://example.com/join.html?invite=$token"))
        assertNull(JoinLinks.tokenFrom("necessityq://join"))
        assertNull(JoinLinks.tokenFrom("necessityq://join?invite=<script>"))
        // the old short code is no longer a way in
        assertNull(JoinLinks.tokenFrom("necessityq://join?code=AB12CD"))
        assertNull(JoinLinks.tokenFrom("necessityq://join?invite=AB12CD"))
    }

    @Test
    fun `the invitation text carries only the link`() {
        val text = JoinLinks.shareText("הבית שלנו", token)
        assertTrue(text.contains(JoinLinks.link(token)))
        assertFalse(text.contains("קוד"))
    }
}
