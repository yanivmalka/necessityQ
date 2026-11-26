package app.necessityq.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatToolsTest {
    private val min = 60_000L
    private fun msg(id: String, item: String = "milk", author: String = "me", at: Long = 0, kind: MessageKind = MessageKind.TEXT, archived: Long? = null) =
        ItemMessage(id, item, "חלב", author, "x", "טקסט", at, kind = kind, archivedAtMillis = archived)

    @Test fun `edit only own text messages within 15 minutes`() {
        val m = msg("1", at = 0)
        assertTrue(MessageRules.canEdit(m, "me", 14 * min))
        assertTrue(MessageRules.canEdit(m, "me", 15 * min))
        assertFalse(MessageRules.canEdit(m, "me", 15 * min + 1))
        assertFalse(MessageRules.canEdit(m, "other", 1 * min))
        assertFalse(MessageRules.canEdit(msg("2", kind = MessageKind.AUDIO), "me", 1 * min))
        assertFalse(MessageRules.canEdit(m.copy(pending = true), "me", 1 * min))
        assertEquals(5, MessageRules.minutesLeft(m, 10 * min))
        assertEquals(0, MessageRules.minutesLeft(m, 20 * min))
    }

    /** Fixes #72: a deleted message has nothing to read, so it must not stay "unread". */
    @Test fun `a deleted message is never unread`() {
        val fromOther = msg("1", author = "other", at = 10)
        assertTrue(MessageRules.isUnread(fromOther, "me", readAt = 5))
        assertFalse(MessageRules.isUnread(fromOther.copy(deletedAtMillis = 20), "me", readAt = 5))
        // read already, my own message, or older than the last reading: not unread either
        assertFalse(MessageRules.isUnread(fromOther, "me", readAt = 10))
        assertFalse(MessageRules.isUnread(msg("2", author = "me", at = 10), "me", readAt = 5))
    }

    @Test fun `waveform is scaled to the loudest moment`() {
        val wave = Waveform.fromAmplitudes(listOf(0, 100, 8000, 32000, 500, 0, 16000, 200), bars = 4)
        assertEquals(4, wave.size)
        assertEquals(100, wave.max())
        assertTrue(wave.all { it in 1..100 })
        assertEquals(Waveform.BARS, Waveform.fromAmplitudes(emptyList()).size)
        assertEquals(Waveform.BARS, Waveform.fromAmplitudes(listOf(5, 9)).size)
    }

    @Test fun `retention expires archived messages only`() {
        val day = 24L * 60 * min
        val active = msg("a")
        val fresh = msg("b", archived = 10 * day)
        val old = msg("c", archived = 1 * day)
        val now = 35 * day
        assertEquals(listOf("c"), ConversationRetention.expired(listOf(active, fresh, old), 30, now).map { it.id })
        assertEquals(listOf("b", "c"), ConversationRetention.expired(listOf(active, fresh, old), 0, now).map { it.id })
        assertTrue(ConversationRetention.expired(listOf(active, fresh, old), -1, now).isEmpty())
        assertEquals(11 * day, ConversationRetention.deleteAt(1 * day, 10))
    }

    @Test fun `purge keeps statistics and the in-memory repository forgets the messages`() = runBlocking {
        val repo = InMemoryCollabRepository()
        repo.sendMessage("h", msg("1", at = 1, archived = 5))
        repo.sendMessage("h", msg("2", at = 2, kind = MessageKind.IMAGE, archived = 5))
        repo.sendMessage("h", msg("3", item = "bread", at = 3))
        repo.purgeMessages("h", listOf(msg("1", at = 1, archived = 5), msg("2", at = 2, kind = MessageKind.IMAGE, archived = 5)))
        val stats = repo.chatStats("h")
        val s = stats.first().getValue("milk")
        assertEquals(ChatStats(messages = 2, threads = 1, media = 1, lastAtMillis = 2), s)
        assertEquals(listOf("3"), repo.messages("h").first().map { it.id })
    }

    @Test fun `archive and undo only touch their own event`() = runBlocking {
        val repo = InMemoryCollabRepository()
        repo.sendMessage("h", msg("old", at = 1, archived = 100))
        repo.sendMessage("h", msg("new", at = 2))
        repo.archiveConversation("h", "milk", 200)
        var all = repo.messages("h").first()
        assertEquals(mapOf("old" to 100L, "new" to 200L), all.associate { it.id to it.archivedAtMillis })
        repo.unarchiveConversation("h", "milk", 200)
        all = repo.messages("h").first()
        assertEquals(mapOf("old" to 100L, "new" to null), all.associate { it.id to it.archivedAtMillis })
    }

    @Test fun `receipt details list every member but the author`() {
        val m = msg("1", author = "a", at = 100)
        val receipts = mapOf(
            "b" to Receipt(seenAt = 150, read = mapOf("milk" to 160)),
            "c" to Receipt(seenAt = 120),
        )
        val d = ReceiptDetails.of(m, listOf("a", "b", "c", "d"), receipts).associateBy { it.userId }
        assertEquals(DeliveryStatus.READ, d.getValue("b").state)
        assertEquals(160L, d.getValue("b").atMillis)
        assertEquals(DeliveryStatus.DELIVERED, d.getValue("c").state)
        assertEquals(DeliveryStatus.SENT, d.getValue("d").state)
        assertFalse(d.containsKey("a"))
    }
}
