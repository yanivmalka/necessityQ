package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {
    @Test fun `build number is the last number of the tag`() {
        assertEquals(5, Releases.versionCodeOf("v0.1.5"))
        assertEquals(12, Releases.versionCodeOf(" v0.1.12 "))
        assertNull(Releases.versionCodeOf("nightly"))
    }

    @Test fun `new tags carry the build after -b and the name restarts per line`() {
        assertEquals(235, Releases.versionCodeOf("v0.2.1-b235"))
        assertEquals(240, Releases.versionCodeOf("v0.3.0-b240"))
        assertEquals("0.3.0", Releases.versionNameOf("v0.3.0-b240"))
        assertEquals("0.1.5", Releases.versionNameOf("v0.1.5"))
    }

    @Test fun `only a higher build counts as an update`() {
        assertTrue(Releases.isNewer(6, 5))
        assertFalse(Releases.isNewer(5, 5))
        assertFalse(Releases.isNewer(4, 5))
    }

    @Test fun `version name drops the v prefix`() {
        assertEquals("0.1.5", ReleaseInfo("v0.1.5", 5, "", "", 0).versionName)
    }
}
