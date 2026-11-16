package app.necessityq.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.text.Collator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

// ---------- stores ----------

enum class StoreType(val label: String) {
    SUPERMARKET("סופר"),
    GROCERY("מכולת"),
    OTHER("אחר"),
}

/** A place the household shops at. Coordinates are optional; with them the app can suggest the store you are in. */
data class Store(
    val id: String,
    val name: String,
    val type: StoreType,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

object Stores {
    /** Approximate distance in metres (equirectangular; accurate enough for "which shop am I standing in"). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val metersPerDegree = 111_320.0
        val dLat = (lat2 - lat1) * metersPerDegree
        val dLon = (lon2 - lon1) * metersPerDegree * cos(Math.toRadians((lat1 + lat2) / 2))
        return sqrt(dLat * dLat + dLon * dLon)
    }

    /** The closest store within [maxMeters], or null. */
    fun nearest(stores: List<Store>, latitude: Double, longitude: Double, maxMeters: Double = 250.0): Store? =
        stores.filter { it.latitude != null && it.longitude != null }
            .map { it to distanceMeters(latitude, longitude, it.latitude!!, it.longitude!!) }
            .filter { it.second <= maxMeters }
            .minByOrNull { it.second }
            ?.first
}

// ---------- per-item conversation ----------

/** One chat message about a product. A product has one conversation across all its appearances on the list. */
data class ItemMessage(
    val id: String,
    val itemId: String,
    val itemName: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val atMillis: Long,
    /** Written on this device but not yet confirmed by the server. */
    val pending: Boolean = false,
)

/** What one member has received and read. */
data class Receipt(val seenAt: Long = 0, val read: Map<String, Long> = emptyMap())

/** WhatsApp-style ticks for my own message: o written · ✓ sent · ✓✓ received (grey) · ✓✓ read (blue). */
enum class DeliveryStatus { PENDING, SENT, DELIVERED, READ }

object Receipts {
    fun status(m: ItemMessage, me: String, receipts: Map<String, Receipt>, members: Collection<String>): DeliveryStatus {
        if (m.pending) return DeliveryStatus.PENDING
        val others = members.filter { it != me && it != m.authorId }
        if (others.any { (receipts[it]?.read?.get(m.itemId) ?: 0L) >= m.atMillis }) return DeliveryStatus.READ
        if (others.any { (receipts[it]?.seenAt ?: 0L) >= m.atMillis }) return DeliveryStatus.DELIVERED
        return DeliveryStatus.SENT
    }
}

/** Who gets notified when someone flags an item as missing. */
data class NotifySettings(val missingRecipients: Set<String> = emptySet())

/**
 * Household-level extras next to the list: stores, favourites, item conversations and notification choices.
 */
interface CollabRepository {
    fun stores(householdId: String): Flow<List<Store>>
    suspend fun saveStore(householdId: String, store: Store)

    fun favorites(householdId: String): Flow<Set<String>>
    suspend fun setFavorite(householdId: String, itemId: String, favorite: Boolean)

    fun messages(householdId: String): Flow<List<ItemMessage>>
    suspend fun sendMessage(householdId: String, message: ItemMessage)

    fun notifySettings(householdId: String): Flow<NotifySettings>
    suspend fun setMissingRecipients(householdId: String, userIds: Set<String>)

    /** Per member: when they last received messages, and when they read each item's conversation. */
    fun receipts(householdId: String): Flow<Map<String, Receipt>>
    suspend fun markSeen(householdId: String, userId: String, atMillis: Long)
    suspend fun markRead(householdId: String, userId: String, itemId: String, atMillis: Long)
}

/** Non-persistent implementation for the single-device mode and tests. */
class InMemoryCollabRepository : CollabRepository {
    private val storesState = MutableStateFlow<Map<String, Store>>(emptyMap())
    private val favoritesState = MutableStateFlow<Set<String>>(emptySet())
    private val messagesState = MutableStateFlow<List<ItemMessage>>(emptyList())
    private val notifyState = MutableStateFlow(NotifySettings())

    override fun stores(householdId: String) = storesState.map { it.values.sortedBy { s -> s.name } }
    override suspend fun saveStore(householdId: String, store: Store) = storesState.update { it + (store.id to store) }
    override fun favorites(householdId: String): Flow<Set<String>> = favoritesState
    override suspend fun setFavorite(householdId: String, itemId: String, favorite: Boolean) =
        favoritesState.update { if (favorite) it + itemId else it - itemId }
    override fun messages(householdId: String): Flow<List<ItemMessage>> = messagesState
    override suspend fun sendMessage(householdId: String, message: ItemMessage) = messagesState.update { it + message }
    override fun notifySettings(householdId: String): Flow<NotifySettings> = notifyState
    override suspend fun setMissingRecipients(householdId: String, userIds: Set<String>) {
        notifyState.value = NotifySettings(userIds)
    }

    private val receiptState = MutableStateFlow<Map<String, Receipt>>(emptyMap())
    override fun receipts(householdId: String): Flow<Map<String, Receipt>> = receiptState
    override suspend fun markSeen(householdId: String, userId: String, atMillis: Long) =
        receiptState.update { it + (userId to (it[userId] ?: Receipt()).copy(seenAt = atMillis)) }
    override suspend fun markRead(householdId: String, userId: String, itemId: String, atMillis: Long) =
        receiptState.update { m ->
            val r = m[userId] ?: Receipt()
            m + (userId to r.copy(read = r.read + (itemId to atMillis)))
        }
}

// ---------- ordering, sorting, quantity steps ----------

enum class ListSort(val label: String) {
    GROUPS("לפי קבוצות"),
    ALPHA("א–ב"),
    ADDED("לפי סדר הוספה"),
    PICK("לפי סדר איסוף"),
}

/** The two ways the list is used: preparing it at home, and walking the shop. */
enum class ListMode(val label: String) { PLAN("הכנת רשימה"), SHOP("בקנייה") }

/**
 * Learns the order items are collected in: within each trip (one day, one store) the n-th checked-off
 * item gets rank n/(size-1); an item's rank is its average over trips. Trips at the chosen store are
 * preferred; with too little history there, all trips are used.
 */
object PickOrder {
    fun ranks(purchases: List<Purchase>, storeId: String?, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Map<String, Double> {
        fun build(source: List<Purchase>): Map<String, Double> {
            val sums = HashMap<String, Pair<Double, Int>>()
            source.groupBy { java.time.Instant.ofEpochMilli(it.boughtAtMillis).atZone(zone).toLocalDate() to it.storeId }
                .values.filter { it.size >= 2 }
                .forEach { trip ->
                    val ordered = trip.sortedBy { it.boughtAtMillis }
                    ordered.forEachIndexed { i, p ->
                        val r = i.toDouble() / (ordered.size - 1)
                        val (sum, n) = sums[p.itemId] ?: (0.0 to 0)
                        sums[p.itemId] = (sum + r) to (n + 1)
                    }
                }
            return sums.mapValues { (_, v) -> v.first / v.second }
        }
        val atStore = if (storeId != null) build(purchases.filter { it.storeId == storeId }) else emptyMap()
        return if (atStore.size >= 5) atStore else build(purchases) + atStore
    }
}

object ListOrdering {
    private val collator: Collator = Collator.getInstance(Locale("he", "IL"))

    /** Groups for the list screen; [ListSort.GROUPS] keeps the store-walk categories, the others use one group. */
    fun arrange(
        lines: List<ListLine>,
        sort: ListSort,
        pickRanks: Map<String, Double> = emptyMap(),
    ): List<Pair<Category?, List<ListLine>>> {
        val needed = lines.filter { it.status == LineStatus.NEEDED }
        return when (sort) {
            ListSort.GROUPS -> needed.groupBy { it.category }
                .toSortedMap(compareBy { it.order })
                .map { (c, items) -> c to items.sortedBy { it.orderKey } }
            ListSort.ALPHA -> if (needed.isEmpty()) emptyList()
            else listOf(null to needed.sortedWith { a, b -> collator.compare(a.displayName, b.displayName) })
            ListSort.ADDED -> if (needed.isEmpty()) emptyList()
            else listOf(null to needed.sortedBy { it.addedAtMillis })
            ListSort.PICK -> if (needed.isEmpty()) emptyList()
            else listOf(
                null to needed.sortedWith(
                    compareBy<ListLine>({ pickRanks[it.itemId] ?: 2.0 }, { it.category.order }, { it.addedAtMillis }),
                ),
            )
        }
    }

    /**
     * A position that places a line between two neighbours (either may be null at the ends).
     * Keys are millisecond-scale so repeated halving has plenty of room.
     */
    fun positionBetween(previous: Long?, next: Long?): Long = when {
        previous != null && next != null -> if (abs(next - previous) >= 2) previous + (next - previous) / 2 else previous
        previous != null -> previous + 1_000
        next != null -> next - 1_000
        else -> 1_000
    }
}

object QuantitySteps {
    /** One tap on +/-: whole units for counts, 100 g/ml for small measures, 0.5 for kg/litre. Never goes below the first step. */
    fun step(current: Quantity?, delta: Int): Quantity? {
        if (current == null) return if (delta > 0) Quantity.pieces(1) else null
        val size = when (current.unit) {
            QuantityUnit.PIECE, QuantityUnit.PACKAGE -> 1.0
            QuantityUnit.GRAM, QuantityUnit.MILLILITER -> 100.0
            QuantityUnit.KILOGRAM, QuantityUnit.LITER -> 0.5
        }
        val next = current.amount + delta * size
        return if (next < size - 1e-9) current else Quantity(next, current.unit)
    }
}
