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
    fun `a single typed letter does not crash the search`() {
        assertTrue(HelpCatalog.search("מ").isEmpty())
        assertTrue(HelpCatalog.searchProblems("ה").isEmpty())
        assertTrue(HelpCatalog.search("מבצעים ה").isEmpty())
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

class ProductSearchStagesTest {
    private fun p(code: String, name: String, price: Double = 5.0) =
        BranchProduct(code = code, name = name, brand = "", packageSize = null, unit = "", weighted = false, internalCode = false, price = price, unitPrice = null, since = "", low = price, high = price)

    @Test
    fun `stages come quick pass first and end where the full search ends`() {
        val a = listOf(p("1", "עגבניה שרי"), p("2", "עגבניות מרוסקות"), p("3", "חלב"))
        val b = listOf(p("1", "עגבניה שרי"))
        val idx = listOf("a" to ProductIndex("a", 1, a), "b" to ProductIndex("b", 1, b))
        val codes = mapOf("a" to PriceComparisons.byCode(a), "b" to PriceComparisons.byCode(b))
        val steps = ProductSearch.stages(idx, { codes[it].orEmpty() }, "עגבניות").toList()
        // "עגבניות" is contained only in the second name: found by the quick pass; the singular needs the stem pass
        assertEquals(listOf("2"), steps.first().map { it.product.code })
        assertEquals(setOf("1", "2"), steps.last().map { it.product.code }.toSet())
        val full = ProductSearch.across(listOf("a" to a, "b" to b), codes, "עגבניות").map { it.product.code }
        assertEquals(full.toSet(), steps.last().map { it.product.code }.toSet())
    }
}
