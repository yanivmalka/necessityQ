package app.necessityq.domain

/** What a member is doing in the conversation: writing a message or recording a voice message. */
enum class TypingKind { TEXT, RECORDING }

/** A member is typing / recording in the conversation of an item (live, short-lived; never stored as history). */
data class TypingState(
    val userId: String,
    val name: String,
    val itemId: String,
    val itemName: String,
    val atMillis: Long,
    val kind: TypingKind = TypingKind.TEXT,
)

object Typing {
    /** A mark not refreshed for this long is stale (the writer stopped, left or lost the connection). */
    const val FRESH_MS = 6_000L
    /** How often an ongoing typing is re-announced. */
    const val HEARTBEAT_MS = 2_500L
    /** Typing stops by itself after this long without a keystroke. */
    const val IDLE_MS = 5_000L

    /** Other members active right now. Marks carry the reader's own receive time (see [TypingTracker]). */
    fun active(all: List<TypingState>, me: String, nowMillis: Long): List<TypingState> =
        all.filter { it.userId != me && it.atMillis in (nowMillis - FRESH_MS)..(nowMillis + FRESH_MS) }
            .sortedBy { it.name }

    private fun phrase(names: List<String>, noun: String): String = when (names.size) {
        0 -> ""
        1 -> "${names[0]} באמצע $noun…"
        2 -> "${names[0]} ו${names[1]} באמצע $noun…"
        else -> "${names.size} אנשים באמצע $noun…"
    }

    /** "קרן באמצע הקלדה…", "קרן ויניב באמצע הקלדה…", "3 אנשים באמצע הקלדה…" — gender-neutral wording. */
    fun label(names: List<String>): String = phrase(names, "הקלדה")

    /** Like [label], and "באמצע הקלטה…" for those recording a voice message. */
    fun labelFor(states: List<TypingState>): String = listOf(
        phrase(states.filter { it.kind == TypingKind.TEXT }.map { it.name }, "הקלדה"),
        phrase(states.filter { it.kind == TypingKind.RECORDING }.map { it.name }, "הקלטה"),
    ).filter { it.isNotEmpty() }.joinToString(" · ")

    /** Should the writer announce (again) that it is typing about [itemId]? */
    fun shouldAnnounce(lastItemId: String?, lastAtMillis: Long, itemId: String, nowMillis: Long): Boolean =
        lastItemId != itemId || nowMillis - lastAtMillis >= HEARTBEAT_MS
}

/**
 * Turns the marks of one listener into marks stamped with the READER's clock. The writer's clock is
 * never compared with ours (phones differ by seconds, which made "typing…" late or invisible): a mark
 * is fresh when it CHANGED (a heartbeat rewrites it) a moment ago on our own clock. Marks already there
 * when listening started are stale until their next heartbeat, so a leftover of a closed app is not shown.
 */
class TypingTracker {
    private data class Seen(val key: String, val localAt: Long)
    private val seen = HashMap<String, Seen>()
    private var baseline = true

    fun observe(marks: List<TypingState>, nowMillis: Long): List<TypingState> {
        seen.keys.retainAll(marks.map { it.userId }.toSet())
        val out = marks.map { m ->
            val key = "${m.itemId}|${m.kind}|${m.atMillis}"
            val prev = seen[m.userId]
            val local = when {
                prev != null && prev.key == key -> prev.localAt
                baseline -> 0L
                else -> nowMillis
            }
            seen[m.userId] = Seen(key, local)
            m.copy(atMillis = local)
        }
        baseline = false
        return out
    }
}
