package app.necessityq.data.firebase

import app.necessityq.domain.Category
import app.necessityq.domain.Household
import app.necessityq.domain.RemoveReason
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

    fun recipePartsToList(parts: List<app.necessityq.domain.RecipePart>): List<Map<String, Any?>> =
        parts.map { mapOf("dish" to it.dish, "quantity" to quantityToMap(it.quantity), "menu" to it.fromMenu) }

    fun recipePartsFrom(raw: Any?): List<app.necessityq.domain.RecipePart> =
        (raw as? List<*>).orEmpty().mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val dish = map["dish"] as? String ?: return@mapNotNull null
            app.necessityq.domain.RecipePart(dish, quantityFrom(map["quantity"]), map["menu"] as? Boolean ?: false)
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
        "recipeParts" to recipePartsToList(line.recipeParts),
        "removedAt" to line.removedAtMillis,
        "removedReason" to line.removedReason?.name,
        "removedFrom" to line.removedFrom?.name,
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
            recipeParts = recipePartsFrom(doc.get("recipeParts")),
            removedAtMillis = doc.getLong("removedAt"),
            removedReason = doc.getString("removedReason")?.let { runCatching { RemoveReason.valueOf(it) }.getOrNull() },
            removedFrom = doc.getString("removedFrom")?.let { runCatching { LineStatus.valueOf(it) }.getOrNull() },
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
        val text = doc.getString("text") ?: ""
        return ItemMessage(
            id = doc.id,
            itemId = doc.getString("itemId") ?: return null,
            itemName = doc.getString("itemName") ?: "",
            authorId = doc.getString("authorId") ?: "",
            authorName = doc.getString("authorName") ?: "",
            text = text,
            atMillis = doc.getLong("at") ?: 0L,
            pending = doc.metadata.hasPendingWrites(),
            kind = doc.getString("kind")?.let { runCatching { app.necessityq.domain.MessageKind.valueOf(it) }.getOrNull() }
                ?: app.necessityq.domain.MessageKind.TEXT,
            mediaId = doc.getString("mediaId"),
            durationMs = doc.getLong("durationMs"),
            replyToId = doc.getString("replyTo"),
            editedAtMillis = doc.getLong("editedAt"),
            waveform = (doc.get("wave") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.takeIf { it.isNotEmpty() },
            archivedAtMillis = doc.getLong("archivedAt"),
        )
    }

    fun messageToMap(m: ItemMessage): Map<String, Any?> = buildMap {
        put("itemId", m.itemId); put("itemName", m.itemName); put("authorId", m.authorId)
        put("authorName", m.authorName); put("text", m.text); put("at", m.atMillis)
        if (m.kind != app.necessityq.domain.MessageKind.TEXT) put("kind", m.kind.name)
        m.mediaId?.let { put("mediaId", it) }
        m.durationMs?.let { put("durationMs", it) }
        m.replyToId?.let { put("replyTo", it) }
        m.waveform?.let { put("wave", it) }
    }

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
