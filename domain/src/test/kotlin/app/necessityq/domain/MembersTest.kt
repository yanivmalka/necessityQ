package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MembersTest {
    @Test fun `legacy household without owner treats the first member as owner`() {
        val h = Household("h", "בית", "ABC234", listOf("a", "b"))
        assertTrue(h.isOwner("a"))
        assertFalse(h.isOwner("b"))
    }

    @Test fun `owner who is no longer a member falls back to the first member`() {
        val h = Household("h", "בית", "ABC234", listOf("b", "c"), ownerId = "a")
        assertEquals("b", h.effectiveOwnerId)
    }

    @Test fun `resolve lists the owner first and names unnamed members`() {
        val h = Household("h", "בית", "ABC234", listOf("a", "b", "c"), ownerId = "b")
        val members = Members.resolve(h, mapOf("b" to ("יניב" to "y@x.com"), "c" to ("כרן" to null)))
        assertEquals(listOf("b", "c", "a"), members.map { it.userId })
        assertEquals(MemberRole.OWNER, members[0].role)
        assertEquals("משתתף 1", members[2].displayName)
        assertEquals("y@x.com", members[0].email)
    }
}
