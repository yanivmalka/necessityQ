package app.necessityq.data.firebase

import app.necessityq.domain.CollabRepository
import app.necessityq.domain.ItemMessage
import app.necessityq.domain.NotifySettings
import app.necessityq.domain.Receipt
import app.necessityq.domain.Store
import app.necessityq.domain.ChatStats
import app.necessityq.domain.ConversationRetention
import app.necessityq.domain.MediaBlob
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 *   households/{hid}/stores/{id}        { name, type, lat?, lng? }
 *   households/{hid}/itemMeta/{itemId}  { favorite, price?, chatMessages?, chatThreads?, chatMedia?, chatLastAt? }
 *   households/{hid}/messages/{id}      { itemId, itemName, authorId, authorName, text, at, kind?, mediaId?, durationMs?, replyTo?,
 *                                         wave?, editedAt?, archivedAt? }
 *   households/{hid}/settings/chat      { retentionDays }
 *   households/{hid}/media/{id}         { mime, data (bytes), by, at }  — picture / recording of a message
 *   households/{hid}/settings/notify    { missingRecipients[] }
 *   households/{hid}/typing/{uid}       { itemId, itemName, name, at } — live "typing…" mark; deleted when typing stops
 *
 * Writes are fire-and-forget like the list edits: Firestore applies them to its local cache at once
 * and syncs when online.
 */
class FirebaseCollabRepository(private val db: FirebaseFirestore) : CollabRepository {

    private fun household(hid: String) = db.collection("households").document(hid)

    override fun stores(householdId: String): Flow<List<Store>> =
        household(householdId).collection("stores").observe()
            .map { snap -> snap.documents.mapNotNull(FirestoreMapping::storeFrom).sortedBy { it.name } }

    override suspend fun saveStore(householdId: String, store: Store) {
        household(householdId).collection("stores").document(store.id).set(FirestoreMapping.storeToMap(store))
    }

    override fun favorites(householdId: String): Flow<Set<String>> =
        household(householdId).collection("itemMeta").observe().map { snap ->
            snap.documents.filter { it.getBoolean("favorite") == true }.map { it.id }.toSet()
        }

    override suspend fun setFavorite(householdId: String, itemId: String, favorite: Boolean) {
        household(householdId).collection("itemMeta").document(itemId)
            .set(mapOf("favorite" to favorite), com.google.firebase.firestore.SetOptions.merge())
    }

    override fun messages(householdId: String): Flow<List<ItemMessage>> =
        household(householdId).collection("messages").orderBy("at", Query.Direction.ASCENDING).limitToLast(500).observe(includeMetadata = true)
            .map { snap -> snap.documents.mapNotNull(FirestoreMapping::messageFrom) }

    override fun typing(householdId: String): Flow<List<app.necessityq.domain.TypingState>> =
        household(householdId).collection("typing").observe().map { snap ->
            snap.documents.mapNotNull { d ->
                app.necessityq.domain.TypingState(
                    userId = d.id,
                    name = d.getString("name").orEmpty(),
                    itemId = d.getString("itemId") ?: return@mapNotNull null,
                    itemName = d.getString("itemName").orEmpty(),
                    atMillis = d.getLong("at") ?: return@mapNotNull null,
                )
            }
        }

    override suspend fun setTyping(householdId: String, userId: String, state: app.necessityq.domain.TypingState?) {
        val doc = household(householdId).collection("typing").document(userId)
        if (state == null) doc.delete()
        else doc.set(mapOf("itemId" to state.itemId, "itemName" to state.itemName, "name" to state.name, "at" to state.atMillis))
    }

    override suspend fun sendMessage(householdId: String, message: ItemMessage) {
        household(householdId).collection("messages").document(message.id).set(FirestoreMapping.messageToMap(message))
    }

    override suspend fun editMessage(householdId: String, messageId: String, text: String, editedAtMillis: Long) {
        household(householdId).collection("messages").document(messageId)
            .update(mapOf("text" to text, "editedAt" to editedAtMillis))
    }

    override suspend fun deleteMessages(householdId: String, messages: List<ItemMessage>) {
        val hh = household(householdId)
        messages.chunked(200).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { m ->
                batch.delete(hh.collection("messages").document(m.id))
                m.mediaId?.let { batch.delete(hh.collection("media").document(it)) }
            }
            batch.commit()
        }
    }

    /** Messages of [itemId] archived exactly at [from] (null = still open) move to [to]. */
    private suspend fun moveArchive(householdId: String, itemId: String, from: Long?, to: Long?) {
        val docs = household(householdId).collection("messages").whereEqualTo("itemId", itemId).get().await()
            .documents.filter { it.getLong("archivedAt") == from }
        docs.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { d -> batch.update(d.reference, "archivedAt", to ?: FieldValue.delete()) }
            batch.commit()
        }
    }

    override suspend fun archiveConversation(householdId: String, itemId: String, atMillis: Long) =
        moveArchive(householdId, itemId, null, atMillis)

    override suspend fun unarchiveConversation(householdId: String, itemId: String, atMillis: Long) =
        moveArchive(householdId, itemId, atMillis, null)

    /**
     * One transaction per item: only messages that still exist are counted, so two devices purging at
     * the same moment cannot count the same message twice.
     */
    override suspend fun purgeMessages(householdId: String, messages: List<ItemMessage>) {
        val hh = household(householdId)
        messages.groupBy { it.itemId }.forEach { (itemId, group) ->
            group.chunked(200).forEach { chunk ->
                db.runTransaction { tx ->
                    val existing = chunk.filter { tx.get(hh.collection("messages").document(it.id)).exists() }
                    if (existing.isNotEmpty()) {
                        val stats = ConversationRetention.statsFor(existing)
                        tx.set(
                            hh.collection("itemMeta").document(itemId),
                            mapOf(
                                "chatMessages" to FieldValue.increment(stats.messages.toLong()),
                                "chatThreads" to FieldValue.increment(stats.threads.toLong()),
                                "chatMedia" to FieldValue.increment(stats.media.toLong()),
                                "chatLastAt" to stats.lastAtMillis,
                            ),
                            com.google.firebase.firestore.SetOptions.merge(),
                        )
                        existing.forEach { m ->
                            tx.delete(hh.collection("messages").document(m.id))
                            m.mediaId?.let { tx.delete(hh.collection("media").document(it)) }
                        }
                    }
                    Unit
                }.await()
            }
        }
    }

    /** Null until the server confirmed the setting, so nothing is deleted on a guess from an empty cache. */
    override fun chatRetentionDays(householdId: String): Flow<Int?> =
        household(householdId).collection("settings").document("chat").observe(includeMetadata = true).map { doc ->
            if (!doc.exists() && doc.metadata.isFromCache) null
            else doc.getLong("retentionDays")?.toInt() ?: ConversationRetention.DEFAULT_DAYS
        }

    override suspend fun setChatRetentionDays(householdId: String, days: Int) {
        household(householdId).collection("settings").document("chat").set(mapOf("retentionDays" to days))
    }

    override fun chatStats(householdId: String): Flow<Map<String, ChatStats>> =
        household(householdId).collection("itemMeta").observe().map { snap ->
            snap.documents.mapNotNull { doc ->
                val n = doc.getLong("chatMessages")?.toInt() ?: return@mapNotNull null
                doc.id to ChatStats(n, doc.getLong("chatThreads")?.toInt() ?: 0, doc.getLong("chatMedia")?.toInt() ?: 0, doc.getLong("chatLastAt") ?: 0L)
            }.toMap()
        }

    override suspend fun saveMedia(householdId: String, mediaId: String, authorId: String, media: MediaBlob) {
        household(householdId).collection("media").document(mediaId).set(
            mapOf("mime" to media.mime, "data" to Blob.fromBytes(media.bytes), "by" to authorId, "at" to System.currentTimeMillis()),
        )
    }

    override suspend fun loadMedia(householdId: String, mediaId: String): MediaBlob? {
        val doc = household(householdId).collection("media").document(mediaId).get().await()
        val bytes = doc.getBlob("data")?.toBytes() ?: return null
        return MediaBlob(doc.getString("mime") ?: "application/octet-stream", bytes)
    }

    override fun prices(householdId: String): Flow<Map<String, Double>> =
        household(householdId).collection("itemMeta").observe().map { snap ->
            snap.documents.mapNotNull { doc -> doc.getDouble("price")?.let { doc.id to it } }.toMap()
        }

    override suspend fun setPrice(householdId: String, itemId: String, price: Double?) {
        household(householdId).collection("itemMeta").document(itemId)
            .set(mapOf("price" to (price ?: FieldValue.delete())), com.google.firebase.firestore.SetOptions.merge())
    }

    override fun notifySettings(householdId: String): Flow<NotifySettings> =
        household(householdId).collection("settings").document("notify").observe().map { doc ->
            @Suppress("UNCHECKED_CAST")
            NotifySettings(((doc.get("missingRecipients") as? List<String>) ?: emptyList()).toSet())
        }

    override fun receipts(householdId: String): Flow<Map<String, Receipt>> =
        household(householdId).collection("reads").observe().map { snap ->
            snap.documents.associate { doc ->
                @Suppress("UNCHECKED_CAST")
                val read = (doc.get("read") as? Map<String, Any?>).orEmpty()
                    .mapNotNull { (k, v) -> (v as? Number)?.toLong()?.let { k to it } }.toMap()
                doc.id to Receipt(doc.getLong("seenAt") ?: 0L, read)
            }
        }

    override suspend fun markSeen(householdId: String, userId: String, atMillis: Long) {
        household(householdId).collection("reads").document(userId)
            .set(mapOf("seenAt" to atMillis), com.google.firebase.firestore.SetOptions.merge())
    }

    override suspend fun markRead(householdId: String, userId: String, itemId: String, atMillis: Long) {
        household(householdId).collection("reads").document(userId)
            .set(mapOf("read" to mapOf(itemId to atMillis)), com.google.firebase.firestore.SetOptions.merge())
    }

    override suspend fun setMissingRecipients(householdId: String, userIds: Set<String>) {
        household(householdId).collection("settings").document("notify").set(mapOf("missingRecipients" to userIds.toList()))
    }
}
