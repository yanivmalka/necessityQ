package app.necessityq.data.firebase

import app.necessityq.domain.LineStatus
import app.necessityq.domain.ListLine
import app.necessityq.domain.Purchase
import app.necessityq.domain.Quantity
import app.necessityq.domain.ShoppingRepository
import app.necessityq.domain.SuggestionSuppression
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Layout:
 *   households/{hid}/lines/{lineId}
 *   households/{hid}/purchases/{purchaseId}      (purchaseId = "p_" + lineId)
 *   households/{hid}/suppressions/{itemId}
 *
 * Concurrency: every edit updates only the fields it changes (field-level last-writer-wins),
 * check-off/reopen are atomic batches, and the purchase id is deterministic, so two members
 * acting at the same time converge on the same state.
 */
class FirebaseShoppingRepository(
    private val db: FirebaseFirestore,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    /** Purchases older than this are ignored by the statistics (habits change). */
    private val historyDays: Long = 400,
) : ShoppingRepository {

    private fun household(hid: String) = db.collection("households").document(hid)
    private fun lines(hid: String) = household(hid).collection("lines")
    private fun purchasesRef(hid: String) = household(hid).collection("purchases")
    private fun suppressionsRef(hid: String) = household(hid).collection("suppressions")

    override fun lines(householdId: String): Flow<List<ListLine>> =
        lines(householdId).observe().map { snap -> snap.documents.mapNotNull(FirestoreMapping::lineFrom) }

    override fun purchases(householdId: String): Flow<List<Purchase>> {
        val since = clockMillis() - historyDays * 24 * 60 * 60 * 1000
        return purchasesRef(householdId).whereGreaterThanOrEqualTo("boughtAt", since).observe()
            .map { snap -> snap.documents.mapNotNull(FirestoreMapping::purchaseFrom) }
    }

    override fun suppressions(householdId: String): Flow<List<SuggestionSuppression>> =
        suppressionsRef(householdId).observe().map { snap -> snap.documents.mapNotNull(FirestoreMapping::suppressionFrom) }

    // List edits are not awaited: the Task completes only on server acknowledgement, which would hang
    // while offline. Firestore applies them to the local cache immediately (the UI updates at once)
    // and syncs them in the background when the connection returns.

    override suspend fun addLine(householdId: String, line: ListLine) {
        lines(householdId).document(line.id).set(FirestoreMapping.lineToMap(line))
    }

    override suspend fun updateLineQuantity(householdId: String, lineId: String, quantity: Quantity?, atMillis: Long) {
        lines(householdId).document(lineId).update(
            mapOf("quantity" to FirestoreMapping.quantityToMap(quantity), "updatedAt" to atMillis),
        )
    }

    override suspend fun updateLineNote(householdId: String, lineId: String, note: String?, atMillis: Long) {
        lines(householdId).document(lineId).update(mapOf("note" to note, "updatedAt" to atMillis))
    }

    override suspend fun deleteLine(householdId: String, lineId: String) {
        lines(householdId).document(lineId).delete()
    }

    override suspend fun checkOff(householdId: String, lineId: String, purchase: Purchase) {
        db.batch()
            .update(
                lines(householdId).document(lineId),
                mapOf(
                    "status" to LineStatus.BOUGHT.name,
                    "boughtAt" to purchase.boughtAtMillis,
                    "updatedAt" to purchase.boughtAtMillis,
                ),
            )
            .set(purchasesRef(householdId).document(purchase.id), FirestoreMapping.purchaseToMap(purchase))
            .commit()
    }

    override suspend fun reopen(householdId: String, lineId: String, purchaseId: String, atMillis: Long) {
        db.batch()
            .update(
                lines(householdId).document(lineId),
                mapOf("status" to LineStatus.NEEDED.name, "boughtAt" to FieldValue.delete(), "updatedAt" to atMillis),
            )
            .delete(purchasesRef(householdId).document(purchaseId))
            .commit()
    }

    override suspend fun clearBought(householdId: String, lineIds: List<String>) {
        lineIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(lines(householdId).document(it)) }
            batch.commit()
        }
    }

    override suspend fun deletePurchase(householdId: String, purchaseId: String) {
        purchasesRef(householdId).document(purchaseId).delete()
    }

    override suspend fun suppress(householdId: String, suppression: SuggestionSuppression) {
        suppressionsRef(householdId).document(suppression.itemId)
            .set(mapOf("untilEpochDay" to suppression.untilEpochDay))
    }
}
