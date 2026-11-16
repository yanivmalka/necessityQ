package app.necessityq.data.firebase

import app.necessityq.domain.Category
import app.necessityq.domain.Household
import app.necessityq.domain.ItemMessage
import app.necessityq.domain.Store
import app.necessityq.domain.StoreType
import app.necessityq.domain.LineSource
import app.necessityq.domain.LineStatus
import app.necessityq.domain.ListLine
import app.necessityq.domain.Purchase
import app.necessityq.domain.Quantity
import app.necessityq.domain.QuantityUnit
import app.necessityq.domain.SuggestionSuppression
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Explicit map-based mapping (no reflection) so documents stay readable in the console,
 * R8 can't break them, and unknown/old fields are tolerated.
 */
internal object FirestoreMapping {

    fun quantityToMap(q: Quantity?): Map<String, Any>? =
        q?.let { mapOf("amount" to it.amount, "unit" to it.unit.name) }

    fun quantityFrom(raw: Any?): Quantity? {
        val map = raw as? Map<*, *> ?: return null
        val amount = (map["amount"] as? Number)?.toDouble() ?: return null
        val unit = (map["unit"] as? String)?.let { runCatching { QuantityUnit.valueOf(it) }.getOrNull() } ?: return null
        return if (amount > 0) Quantity(amount, unit) else null
    }

    private fun category(raw: Any?): Category =
        (raw as? String)?.let { runCatching { Category.valueOf(it) }.getOrNull() } ?: Category.OTHER

    fun lineToMap(line: ListLine): Map<String, Any?> = mapOf(
        "itemId" to line.itemId,
        "name" to line.name,
        "variant" to line.variant,
        "category" to line.category.name,
        "quantity" to quantityToMap(line.quantity),
        "note" to line.note,
        "status" to line.status.name,
        "source" to line.source.name,
        "addedBy" to line.addedBy,
        "addedAt" to line.addedAtMillis,
        "updatedAt" to line.updatedAtMillis,
        "boughtAt" to line.boughtAtMillis,
        "position" to line.position,
        "missing" to line.missing,
        "missingBy" to line.missingBy,
        "recipe" to line.recipe,
    )

    fun lineFrom(doc: DocumentSnapshot): ListLine? {
        val itemId = doc.getString("itemId") ?: return null
        val name = doc.getString("name") ?: return null
        return ListLine(
            id = doc.id,
            itemId = itemId,
            name = name,
            variant = doc.getString("variant"),
            category = category(doc.get("category")),
            quantity = quantityFrom(doc.get("quantity")),
            note = doc.getString("note"),
            status = doc.getString("status")?.let { runCatching { LineStatus.valueOf(it) }.getOrNull() } ?: LineStatus.NEEDED,
            source = doc.getString("source")?.let { runCatching { LineSource.valueOf(it) }.getOrNull() } ?: LineSource.MANUAL,
            addedBy = doc.getString("addedBy") ?: "",
            addedAtMillis = doc.getLong("addedAt") ?: 0L,
            updatedAtMillis = doc.getLong("updatedAt") ?: 0L,
            boughtAtMillis = doc.getLong("boughtAt"),
            position = doc.getLong("position") ?: 0L,
            missing = doc.getBoolean("missing") ?: false,
            missingBy = doc.getString("missingBy"),
            recipe = doc.getString("recipe"),
        )
    }

    fun purchaseToMap(p: Purchase): Map<String, Any?> = mapOf(
        "itemId" to p.itemId,
        "name" to p.name,
        "variant" to p.variant,
        "category" to p.category.name,
        "quantity" to quantityToMap(p.quantity),
        "boughtAt" to p.boughtAtMillis,
        "boughtBy" to p.boughtBy,
        "lineId" to p.lineId,
        "storeId" to p.storeId,
    )

    fun purchaseFrom(doc: DocumentSnapshot): Purchase? {
        val itemId = doc.getString("itemId") ?: return null
        return Purchase(
            id = doc.id,
            itemId = itemId,
            name = doc.getString("name") ?: itemId,
            variant = doc.getString("variant"),
            category = category(doc.get("category")),
            quantity = quantityFrom(doc.get("quantity")),
            boughtAtMillis = doc.getLong("boughtAt") ?: return null,
            boughtBy = doc.getString("boughtBy") ?: "",
            lineId = doc.getString("lineId"),
            storeId = doc.getString("storeId"),
        )
    }

    fun storeFrom(doc: DocumentSnapshot): Store? {
        val name = doc.getString("name") ?: return null
        return Store(
            id = doc.id,
            name = name,
            type = doc.getString("type")?.let { runCatching { StoreType.valueOf(it) }.getOrNull() } ?: StoreType.OTHER,
            latitude = doc.getDouble("lat"),
            longitude = doc.getDouble("lng"),
        )
    }

    fun storeToMap(s: Store): Map<String, Any?> =
        mapOf("name" to s.name, "type" to s.type.name, "lat" to s.latitude, "lng" to s.longitude)

    fun messageFrom(doc: DocumentSnapshot): ItemMessage? {
        val text = doc.getString("text") ?: return null
        return ItemMessage(
            id = doc.id,
            itemId = doc.getString("itemId") ?: return null,
            itemName = doc.getString("itemName") ?: "",
            authorId = doc.getString("authorId") ?: "",
            authorName = doc.getString("authorName") ?: "",
            text = text,
            atMillis = doc.getLong("at") ?: 0L,
        )
    }

    fun messageToMap(m: ItemMessage): Map<String, Any?> = mapOf(
        "itemId" to m.itemId, "itemName" to m.itemName, "authorId" to m.authorId,
        "authorName" to m.authorName, "text" to m.text, "at" to m.atMillis,
    )

    fun suppressionFrom(doc: DocumentSnapshot): SuggestionSuppression? {
        val until = doc.getLong("untilEpochDay") ?: return null
        return SuggestionSuppression(doc.id, until)
    }

    @Suppress("UNCHECKED_CAST")
    fun householdFrom(doc: DocumentSnapshot): Household? {
        if (!doc.exists()) return null
        return Household(
            id = doc.id,
            name = doc.getString("name") ?: "הבית שלנו",
            joinCode = doc.getString("joinCode") ?: "",
            members = (doc.get("members") as? List<String>) ?: emptyList(),
            ownerId = doc.getString("ownerId"),
        )
    }
}
