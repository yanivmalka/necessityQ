package app.necessityq.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import kotlin.coroutines.cancellation.CancellationException

/** Waiting time before the next attempt: doubles on every failure, up to [maxMs]. */
object Backoff {
    fun delayMs(attempt: Long, initialMs: Long = 1_000L, maxMs: Long = 30_000L): Long {
        var wait = initialMs.coerceAtLeast(1L)
        var n = 0L
        while (n < attempt && wait < maxMs) { wait *= 2; n++ }
        return wait.coerceAtMost(maxMs)
    }
}

/**
 * A realtime stream that does not die: when the source fails (a dropped listener, a transient server or
 * permission error) it is subscribed again after a growing pause, and the new subscription starts with the
 * current state, so the screen recovers without an app restart. [onError] gets the failure and its attempt
 * number (0 for the first), so a caller can tell the user once instead of on every retry.
 */
fun <T> Flow<T>.retryWithBackoff(
    initialMs: Long = 1_000L,
    maxMs: Long = 30_000L,
    onError: (Throwable, Long) -> Unit = { _, _ -> },
): Flow<T> = retryWhen { cause, attempt ->
    if (cause is CancellationException) {
        false
    } else {
        onError(cause, attempt)
        delay(Backoff.delayMs(attempt, initialMs, maxMs))
        true
    }
}
