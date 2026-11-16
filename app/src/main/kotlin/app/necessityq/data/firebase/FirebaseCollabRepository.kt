package app.necessityq.data.firebase

import app.necessityq.domain.CollabRepository
import app.necessityq.domain.ItemMessage
import app.necessityq.domain.NotifySettings
import app.necessityq.domain.Receipt
import app.necessityq.domain.Store
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 *   households/{hid}/stores/{id}        { name, type, lat?, lng? }
 *   households/{hid}/itemMeta/{itemId}  { favorite }
 *   households/{hid}/messages/{id}      { itemId, itemName, authorId, authorName, text, at }
 *   households/{hid}/settings/notify    { missingRecipients[] }
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

    override suspend fun sendMessage(householdId: String, message: ItemMessage) {
        household(householdId).collection("messages").document(message.id).set(FirestoreMapping.messageToMap(message))
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
