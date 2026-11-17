package app.necessityq.domain

/** A member is typing in the conversation of an item (live, short-lived; never stored as history). */
data class TypingState(
    val userId: String,
    val name: String,
    val itemId: String,
    val itemName: String,
    val atMillis: Long,
)

object Typing {
    /** A typing mark older than this is stale (the writer stopped, left or lost the connection). */
    const val FRESH_MS = 8_000L
    /** How often an ongoing typing is re-announced. */
    const val HEARTBEAT_MS = 3_000L
    /** Typing stops by itself after this long without a keystroke. */
    const val IDLE_MS = 6_000L

    /** Other members typing right now (a few seconds of clock difference between phones are tolerated). */
    fun active(all: List<TypingState>, me: String, nowMillis: Long): List<TypingState> =
        all.filter { it.userId != me && it.atMillis in (nowMillis - FRESH_MS)..(nowMillis + FRESH_MS) }
            .sortedBy { it.name }

    /** "קרן באמצע הקלדה…", "קרן ויניב באמצע הקלדה…", "3 אנשים באמצע הקלדה…" — gender-neutral wording. */
    fun label(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> "${names[0]} באמצע הקלדה…"
        2 -> "${names[0]} ו${names[1]} באמצע הקלדה…"
        else -> "${names.size} אנשים באמצע הקלדה…"
    }

    /** Should the writer announce (again) that it is typing about [itemId]? */
    fun shouldAnnounce(lastItemId: String?, lastAtMillis: Long, itemId: String, nowMillis: Long): Boolean =
        lastItemId != itemId || nowMillis - lastAtMillis >= HEARTBEAT_MS
}
