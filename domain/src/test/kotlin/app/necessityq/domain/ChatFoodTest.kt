package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.first
import java.time.ZoneId

class ChatFoodTest {
    private val members = listOf(
        ChatMember("u1", "יוחאי"),
        ChatMember("u2", "קרן כהן"),
        ChatMember("u3", "קרן"),
        ChatMember("u4", "דוד"),
    )

    private fun msg(id: String, author: String = "u1", at: Long = 0, mentions: List<String> = emptyList(), replyTo: String? = null) =
        ItemMessage(id, "milk", "חלב", author, "שם", "טקסט", at, mentions = mentions, replyToId = replyTo)

    // ---------- tagging ----------

    @Test fun `active query only right after an at sign that starts a word`() {
        assertEquals("", Mentions.activeQuery("שלום @"))
        assertEquals("קר", Mentions.activeQuery("@קר"))
        assertEquals("קרן כ", Mentions.activeQuery("תקנה @קרן כ"))
        assertNull(Mentions.activeQuery("mail@example"))
        assertNull(Mentions.activeQuery("שלום"))
        assertNull(Mentions.activeQuery("@קרן\nעוד"))
        assertNull(Mentions.activeQuery("@ קרן"))
        // cursor in the middle of the text
        assertEquals("ד", Mentions.activeQuery("@ד ועוד מילים", cursor = 2))
    }

    @Test fun `suggestions skip me and put prefix matches first`() {
        val s = Mentions.suggest("קר", members, me = "u1")
        assertEquals(listOf("קרן", "קרן כהן"), s.map { it.name })
        assertTrue(Mentions.suggest("", members, me = "u1").none { it.userId == "u1" })
        // a second word counts as a prefix too
        assertEquals(listOf("u2"), Mentions.suggest("כה", members, me = "u1").map { it.userId })
        // "@כולם" is offered when there are at least two others
        assertTrue(Mentions.suggest("כו", members, me = "u1").any { it.userId == Mentions.EVERYONE })
    }

    @Test fun `completing replaces the partial name`() {
        val (text, cursor) = Mentions.complete("תקני @קר", 8, "קרן כהן")
        assertEquals("תקני @קרן כהן ", text)
        assertEquals(text.length, cursor)
        val (mid, at) = Mentions.complete("@ד חלב", 2, "דוד")
        assertEquals("@דוד חלב", mid)
        assertEquals(5, at)
    }

    @Test fun `resolve finds the longest name and never the author`() {
        assertEquals(listOf("u2"), Mentions.resolve("@קרן כהן תביאי חלב", members, author = "u1"))
        assertEquals(listOf("u3"), Mentions.resolve("@קרן תביאי חלב", members, author = "u1"))
        assertEquals(listOf("u4", "u3"), Mentions.resolve("@דוד ו-@קרן, מי קונה?", members, author = "u1"))
        assertEquals(emptyList<String>(), Mentions.resolve("@יוחאי", members, author = "u1"))
        assertEquals(emptyList<String>(), Mentions.resolve("@קרנית", members, author = "u1"))
        assertEquals(listOf("u2", "u3", "u4"), Mentions.resolve("@כולם לשים לב", members, author = "u1").sorted())
    }

    @Test fun `find gives ranges for highlighting`() {
        val text = "היי @דוד!"
        val found = Mentions.find(text, members)
        assertEquals(1, found.size)
        assertEquals("@דוד", text.substring(found[0].first))
        assertEquals("u4", found[0].second)
    }

    // ---------- who is alerted ----------

    @Test fun `a message alerts only tagged members and the quoted author`() {
        val original = msg("a", author = "u2")
        val plain = msg("b", author = "u1")
        val quote = msg("c", author = "u1", replyTo = "a")
        val tag = msg("d", author = "u1", mentions = listOf("u4"))
        val byId = listOf(original, plain, quote, tag).associateBy { it.id }

        assertFalse(ChatAlerts.isForMe(plain, "u2", byId))
        assertTrue(ChatAlerts.isForMe(quote, "u2", byId))
        assertFalse(ChatAlerts.isForMe(quote, "u4", byId))
        assertTrue(ChatAlerts.isForMe(tag, "u4", byId))
        assertFalse(ChatAlerts.isForMe(tag, "u2", byId))
        // quoting myself or tagging myself alerts nobody
        assertEquals(emptySet<String>(), ChatAlerts.targets(msg("e", author = "u2", mentions = listOf("u2")), quotedAuthor = "u2"))
        assertEquals(setOf("u2", "u4"), ChatAlerts.targets(msg("f", author = "u1", mentions = listOf("u4")), quotedAuthor = "u2"))
        assertEquals("שם תייג/ה אותך", ChatAlerts.reason(tag, "u4", byId))
        assertEquals("שם הגיב/ה להודעה שלך", ChatAlerts.reason(quote, "u2", byId))
    }

    // ---------- bubbles ----------

    @Test fun `runs of one writer and day lines`() {
        val zone = ZoneId.of("Asia/Jerusalem")
        val min = 60_000L
        val day = 24 * 60 * min
        val base = java.time.LocalDate.of(2026, 11, 18).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val now = base + day
        val thread = listOf(
            msg("1", author = "u1", at = base),
            msg("2", author = "u1", at = base + 2 * min),
            msg("3", author = "u2", at = base + 3 * min),
            msg("4", author = "u2", at = base + 20 * min), // too long after: a new run
            msg("5", author = "u2", at = base + day), // next day
        )
        val slots = ChatLayout.slots(thread, now, zone)
        assertEquals(listOf(true, false, true, true, true), slots.map { it.firstInRun })
        assertEquals(listOf(false, true, true, true, true), slots.map { it.lastInRun })
        assertEquals(listOf("אתמול", null, null, null, "היום"), slots.map { it.dayHeader })
        assertEquals("1.11.2026", ChatLayout.dayLabel(base - 17 * day, now, zone))
    }

    @Test fun `writers get distinct steady colours`() {
        val ids = listOf("b", "a", "c")
        val slots = ids.map { ChatLayout.colorSlot(it, ids, 6) }
        assertEquals(3, slots.toSet().size)
        assertEquals(ChatLayout.colorSlot("a", ids, 6), ChatLayout.colorSlot("a", ids.reversed(), 6))
        assertTrue(ChatLayout.colorSlot("gone", ids, 6) in 0 until 6)
        assertEquals("קכ", ChatLayout.initials("קרן כהן"))
        assertEquals("י", ChatLayout.initials(" יוחאי "))
        assertEquals("?", ChatLayout.initials(""))
    }

    // ---------- voice playback ----------

    @Test fun `speed cycles and seeking stays inside the recording`() {
        assertEquals(1.5f, VoiceSpeed.next(1f))
        assertEquals(2f, VoiceSpeed.next(1.5f))
        assertEquals(1f, VoiceSpeed.next(2f))
        assertEquals(1f, VoiceSpeed.next(7f)) // unknown → back to normal speed
        assertEquals("1.5×", VoiceSpeed.label(1.5f))
        assertEquals(5_000L, VoiceSpeed.seekPosition(0.5f, 10_000))
        assertEquals(10_000L, VoiceSpeed.seekPosition(3f, 10_000))
        assertEquals(0L, VoiceSpeed.seekPosition(-1f, 10_000))
    }

    // ---------- deleting and reactions ----------

    @Test fun `a deleted message keeps its place but cannot be edited or deleted again`() {
        val m = msg("1", author = "u1").copy(deletedAtMillis = 5)
        assertTrue(m.isDeleted)
        assertEquals("🚫 ההודעה נמחקה", m.preview)
        assertFalse(MessageRules.canEdit(m, "u1", 10))
        assertFalse(MessageRules.canDelete(m, "u1"))
        assertEquals("🚫 מחקת את ההודעה", MessageRules.deletedLabel(m, "u1", "יוחאי"))
        assertEquals("🚫 ההודעה נמחקה על ידי יוחאי", MessageRules.deletedLabel(m, "u2", "יוחאי"))
    }

    @Test fun `deleting in memory clears the content and keeps the message`() = kotlinx.coroutines.runBlocking {
        val repo = InMemoryCollabRepository()
        val m = msg("1", author = "u1").copy(kind = MessageKind.IMAGE, mediaId = "img", mentions = listOf("u2"), reactions = mapOf("u2" to "👍"))
        repo.saveMedia("h", "img", "u1", MediaBlob("image/jpeg", byteArrayOf(1)))
        repo.sendMessage("h", m)
        repo.markDeleted("h", m, 99)
        val left = repo.messages("h").first().single()
        assertEquals(99L, left.deletedAtMillis)
        assertEquals(MessageKind.TEXT, left.kind)
        assertNull(left.mediaId)
        assertTrue(left.mentions.isEmpty() && left.reactions.isEmpty())
        assertNull(repo.loadMedia("h", "img"))
    }

    @Test fun `one reaction per member, tapping it again removes it`() = kotlinx.coroutines.runBlocking {
        assertNull(Reactions.toggle("👍", "👍"))
        assertEquals("❤️", Reactions.toggle("👍", "❤️"))
        assertEquals("👍", Reactions.toggle(null, "👍"))
        val repo = InMemoryCollabRepository()
        repo.sendMessage("h", msg("1"))
        repo.react("h", "1", "u2", "👍")
        repo.react("h", "1", "u3", "❤️")
        repo.react("h", "1", "u4", "❤️")
        repo.react("h", "1", "u2", "❤️") // replaces the 👍
        repo.react("h", "1", "u4", null)
        val m = repo.messages("h").first().single()
        assertEquals(mapOf("u2" to "❤️", "u3" to "❤️"), m.reactions)
        assertEquals(listOf("❤️" to 3, "👍" to 1), Reactions.summary(mapOf("a" to "👍", "b" to "❤️", "c" to "❤️", "d" to "❤️")))
        assertEquals(listOf("👍" to 1, "🔥" to 1), Reactions.summary(mapOf("a" to "🔥", "b" to "👍")))
    }
}
