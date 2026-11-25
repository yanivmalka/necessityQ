package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureAdvisorTest {
    private val day = AdvisorState.DAY
    private val now = 100 * day
    private val routine = listOf(
        RoutineItem("חלב", 5), RoutineItem("לחם", 3), RoutineItem("ביצים", 7), RoutineItem("קוטג'", 6), RoutineItem("עגבניות", 4),
    )

    @Test
    fun `routine items suggest the reminders, told from the household's own data`() {
        val s = FeatureAdvisor.next(Features.SIMPLE.with(Feature.RUNNING_LOW, false), UsageSignals(routine = routine), AdvisorState(since = 1), now)!!
        assertEquals("on:RUNNING_LOW", s.id)
        assertEquals(Feature.RUNNING_LOW, s.feature)
        assertTrue(s.why.contains("חלב: כל 5 ימים"))
        assertTrue(s.why.contains("ועוד 3 מוצרים"))
        // with the reminders on there is nothing to suggest
        assertNull(FeatureAdvisor.next(Features.SIMPLE, UsageSignals(routine = routine), AdvisorState(since = 1), now))
    }

    @Test
    fun `declined is never offered again, later waits, and a week passes between cards`() {
        val off = Features(emptySet())
        val signals = UsageSignals(routine = routine, repeated = listOf("קוטג'" to 8))
        val declined = AdvisorState(since = 1).decline("on:RUNNING_LOW", now)
        // a week after the answer, the next suggestion
        assertNull(FeatureAdvisor.next(off, signals, declined, now + 6 * day))
        assertEquals("on:FREQUENT", FeatureAdvisor.next(off, signals, declined, now + 7 * day)?.id)
        // put off: back after two weeks
        val later = AdvisorState(since = 1).later("on:RUNNING_LOW", now)
        assertEquals("on:FREQUENT", FeatureAdvisor.next(off, signals, later, now + 8 * day)?.id)
        assertEquals("on:RUNNING_LOW", FeatureAdvisor.next(off, signals, later, now + 15 * day)?.id)
        // restored from the list of declined suggestions
        assertEquals("on:RUNNING_LOW", FeatureAdvisor.next(off, signals, declined.restore("on:RUNNING_LOW"), now + 8 * day)?.id)
    }

    @Test
    fun `prices, branches, chat and the double tap`() {
        val ids = { f: Features, s: UsageSignals -> FeatureAdvisor.all(f, s, since = 1, now = now).map { it.id } }
        assertTrue("on:PRICES" in ids(Features.SIMPLE, UsageSignals(scans = 2)))
        assertTrue("pick-branch" in ids(Features.ALL, UsageSignals(followedBranches = 0)))
        assertTrue("second-branch" in ids(Features.ALL, UsageSignals(followedBranches = 1)))
        assertTrue("second-branch" !in ids(Features.ALL, UsageSignals(followedBranches = 2)))
        assertTrue("on:CHAT" in ids(Features.SIMPLE, UsageSignals(members = 2, missingMarks = 3)))
        assertTrue("on:CHAT" !in ids(Features.SIMPLE, UsageSignals(members = 1, missingMarks = 3)))
        assertTrue("double-tap" in ids(Features.SIMPLE, UsageSignals(quickCloses = 3)))
        assertTrue("double-tap" !in ids(Features.SIMPLE, UsageSignals(quickCloses = 3, doubleTap = true)))
    }

    @Test
    fun `a tab unused for a month is offered to be hidden`() {
        val used = mapOf(Feature.PRICES_TAB to now - 2 * day, Feature.CHAT to now - 40 * day)
        val ids = FeatureAdvisor.all(Features.ALL, UsageSignals(followedBranches = 2, lastUsed = used), since = 1, now = now).map { it.id }
        assertTrue("off:MENU_TAB" in ids) // never opened
        assertTrue("off:CHAT" in ids)
        assertTrue("off:PRICES_TAB" !in ids)
        // just turned on: not yet
        val fresh = UsageSignals(followedBranches = 2, lastUsed = used, enabledAt = mapOf(Feature.MENU_TAB to now - day))
        assertTrue("off:MENU_TAB" !in FeatureAdvisor.all(Features.ALL, fresh, since = 1, now = now).map { it.id })
        // watching only started now
        assertTrue(FeatureAdvisor.all(Features.ALL, UsageSignals(followedBranches = 2), since = now, now = now).none { it.id.startsWith("off:") })
    }

    @Test
    fun `the state is stored as text`() {
        val state = AdvisorState(declined = setOf("on:CHAT", "double-tap"), snoozedUntil = mapOf("on:PRICES" to 5L), lastAnsweredAt = 7L, since = 3L)
        assertEquals(state, AdvisorState.decode(state.encode()))
        assertEquals(AdvisorState(), AdvisorState.decode(null))
        assertEquals("להדליק צ'אט פוד", FeatureAdvisor.describe("on:CHAT"))
        assertEquals(AdviceKind.TURN_OFF, FeatureAdvisor.kindOf("off:MENU_TAB"))
    }
}
