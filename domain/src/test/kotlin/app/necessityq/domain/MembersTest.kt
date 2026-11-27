package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MembersTest {
    @Test fun `legacy household without owner treats the first member as owner`() {
        val h = Household("h", "בית", listOf("a", "b"))
        assertTrue(h.isOwner("a"))
        assertFalse(h.isOwner("b"))
    }

    @Test fun `owner who is no longer a member falls back to the first member`() {
        val h = Household("h", "בית", listOf("b", "c"), ownerId = "a")
        assertEquals("b", h.effectiveOwnerId)
    }

    @Test fun `resolve lists the owner first and names unnamed members`() {
        val h = Household("h", "בית", listOf("a", "b", "c"), ownerId = "b")
        val members = Members.resolve(h, mapOf("b" to ("יניב" to "y@x.com"), "c" to ("כרן" to null)))
        assertEquals(listOf("b", "c", "a"), members.map { it.userId })
        assertEquals(MemberRole.OWNER, members[0].role)
        assertEquals("משתתף 1", members[2].displayName)
        assertEquals("y@x.com", members[0].email)
    }

    @Test fun `a family profile linked to an account gives the account its name`() {
        val h = Household("h", "בית", listOf("a", "b", "c"), ownerId = "a")
        val members = Members.resolve(h, mapOf("a" to ("Yochai Cohen" to null), "b" to ("Adel" to null)))
        val family = listOf(
            FamilyMember("f1", "יוחאי", linkedUserId = "a"),
            FamilyMember("f2", "אבא"),
            FamilyMember("f3", "  ", linkedUserId = "b"),
        )
        val named = Members.withFamilyNames(members, family).associate { it.userId to it.displayName }
        assertEquals("יוחאי", named["a"])
        // a blank profile name does not hide the account's own name
        assertEquals("Adel", named["b"])
        // no profile: the account's name (here the placeholder) stays
        assertEquals("משתתף 3", named["c"])
    }
}
