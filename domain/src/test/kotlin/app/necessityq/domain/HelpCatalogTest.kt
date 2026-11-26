package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpCatalogTest {
    @Test
    fun `there are seven common problems, each with steps`() {
        assertEquals(7, HelpCatalog.problems.size)
        assertTrue(HelpCatalog.problems.all { it.steps.isNotEmpty() })
        assertEquals(7, HelpCatalog.problems.map { it.id }.toSet().size)
    }

    @Test
    fun `every feature of the settings has an entry that says where to turn it on`() {
        Feature.entries.forEach { f ->
            val e = HelpCatalog.entries.first { it.id == "feature-" + f.name.lowercase() }
            assertTrue(e.howTo.contains(f.group.label))
            assertEquals(HelpTarget.SETTINGS_FEATURES, e.target)
        }
    }

    @Test
    fun `a question finds the thing by its name, its other names or its words`() {
        assertEquals("feature-promos", HelpCatalog.search("איך מדליקים מבצעים?").first().id)
        assertEquals("feature-promos", HelpCatalog.search("המבצעים").first().id)
        assertEquals("feature-chat", HelpCatalog.search("צ'אט פוד").first().id)
        assertEquals("feature-chat", HelpCatalog.search("הקלטה").first().id)
        assertTrue(HelpCatalog.search("מה זה כנראה נגמר בקרוב").any { it.id == "feature-running_low" })
        assertTrue(HelpCatalog.search("נקודה ירוקה").any { it.id == "online-dot" })
        assertTrue(HelpCatalog.search("נגיעה כפולה").any { it.id == "tap-mode" })
    }

    @Test
    fun `only question words, or nothing found, gives no entries`() {
        assertTrue(HelpCatalog.search("").isEmpty())
        assertTrue(HelpCatalog.search("איך מדליקים").isEmpty())
        assertTrue(HelpCatalog.search("בננהזזזזז").isEmpty())
    }

    @Test
    fun `problems are searched too, and all are listed when nothing is asked`() {
        assertEquals(7, HelpCatalog.searchProblems("").size)
        assertEquals("no-notify", HelpCatalog.searchProblems("התראות").first().id)
    }

    @Test
    fun `help text has no arrows, which jumble when drawn right to left`() {
        val text = HelpCatalog.entries.flatMap { listOfNotNull(it.title, it.what, it.benefit, it.drawback, it.howTo) } +
            HelpCatalog.problems.flatMap { listOf(it.title, it.why) + it.steps }
        assertTrue(text.none { line -> line.any { it in "←→↑↓" } })
    }
}

class MemberPresenceTest {
    private fun member(seen: Long?) = Member("u", "דנה", null, MemberRole.MEMBER, lastSeenMillis = seen)

    @Test
    fun `a member is active while the heartbeat is recent`() {
        val now = 1_000_000L
        assertTrue(member(now - 60_000).isActive(now))
        assertTrue(!member(now - Member.ACTIVE_WINDOW_MS - 1).isActive(now))
        assertTrue(!member(null).isActive(now))
    }
}
