package app.necessityq.domain

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class ResilienceTest {

    @Test fun `backoff doubles and stops at the maximum`() {
        assertEquals(1_000L, Backoff.delayMs(0))
        assertEquals(2_000L, Backoff.delayMs(1))
        assertEquals(8_000L, Backoff.delayMs(3))
        assertEquals(30_000L, Backoff.delayMs(10))
        assertEquals(30_000L, Backoff.delayMs(1_000_000L))
    }

    @Test fun `a stream that fails is subscribed again and continues`() = runBlocking {
        var subscriptions = 0
        val source = flow {
            subscriptions++
            emit("state-$subscriptions")
            if (subscriptions < 3) throw IllegalStateException("listener dropped")
        }
        val seen = mutableListOf<Pair<String?, Long>>()
        val values = source.retryWithBackoff(initialMs = 1, maxMs = 4) { e, attempt -> seen += e.message to attempt }.toList()

        assertEquals(listOf("state-1", "state-2", "state-3"), values)
        assertEquals(listOf<Pair<String?, Long>>("listener dropped" to 0L, "listener dropped" to 1L), seen)
    }

    @Test fun `a stream that never fails is untouched`() = runBlocking {
        var errors = 0
        val values = flow { emit(1); emit(2) }.retryWithBackoff(1, 2) { _, _ -> errors++ }.toList()
        assertEquals(listOf(1, 2), values)
        assertEquals(0, errors)
    }

    @Test fun `cancellation is not retried`() = runBlocking {
        var subscriptions = 0
        var errors = 0
        try {
            flow<Int> { subscriptions++; throw CancellationException("closed") }
                .retryWithBackoff(1, 2) { _, _ -> errors++ }.toList()
            fail("expected cancellation")
        } catch (e: CancellationException) {
            assertTrue(e.message == "closed")
        }
        assertEquals(1, subscriptions)
        assertEquals(0, errors)
    }
}
