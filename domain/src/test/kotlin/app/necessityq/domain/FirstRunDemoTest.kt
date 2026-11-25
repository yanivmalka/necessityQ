package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FirstRunDemoTest {
    @Test
    fun `only the listed accounts may replay the first run`() {
        assertFalse(FirstRunDemo.allowed(null))
        assertFalse(FirstRunDemo.allowed(""))
        assertFalse(FirstRunDemo.allowed("someone@example.com"))
        // the hash is of the trimmed, lower-case address
        assertEquals(FirstRunDemo.sha256("a@b.c"), FirstRunDemo.sha256(" A@B.C ".trim().lowercase()))
    }
}
