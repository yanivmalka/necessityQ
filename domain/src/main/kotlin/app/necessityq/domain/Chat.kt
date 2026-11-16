package app.necessityq.domain

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Rules for changing one's own messages. */
object MessageRules {
    /** A message can be edited for 15 minutes after it was written. */
    const val EDIT_WINDOW_MS: Long = 15L * 60 * 1000

    /** Own, confirmed text / picture messages inside the window (a recording has no text to edit). */
    fun canEdit(m: ItemMessage, me: String, now: Long): Boolean =
        m.authorId == me && !m.pending && m.kind != MessageKind.AUDIO && now - m.atMillis <= EDIT_WINDOW_MS

    fun minutesLeft(m: ItemMessage, now: Long): Int =
        ((EDIT_WINDOW_MS - (now - m.atMillis) + 59_999) / 60_000).toInt().coerceAtLeast(0)

    fun canDelete(m: ItemMessage, me: String): Boolean = m.authorId == me
}

/** Loudness bars of a voice recording. */
object Waveform {
    const val BARS = 40

    /** Condenses raw amplitude samples (0..32767) into [bars] values 0..100, relative to the loudest moment. */
    fun fromAmplitudes(samples: List<Int>, bars: Int = BARS): List<Int> {
        if (samples.isEmpty()) return List(bars) { MIN_LEVEL }
        val peak = max(samples.max(), 1)
        return List(bars) { i ->
            val from = i * samples.size / bars
            val to = max(from + 1, (i + 1) * samples.size / bars).coerceAtMost(samples.size)
            val loudest = samples.subList(from.coerceAtMost(samples.size - 1), to).max()
            (sqrt(loudest.toDouble() / peak) * 100).roundToInt().coerceIn(MIN_LEVEL, 100)
        }
    }

    private const val MIN_LEVEL = 6
}

/** What is left of deleted conversations: counts only, usable for statistics. */
data class ChatStats(
    val messages: Int = 0,
    val threads: Int = 0,
    val media: Int = 0,
    val lastAtMillis: Long = 0L,
) {
    operator fun plus(o: ChatStats) =
        ChatStats(messages + o.messages, threads + o.threads, media + o.media, max(lastAtMillis, o.lastAtMillis))
}

/**
 * Conversations belong to a shopping event. When the event ends (the line is cleared or the list reset)
 * the conversation is archived; after the retention period it is deleted and only [ChatStats] remain.
 */
object ConversationRetention {
    const val DEFAULT_DAYS = 30
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Archived, confirmed messages whose retention ran out. [retentionDays] < 0 keeps everything. */
    fun expired(archived: List<ItemMessage>, retentionDays: Int, now: Long): List<ItemMessage> {
        if (retentionDays < 0) return emptyList()
        return archived.filter { m ->
            val at = m.archivedAtMillis
            !m.pending && at != null && now >= at + retentionDays * DAY_MS
        }
    }

    /** When an archived conversation will be deleted (null = never). */
    fun deleteAt(archivedAtMillis: Long, retentionDays: Int): Long? =
        if (retentionDays < 0) null else archivedAtMillis + retentionDays * DAY_MS

    /** Statistics of the messages of ONE item. */
    fun statsFor(messages: List<ItemMessage>): ChatStats = ChatStats(
        messages = messages.size,
        threads = messages.map { it.archivedAtMillis ?: 0L }.distinct().size.coerceAtLeast(if (messages.isEmpty()) 0 else 1),
        media = messages.count { it.kind != MessageKind.TEXT },
        lastAtMillis = messages.maxOfOrNull { it.atMillis } ?: 0L,
    )
}

/** Who received / read a message, and when. */
data class MemberReceipt(val userId: String, val state: DeliveryStatus, val atMillis: Long?)

object ReceiptDetails {
    /** One entry per member other than the author. For READ the time is when they last opened the conversation. */
    fun of(m: ItemMessage, members: Collection<String>, receipts: Map<String, Receipt>): List<MemberReceipt> =
        members.filter { it != m.authorId }.map { id ->
            val read = receipts[id]?.read?.get(m.itemId) ?: 0L
            val seen = receipts[id]?.seenAt ?: 0L
            when {
                read >= m.atMillis -> MemberReceipt(id, DeliveryStatus.READ, read)
                seen >= m.atMillis -> MemberReceipt(id, DeliveryStatus.DELIVERED, seen)
                else -> MemberReceipt(id, DeliveryStatus.SENT, null)
            }
        }
}
