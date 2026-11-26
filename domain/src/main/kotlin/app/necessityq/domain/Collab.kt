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
import kotlin.math.roundToLong

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

enum class MessageKind { TEXT, IMAGE, AUDIO }

/** One chat message about a product. A product has one conversation across all its appearances on the list. */
data class ItemMessage(
    val id: String,
    val itemId: String,
    val itemName: String,
    val authorId: String,
    val authorName: String,
    /** The text, or the caption of a picture / recording (may be empty then). */
    val text: String,
    val atMillis: Long,
    /** Written on this device but not yet confirmed by the server. */
    val pending: Boolean = false,
    val kind: MessageKind = MessageKind.TEXT,
    /** Picture / recording bytes are stored apart from the message and loaded on demand. */
    val mediaId: String? = null,
    /** Length of a recording. */
    val durationMs: Long? = null,
    /** The message this one answers (quoted above it). */
    val replyToId: String? = null,
    /** Set when the author edited the text (see [MessageRules.EDIT_WINDOW_MS]). */
    val editedAtMillis: Long? = null,
    /** Loudness bars (0..100) of a recording, for the waveform display. */
    val waveform: List<Int>? = null,
    /** Set when the shopping event of the item ended: the conversation left the list and awaits its retention period. */
    val archivedAtMillis: Long? = null,
    /** Members tagged with "@name" (see [Mentions]); they, and the author of the quoted message, are alerted. */
    val mentions: List<String> = emptyList(),
    /** Emoji reactions, one per member: user id → emoji (see [Reactions]). */
    val reactions: Map<String, String> = emptyMap(),
    /** Set when the author deleted the message: it stays in the conversation as "ההודעה נמחקה". */
    val deletedAtMillis: Long? = null,
) {
    val isArchived: Boolean get() = archivedAtMillis != null
    val isDeleted: Boolean get() = deletedAtMillis != null

    /** One-line preview for lists, notifications and quotes. */
    val preview: String get() = if (isDeleted) "🚫 ההודעה נמחקה" else when (kind) {
        MessageKind.TEXT -> text
        MessageKind.IMAGE -> "📷 תמונה" + (if (text.isNotBlank()) ": $text" else "")
        MessageKind.AUDIO -> "🎤 הקלטה" + (durationMs?.let { " " + formatDuration(it) } ?: "")
    }

    companion object {
        fun formatDuration(ms: Long): String {
            val sec = (ms / 1000).coerceAtLeast(0)
            return "%d:%02d".format(sec / 60, sec % 60)
        }
    }
}

/** Binary attachment of a message (a compressed picture or a short voice recording). */
class MediaBlob(val mime: String, val bytes: ByteArray)

/** What one member has received and read. */
data class Receipt(val seenAt: Long = 0, val read: Map<String, Long> = emptyMap())

/** WhatsApp-style ticks for my own message: o written · ✓ sent · ✓✓ received (grey) · ✓✓ read (blue). */
enum class DeliveryStatus { PENDING, SENT, DELIVERED, READ }

object Receipts {
    /** The others who can read [m]: every member except me and the author. */
    private fun others(m: ItemMessage, me: String, members: Collection<String>) = members.filter { it != me && it != m.authorId }

    private fun hasRead(m: ItemMessage, id: String, receipts: Map<String, Receipt>) = (receipts[id]?.read?.get(m.itemId) ?: 0L) >= m.atMillis
    private fun hasSeen(m: ItemMessage, id: String, receipts: Map<String, Receipt>) = (receipts[id]?.seenAt ?: 0L) >= m.atMillis

    /** As in a WhatsApp group: blue ticks only once EVERYONE read it, grey double ticks once everyone received it. */
    fun status(m: ItemMessage, me: String, receipts: Map<String, Receipt>, members: Collection<String>): DeliveryStatus {
        if (m.pending) return DeliveryStatus.PENDING
        val others = others(m, me, members)
        if (others.isEmpty()) return DeliveryStatus.SENT
        if (others.all { hasRead(m, it, receipts) }) return DeliveryStatus.READ
        if (others.all { hasRead(m, it, receipts) || hasSeen(m, it, receipts) }) return DeliveryStatus.DELIVERED
        return DeliveryStatus.SENT
    }

    /** "read by N of M" for the message details; null when nobody else is in the household. */
    fun readProgress(m: ItemMessage, me: String, receipts: Map<String, Receipt>, members: Collection<String>): Pair<Int, Int>? {
        val others = others(m, me, members)
        if (others.isEmpty()) return null
        return others.count { hasRead(m, it, receipts) } to others.size
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
    /**
     * The whole conversation of one item, whatever the size of the household-wide window of [messages]
     * (which holds only the latest messages of all items together).
     */
    fun itemMessages(householdId: String, itemId: String): Flow<List<ItemMessage>> =
        messages(householdId).map { all -> all.filter { it.itemId == itemId } }
    suspend fun sendMessage(householdId: String, message: ItemMessage)
    /** [mentions]: the members tagged in the new text (a tag may be added or removed by the edit). */
    suspend fun editMessage(householdId: String, messageId: String, text: String, editedAtMillis: Long, mentions: List<String> = emptyList())
    /**
     * The author deletes one message: its content and attachment are removed, and it stays in the
     * conversation as "ההודעה נמחקה על ידי …" (as in WhatsApp).
     */
    suspend fun markDeleted(householdId: String, message: ItemMessage, atMillis: Long)
    /** [userId] reacts to a message with [emoji]; null removes their reaction. */
    suspend fun react(householdId: String, messageId: String, userId: String, emoji: String?)
    /** Deletes the messages and their attachments for good (no statistics are kept). */
    suspend fun deleteMessages(householdId: String, messages: List<ItemMessage>)
    /** The shopping event of the item ended: its open conversation moves out of the list. */
    suspend fun archiveConversation(householdId: String, itemId: String, atMillis: Long)
    /** Undo of [archiveConversation] (only messages archived exactly at [atMillis]). */
    suspend fun unarchiveConversation(householdId: String, itemId: String, atMillis: Long)
    /** Retention expired: deletes the messages and adds them to the item's [ChatStats]. */
    suspend fun purgeMessages(householdId: String, messages: List<ItemMessage>)
    /** Days an ended conversation is kept (0 = delete at once, -1 = keep); null until known. */
    fun chatRetentionDays(householdId: String): Flow<Int?>
    suspend fun setChatRetentionDays(householdId: String, days: Int)
    /** What remains of deleted conversations, per item. */
    fun chatStats(householdId: String): Flow<Map<String, ChatStats>>

    /** Attachments (kept out of the message documents so the conversation list stays light). */
    suspend fun saveMedia(householdId: String, mediaId: String, authorId: String, media: MediaBlob)
    suspend fun loadMedia(householdId: String, mediaId: String): MediaBlob?

    /** Price per unit (piece / package / kg / litre) the household entered, by item id — until a price database is connected. */
    fun prices(householdId: String): Flow<Map<String, Double>>
    suspend fun setPrice(householdId: String, itemId: String, price: Double?)

    fun notifySettings(householdId: String): Flow<NotifySettings>
    suspend fun setMissingRecipients(householdId: String, userIds: Set<String>)

    /** Per member: when they last received messages, and when they read each item's conversation. */
    fun receipts(householdId: String): Flow<Map<String, Receipt>>
    suspend fun markSeen(householdId: String, userId: String, atMillis: Long)
    suspend fun markRead(householdId: String, userId: String, itemId: String, atMillis: Long)

    /** Who is typing in which item conversation right now (live only; nothing without a backend). */
    fun typing(householdId: String): kotlinx.coroutines.flow.Flow<List<TypingState>> = kotlinx.coroutines.flow.flowOf(emptyList())
    /** Announces that [userId] is typing ([state]), or stopped (null). */
    suspend fun setTyping(householdId: String, userId: String, state: TypingState?) {}
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

    override suspend fun editMessage(householdId: String, messageId: String, text: String, editedAtMillis: Long, mentions: List<String>) =
        messagesState.update { list -> list.map { if (it.id == messageId) it.copy(text = text, editedAtMillis = editedAtMillis, mentions = mentions) else it } }

    override suspend fun markDeleted(householdId: String, message: ItemMessage, atMillis: Long) {
        message.mediaId?.let { media.remove(it) }
        messagesState.update { list ->
            list.map {
                if (it.id != message.id) it
                else it.copy(
                    text = "", kind = MessageKind.TEXT, mediaId = null, durationMs = null, waveform = null,
                    mentions = emptyList(), reactions = emptyMap(), deletedAtMillis = atMillis,
                )
            }
        }
    }

    override suspend fun react(householdId: String, messageId: String, userId: String, emoji: String?) =
        messagesState.update { list ->
            list.map {
                if (it.id != messageId) it
                else it.copy(reactions = if (emoji == null) it.reactions - userId else it.reactions + (userId to emoji))
            }
        }

    override suspend fun deleteMessages(householdId: String, messages: List<ItemMessage>) {
        val ids = messages.map { it.id }.toSet()
        messagesState.update { list -> list.filter { it.id !in ids } }
    }

    override suspend fun archiveConversation(householdId: String, itemId: String, atMillis: Long) =
        messagesState.update { list -> list.map { if (it.itemId == itemId && it.archivedAtMillis == null) it.copy(archivedAtMillis = atMillis) else it } }

    override suspend fun unarchiveConversation(householdId: String, itemId: String, atMillis: Long) =
        messagesState.update { list -> list.map { if (it.itemId == itemId && it.archivedAtMillis == atMillis) it.copy(archivedAtMillis = null) else it } }

    private val statsState = MutableStateFlow<Map<String, ChatStats>>(emptyMap())
    override suspend fun purgeMessages(householdId: String, messages: List<ItemMessage>) {
        val ids = messages.map { it.id }.toSet()
        val existing = messagesState.value.filter { it.id in ids }
        existing.groupBy { it.itemId }.forEach { (itemId, group) ->
            val add = ConversationRetention.statsFor(group)
            statsState.update { it + (itemId to ((it[itemId] ?: ChatStats()) + add)) }
        }
        messagesState.update { list -> list.filter { it.id !in ids } }
    }

    private val retentionState = MutableStateFlow<Int?>(ConversationRetention.DEFAULT_DAYS)
    override fun chatRetentionDays(householdId: String): Flow<Int?> = retentionState
    override suspend fun setChatRetentionDays(householdId: String, days: Int) { retentionState.value = days }
    override fun chatStats(householdId: String): Flow<Map<String, ChatStats>> = statsState

    private val media = java.util.concurrent.ConcurrentHashMap<String, MediaBlob>()
    override suspend fun saveMedia(householdId: String, mediaId: String, authorId: String, media: MediaBlob) {
        this.media[mediaId] = media
    }
    override suspend fun loadMedia(householdId: String, mediaId: String): MediaBlob? = media[mediaId]

    private val pricesState = MutableStateFlow<Map<String, Double>>(emptyMap())
    override fun prices(householdId: String): Flow<Map<String, Double>> = pricesState
    override suspend fun setPrice(householdId: String, itemId: String, price: Double?) =
        pricesState.update { if (price == null) it - itemId else it + (itemId to price) }
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

/**
 * How the list is ordered, with a line under each choice that says what it does. [hasDirection]: only an order
 * by name or by time can be turned around (ascending / descending); the others have one natural order.
 */
enum class ListSort(val label: String, val hint: String?, val hasDirection: Boolean) {
    GROUPS("לפי קבוצות", null, false),
    ALPHA("א–ב", null, true),
    ADDED("לפי סדר הוספה", null, true),
    PICK("לפי המסלול שלי בסופר", "נלמד מהסדר שבו סימנתם בקניות קודמות", false),
    CART_LOAD("כבדים קודם, קירור בסוף", "כבדים לתחתית העגלה, ירקות ולחם למעלה, קפואים אחרונים", false),
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

/**
 * One block of the list screen. [title] null = no header (a flat list); [category] is set for
 * category groups so a drag can regroup a line.
 */
data class ListSection(
    val key: String,
    val title: String?,
    val category: Category?,
    val lines: List<ListLine>,
)

object ListOrdering {
    private val collator: Collator = Collator.getInstance(Locale("he", "IL"))

    const val CHILLED_KEY = "chilled"

    /**
     * Sections for the list screen.
     *  - [ListSort.GROUPS] keeps the store-walk categories; the others are one flat block.
     *  - [ListSort.CART_LOAD] is split into the cart-loading stages of [CartLoad].
     *  - [descending] reverses the order inside every section (and the group order).
     *  - [chilledLast] moves dairy, meat/fish and frozen to the end, since they are picked up last.
     */
    fun arrange(
        lines: List<ListLine>,
        sort: ListSort,
        pickRanks: Map<String, Double> = emptyMap(),
        descending: Boolean = false,
        chilledLast: Boolean = false,
        /** In the shop what was already bought stays in its place on the list, marked, like ticks on a written list. */
        includeBought: Boolean = false,
    ): List<ListSection> {
        val needed = lines.filter { it.status == LineStatus.NEEDED || (includeBought && it.status == LineStatus.BOUGHT) }
        if (needed.isEmpty()) return emptyList()
        // a direction chosen for another sort does not turn around one that has no direction
        val descending = descending && sort.hasDirection
        fun List<ListLine>.dir() = if (descending) asReversed().toList() else this

        if (sort == ListSort.CART_LOAD) {
            return needed.groupBy { CartLoad.stageOf(it) }
                .toSortedMap(compareBy { it.ordinal })
                .map { (stage, items) ->
                    ListSection("load_" + stage.name, stage.title, null, items.sortedWith(CartLoad.withinStage).dir())
                }
        }

        if (sort == ListSort.GROUPS) {
            var groups = needed.groupBy { it.category }.entries.sortedBy { it.key.order }
            if (descending) groups = groups.asReversed()
            if (chilledLast) groups = groups.sortedBy { it.key.isChilled } // stable: keeps the order inside each part
            return groups.map { (c, items) -> ListSection(c.name, c.label, c, items.sortedBy { it.orderKey }.dir()) }
        }

        val sorted = when (sort) {
            ListSort.ALPHA -> needed.sortedWith { a, b -> collator.compare(a.displayName, b.displayName) }
            ListSort.ADDED -> needed.sortedBy { it.addedAtMillis }
            ListSort.PICK -> needed.sortedWith(
                compareBy<ListLine>({ pickRanks[it.itemId] ?: 2.0 }, { it.category.order }, { it.addedAtMillis }),
            )
            else -> needed
        }.dir()
        if (!chilledLast) return listOf(ListSection("all", null, null, sorted))
        val (cold, rest) = sorted.partition { it.category.isChilled }
        return listOfNotNull(
            rest.takeIf { it.isNotEmpty() }?.let { ListSection("all", null, null, it) },
            cold.takeIf { it.isNotEmpty() }?.let { ListSection(CHILLED_KEY, "❄ קירור והקפאה — בסוף הקנייה", null, it) },
        )
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
    /** The unit choices offered on an item, in the order shown ("ללא" = no quantity is fine too). */
    val choices: List<QuantityUnit> = listOf(QuantityUnit.PIECE, QuantityUnit.PACKAGE, QuantityUnit.KILOGRAM, QuantityUnit.GRAM, QuantityUnit.LITER, QuantityUnit.MILLILITER)

    /**
     * The unit choices that fit how [name] (in [category]) is bought, in the order of [choices]: produce, meat and fish
     * by the piece or by weight but never in litres (an avocado), drinks by the bottle or the litre but not by weight,
     * bread by the piece or the package. The unit in use ([current]) is always offered, so it can be seen and changed;
     * millilitres only then. Unknown items ([Category.OTHER]) get every choice.
     */
    fun choicesFor(name: String, category: Category, current: QuantityUnit? = null): List<QuantityUnit> {
        val words = HebrewText.matchKey(name).split(' ').filter { it.isNotBlank() }
        fun any(vararg w: String) = words.any { it in w }
        val liquid = any("שמן", "חומץ", "סילאן", "מיץ", "מים", "תרכיז", "נוזל", "סבון", "אקונומיקה", "מרכך", "שמפו", "משקה")
        val count = setOf(QuantityUnit.PIECE, QuantityUnit.PACKAGE)
        val weight = setOf(QuantityUnit.KILOGRAM, QuantityUnit.GRAM)
        val volume = setOf(QuantityUnit.LITER)
        val fit: Set<QuantityUnit> = when (category) {
            Category.PRODUCE, Category.MEAT_FISH, Category.FROZEN -> count + weight
            Category.DRINKS -> count + volume
            Category.DAIRY -> count +
                (if (words.firstOrNull() in setOf("חלב", "שמנת", "לבן", "משקה")) volume else emptySet()) +
                (if (words.firstOrNull() == "גבינה") weight else emptySet())
            Category.PANTRY -> count + (if (liquid) volume else weight)
            Category.BREAKFAST_SNACKS ->
                count + (if (any("אגוזים", "אגוזי", "שקדים", "פיצוחים", "גרעינים", "קשיו", "בוטנים", "צימוקים", "תמרים")) weight else emptySet())
            Category.CLEANING, Category.TOILETRIES -> count + (if (liquid) volume else emptySet())
            Category.BAKERY, Category.BABY -> count
            Category.OTHER -> choices.toSet() - QuantityUnit.MILLILITER
        }
        return choices.filter { it in fit || it == current }
    }

    /** A natural first amount for a unit: one piece / package / kilo / litre, half a kilo in grams. */
    fun defaultFor(unit: QuantityUnit): Quantity = when (unit) {
        QuantityUnit.GRAM, QuantityUnit.MILLILITER -> Quantity(500.0, unit)
        else -> Quantity(1.0, unit)
    }

    /**
     * Changes the kind of quantity of a line. Within a dimension the amount is converted
     * (1.5 ק"ג → 1500 גרם; 3 יח' → 3 חב'). Across dimensions nothing can be converted (how much do 15
     * cucumbers weigh?), so the household's usual amount in that unit is used, else a natural default.
     * [unit] null = no quantity ("עגבניות" alone is a valid line).
     */
    fun switchUnit(current: Quantity?, unit: QuantityUnit?, usual: Quantity? = null): Quantity? {
        if (unit == null) return null
        if (current != null && current.unit == unit) return current
        if (current != null && current.dimension == unit.dimension) {
            val amount = (current.baseAmount / unit.toBase * 1000).roundToLong() / 1000.0
            return if (amount > 0) Quantity(amount, unit) else defaultFor(unit)
        }
        if (usual != null && usual.dimension == unit.dimension) return switchUnit(usual, unit)
        return defaultFor(unit)
    }

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
