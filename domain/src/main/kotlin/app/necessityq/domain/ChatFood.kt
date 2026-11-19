package app.necessityq.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * צ'אט פוד — the household's conversations about products.
 *
 * Who gets alerted: not every member gets a notification for every message. A message alerts only the
 * members it is aimed at: those tagged with "@name" and the author of the message it quotes (a quote
 * counts as a tag). Everyone still sees the message in the conversation.
 */

/** A household member that can be tagged. */
data class ChatMember(val userId: String, val name: String)

object Mentions {
    /** "@כולם" tags every member. */
    const val EVERYONE = "כולם"

    /** The longest partial name looked up while typing after "@" (names may contain one space or two). */
    private const val MAX_QUERY = 24

    /**
     * The name being typed after "@" just before [cursor], or null when the cursor is not in a tag.
     * The "@" must not follow a letter or digit (so e-mail addresses do not trigger it; "ו-@קרן" does).
     */
    fun activeQuery(text: String, cursor: Int = text.length): String? {
        val end = cursor.coerceIn(0, text.length)
        val at = text.lastIndexOf('@', (end - 1).coerceAtLeast(-1))
        if (at < 0 || end <= at) return null
        if (at > 0 && text[at - 1].isLetterOrDigit()) return null
        val partial = text.substring(at + 1, end)
        if (partial.length > MAX_QUERY || '\n' in partial || partial.startsWith(' ') || partial.contains("  ")) return null
        return partial
    }

    /** Members to offer for a partial name: names that start with it first, then names that contain it. Never me. */
    fun suggest(query: String, members: List<ChatMember>, me: String): List<ChatMember> {
        val q = query.trim().lowercase()
        val others = members.filter { it.userId != me && it.name.isNotBlank() }
        val everyone = if (others.size >= 2 && EVERYONE.startsWith(q)) listOf(ChatMember(EVERYONE, EVERYONE)) else emptyList()
        if (q.isEmpty()) return others.sortedBy { it.name } + everyone
        val starts = others.filter { m -> m.name.lowercase().startsWith(q) || m.name.lowercase().split(' ').any { it.startsWith(q) } }
        val contains = others.filter { it !in starts && it.name.lowercase().contains(q) }
        return starts.sortedBy { it.name } + contains.sortedBy { it.name } + everyone
    }

    /**
     * Replaces the "@partial" before [cursor] with "@name " and returns the new text and cursor position.
     * Without an active tag the text is returned unchanged.
     */
    fun complete(text: String, cursor: Int, name: String): Pair<String, Int> {
        val end = cursor.coerceIn(0, text.length)
        activeQuery(text, end) ?: return text to end
        val at = text.lastIndexOf('@', end - 1)
        val inserted = "@$name "
        val rest = text.substring(end).let { if (it.startsWith(' ')) it.substring(1) else it }
        return text.substring(0, at) + inserted + rest to at + inserted.length
    }

    /** Where each tag is in [text] (for highlighting), with the member it tags; longer names win over prefixes. */
    fun find(text: String, members: List<ChatMember>): List<Pair<IntRange, String>> {
        val names = (members.filter { it.name.isNotBlank() } + ChatMember(EVERYONE, EVERYONE)).sortedByDescending { it.name.length }
        val found = ArrayList<Pair<IntRange, String>>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '@' && (i == 0 || !text[i - 1].isLetterOrDigit())) {
                val hit = names.firstOrNull { m ->
                    text.startsWith(m.name, i + 1, ignoreCase = true) &&
                        (i + 1 + m.name.length == text.length || !text[i + 1 + m.name.length].isLetterOrDigit())
                }
                if (hit != null) {
                    val range = i..(i + hit.name.length)
                    found += range to hit.userId
                    i = range.last + 1
                    continue
                }
            }
            i++
        }
        return found
    }

    /** The members tagged in [text] ("@כולם" = every member). Never the author. */
    fun resolve(text: String, members: List<ChatMember>, author: String): List<String> {
        val tags = find(text, members).map { it.second }.toSet()
        val ids = if (EVERYONE in tags) members.map { it.userId } else tags.toList()
        return ids.filter { it != author && it != EVERYONE }.distinct()
    }
}

/** How a member wants to be told about new messages. */
enum class ChatNotifyMode(val label: String) {
    /** The default: only messages that tag me or quote my message. */
    TAGGED_ONLY("רק כשמתייגים או מצטטים אותי"),
    ALL("כל הודעה חדשה"),
}

object ChatAlerts {
    /** The members a message is aimed at: everyone it tags, plus the author of the message it quotes. */
    fun targets(m: ItemMessage, quotedAuthor: String?): Set<String> =
        (m.mentions + listOfNotNull(quotedAuthor)).filter { it != m.authorId }.toSet()

    /** Whether [m] is aimed at [me] (tagged or quoted). */
    fun isForMe(m: ItemMessage, me: String, byId: Map<String, ItemMessage>): Boolean =
        m.authorId != me && me in targets(m, m.replyToId?.let { byId[it]?.authorId })

    /** Why I am alerted, for the notification title. */
    fun reason(m: ItemMessage, me: String, byId: Map<String, ItemMessage>): String = when {
        me in m.mentions -> "${m.authorName} תייג/ה אותך"
        m.replyToId?.let { byId[it]?.authorId } == me -> "${m.authorName} הגיב/ה להודעה שלך"
        else -> "הודעה מ${m.authorName}"
    }
}

/** One bubble on screen: where it sits in a run of messages by the same writer, and a date line above it. */
data class BubbleSlot(
    val message: ItemMessage,
    /** First bubble of a run: shows the writer's name (for others) and gets the pointed corner. */
    val firstInRun: Boolean,
    /** Last bubble of a run: a little more space below it. */
    val lastInRun: Boolean,
    /** "היום", "אתמול" or the date, when this message starts a new day. */
    val dayHeader: String?,
)

object ChatLayout {
    /** Messages of one writer closer than this belong to one run (as in WhatsApp). */
    const val RUN_GAP_MS = 5L * 60 * 1000

    private val dateFormat = DateTimeFormatter.ofPattern("d.M.yyyy")

    fun dayLabel(atMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val day = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when (day) {
            today -> "היום"
            today.minusDays(1) -> "אתמול"
            else -> day.format(dateFormat)
        }
    }

    fun slots(thread: List<ItemMessage>, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): List<BubbleSlot> {
        fun dayOf(m: ItemMessage): LocalDate = Instant.ofEpochMilli(m.atMillis).atZone(zone).toLocalDate()
        fun sameRun(a: ItemMessage, b: ItemMessage) =
            a.authorId == b.authorId && dayOf(a) == dayOf(b) && b.atMillis - a.atMillis in 0..RUN_GAP_MS
        return thread.mapIndexed { i, m ->
            val prev = thread.getOrNull(i - 1)
            val next = thread.getOrNull(i + 1)
            val newDay = prev == null || dayOf(prev) != dayOf(m)
            BubbleSlot(
                message = m,
                firstInRun = newDay || prev == null || !sameRun(prev, m),
                lastInRun = next == null || !sameRun(m, next),
                dayHeader = if (newDay) dayLabel(m.atMillis, nowMillis, zone) else null,
            )
        }
    }

    /**
     * A steady colour slot per writer, as in group chats: members in a fixed order get distinct slots;
     * someone no longer in the household falls back to a hash of their id.
     */
    fun colorSlot(userId: String, members: List<String>, slots: Int): Int {
        val ordered = members.sorted()
        val i = ordered.indexOf(userId)
        return if (i >= 0) i % slots else Math.floorMod(userId.hashCode(), slots)
    }

    /** First letter(s) for a small avatar: "קרן כהן" → "קכ", "יוחאי" → "י". */
    fun initials(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.ifEmpty { "?" }
}

/** Playback speed of voice messages: 1× → 1.5× → 2× → 1×. */
object VoiceSpeed {
    val steps = listOf(1.0f, 1.5f, 2.0f)

    fun next(current: Float): Float {
        val i = steps.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
        return steps[(i + 1).mod(steps.size)]
    }

    fun label(speed: Float): String = when {
        kotlin.math.abs(speed - 1.5f) < 0.01f -> "1.5×"
        kotlin.math.abs(speed - 2f) < 0.01f -> "2×"
        else -> "1×"
    }

    /** A point on the recording from a touch at [fraction] (0..1) of the bar's width. */
    fun seekPosition(fraction: Float, totalMs: Long): Long =
        (fraction.coerceIn(0f, 1f) * totalMs).toLong().coerceIn(0L, totalMs)
}

/** Emoji reactions on a message (one per member, as in WhatsApp). */
object Reactions {
    /** The quick choices shown above a message on long-press. */
    val quick = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

    /** Tapping the emoji I already chose removes my reaction; another emoji replaces it. */
    fun toggle(current: String?, chosen: String): String? = if (current == chosen) null else chosen

    /** Each emoji with how many chose it, most chosen first (ties: in the order of [quick], then the rest). */
    fun summary(reactions: Map<String, String>): List<Pair<String, Int>> =
        reactions.values.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { quick.indexOf(it.key).let { i -> if (i < 0) quick.size else i } })
            .map { it.key to it.value }
}
