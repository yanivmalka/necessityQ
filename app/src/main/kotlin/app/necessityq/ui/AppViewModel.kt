package app.necessityq.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.necessityq.AppContainer
import app.necessityq.domain.Autocomplete
import app.necessityq.domain.ItemIdentity
import app.necessityq.domain.LineSource
import app.necessityq.domain.ListLine
import app.necessityq.domain.ListSnapshot
import app.necessityq.domain.ItemMessage
import app.necessityq.domain.ListMode
import app.necessityq.domain.MealData
import app.necessityq.domain.MealPlanner
import app.necessityq.domain.PickOrder
import kotlinx.coroutines.flow.first
import app.necessityq.domain.LineStatus
import app.necessityq.domain.ListSort
import app.necessityq.domain.Member
import app.necessityq.domain.NotifySettings
import app.necessityq.domain.Store
import app.necessityq.domain.StoreType
import app.necessityq.domain.Stores
import app.necessityq.platform.DisplayOptions
import app.necessityq.platform.LocationHelper
import app.necessityq.domain.Purchase
import app.necessityq.domain.Releases
import app.necessityq.BuildConfig
import java.io.File
import app.necessityq.domain.Quantity
import app.necessityq.domain.QuickEntryParser
import app.necessityq.domain.Session
import app.necessityq.domain.Suggestion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single screen-state holder. All data comes from realtime repository streams, so a change
 * made by any household member flows through [snapshot] and recomposes the UI — there is no
 * manual refresh and no local copy to get out of sync.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val container: AppContainer) : ViewModel() {

    val isShared: Boolean = container.household.isShared

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    val session: StateFlow<Session> = container.household.session
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Session.Loading)

    private val householdId = session.map { (it as? Session.SignedIn)?.household?.id }

    val snapshot: StateFlow<ListSnapshot?> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.service.snapshot(hid) }
        .catch { _messages.tryEmit("שגיאה בסנכרון: ${it.message}") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val purchases: StateFlow<List<Purchase>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.service.purchases(hid) }
        .map { list -> list.sortedByDescending { it.boughtAtMillis } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Quick-add text box. */
    val query = MutableStateFlow("")

    val completions: StateFlow<List<ItemIdentity>> = combine(query, snapshot) { q, snap ->
        val name = QuickEntryParser.parse(q)?.item?.displayName ?: q
        if (q.isBlank()) emptyList() else Autocomplete.complete(name, snap?.stats?.values.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun ctx(): Triple<String, String, List<ListLine>>? {
        val s = session.value as? Session.SignedIn ?: return null
        val hid = s.household?.id ?: return null
        return Triple(hid, s.userId, snapshot.value?.lines.orEmpty())
    }

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { _messages.tryEmit(it.message ?: "פעולה נכשלה") }
    }

    fun submitQuery() {
        val text = query.value
        val (hid, uid, lines) = ctx() ?: return
        if (text.isBlank()) return
        query.value = ""
        launchSafe { container.service.addFromText(hid, uid, text, lines) }
    }

    /** Picks an autocomplete entry, keeping any quantity typed in the box ("2 קוט…" → 2 × קוטג'). */
    fun pickCompletion(item: ItemIdentity) {
        val (hid, uid, lines) = ctx() ?: return
        val quantity = QuickEntryParser.parse(query.value)?.quantity
        query.value = ""
        launchSafe { container.service.add(hid, uid, item, quantity, LineSource.MANUAL, lines) }
    }

    fun accept(suggestion: Suggestion) {
        val (hid, uid, lines) = ctx() ?: return
        launchSafe { container.service.acceptSuggestion(hid, uid, suggestion, lines) }
    }

    fun dismiss(suggestion: Suggestion) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.dismissSuggestion(hid, suggestion, snapshot.value?.stats?.get(suggestion.itemId)) }
    }

    fun checkOff(line: ListLine) {
        val (hid, uid, _) = ctx() ?: return
        launchSafe { container.service.checkOff(hid, uid, line, currentStoreId.value) }
        _undo.tryEmit(UndoEvent("${line.displayName} סומן כנקנה") { reopen(line) })
    }

    fun reopen(line: ListLine) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.reopen(hid, line) }
    }

    fun changeQuantity(line: ListLine, quantity: Quantity?) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.changeQuantity(hid, line, quantity) }
    }

    fun changeNote(line: ListLine, note: String?) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.changeNote(hid, line, note) }
    }

    fun delete(line: ListLine) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.delete(hid, line) }
        _undo.tryEmit(UndoEvent("${line.displayName} הועבר לסל המחזור") { restore(line, line.status) })
    }

    fun clearBought() {
        val (hid, _, lines) = ctx() ?: return
        val bought = lines.filter { it.status == LineStatus.BOUGHT }
        launchSafe { container.service.clearBought(hid, lines) }
        if (bought.isNotEmpty()) {
            val undoChats = archiveChats(bought.map { it.itemId }, lines.filter { it.status == LineStatus.NEEDED })
            _undo.tryEmit(
                UndoEvent("${bought.size} פריטים שנקנו עברו לסל המחזור") {
                    bought.forEach { restore(it, LineStatus.BOUGHT) }
                    undoChats?.invoke()
                },
            )
        }
    }

    // ---------- list from past purchases, reset ----------

    fun proposeList(mode: app.necessityq.domain.GenerateMode): List<app.necessityq.domain.GeneratedItem> {
        val snap = snapshot.value ?: return emptyList()
        return app.necessityq.domain.ListGenerator.propose(
            mode, snap.stats.values, purchases.value, snap.lines, java.time.LocalDate.now(),
        )
    }

    fun addGenerated(items: List<app.necessityq.domain.GeneratedItem>) {
        val (hid, uid, lines) = ctx() ?: return
        if (items.isEmpty()) return
        launchSafe {
            val added = container.service.addGenerated(hid, uid, items, lines)
            _undo.tryEmit(UndoEvent("נוספו ${added.size} פריטים מקניות עבר") { launchSafe { container.service.undoGenerated(hid, added) } })
        }
    }

    fun resetList(scope: app.necessityq.domain.ResetScope) {
        val (hid, _, lines) = ctx() ?: return
        launchSafe {
            val moved = container.service.resetList(hid, scope, lines)
            if (moved.isEmpty()) {
                _messages.tryEmit("אין מה לאפס")
            } else {
                val movedIds = moved.map { it.id }.toSet()
                val undoChats = archiveChats(moved.map { it.itemId }, lines.filter { it.id !in movedIds })
                _undo.tryEmit(
                    UndoEvent("${moved.size} פריטים עברו לסל המחזור") {
                        moved.forEach { restore(it, it.status) }
                        undoChats?.invoke()
                    },
                )
            }
        }
    }

    fun deletePurchase(purchase: Purchase) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.deletePurchase(hid, purchase) }
    }

    fun importSample() {
        val (hid, uid, lines) = ctx() ?: return
        launchSafe {
            val (added, history) = container.service.importTodoSample(hid, uid, lines)
            _messages.tryEmit("נטענו $history רשומות היסטוריה ו-$added פריטים לרשימה")
        }
    }

    // ---------- display, sorting, stores, favourites ----------

    private val prefs get() = container.prefs

    val brightTheme = MutableStateFlow(prefs.brightTheme)
    val mode = MutableStateFlow(prefs.mode)
    val sort = MutableStateFlow(prefs.sortFor(prefs.mode))
    val display = MutableStateFlow(prefs.display)
    val currentStoreId = MutableStateFlow(prefs.currentStoreId)

    fun setBrightTheme(on: Boolean) { prefs.brightTheme = on; brightTheme.value = on }
    fun setSort(value: ListSort) { prefs.setSortFor(mode.value, value); sort.value = value }
    fun setMode(value: ListMode) {
        prefs.mode = value
        mode.value = value
        sort.value = prefs.sortFor(value)
        descending.value = prefs.descendingFor(value)
        checkStorePrompt()
    }

    /** Sort direction (per stage of use). */
    val descending = MutableStateFlow(prefs.descendingFor(prefs.mode))
    fun setDescending(value: Boolean) { prefs.setDescendingFor(mode.value, value); descending.value = value }

    /** Filter of the open lines (not persisted: a filter is for the moment). */
    val filter = MutableStateFlow(app.necessityq.domain.ListFilter())
    fun setFilter(value: app.necessityq.domain.ListFilter) { filter.value = value }

    /** "Where are we shopping?" — asked once at the start of a shopping trip; optional, editable later. */
    val storePrompt = MutableStateFlow(false)

    private fun checkStorePrompt() {
        val newTrip = System.currentTimeMillis() - prefs.storeAskedAt > 4L * 60 * 60 * 1000
        storePrompt.value = mode.value == ListMode.SHOP && newTrip
    }

    fun answerStorePrompt(storeId: String?, skipped: Boolean = false) {
        if (!skipped) selectStore(storeId)
        prefs.storeAskedAt = System.currentTimeMillis()
        storePrompt.value = false
    }

    val collapsed = MutableStateFlow(prefs.collapsed)
    fun toggleCollapsed(key: String) {
        val next = if (key in collapsed.value) collapsed.value - key else collapsed.value + key
        prefs.collapsed = next
        collapsed.value = next
    }

    /** Collapse every given section, or (null) expand them all. */
    fun setAllCollapsed(keys: Set<String>?) {
        val next = keys ?: emptySet()
        prefs.collapsed = next
        collapsed.value = next
    }

    /** Learned collection order for the current store (used by the "pick order" sort). */
    val pickRanks: StateFlow<Map<String, Double>> = combine(purchases, currentStoreId) { list, store ->
        PickOrder.ranks(list, store)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ---------- undo ----------

    /** A reversible action just done; the UI offers "ביטול" for a few seconds. */
    class UndoEvent(val text: String, val undo: () -> Unit)
    private val _undo = MutableSharedFlow<UndoEvent>(extraBufferCapacity = 4)
    val undo: SharedFlow<UndoEvent> = _undo

    fun restore(line: ListLine, status: LineStatus = line.removedFrom?.takeIf { it != LineStatus.REMOVED } ?: LineStatus.NEEDED) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.restore(hid, line, status) }
    }

    fun restoreToList(line: ListLine) = restore(line, LineStatus.NEEDED)

    fun emptyBin() {
        val (hid, _, lines) = ctx() ?: return
        launchSafe { container.service.purge(hid, lines) }
    }

    // ---------- meal planning (conceptual) ----------

    val meals: StateFlow<MealData> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.meals.meals(hid) }
        .catch { emit(MealData()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealData())

    fun updateMeals(change: (MealData) -> MealData) {
        val (hid, _, _) = ctx() ?: return
        val next = change(meals.value)
        launchSafe { container.meals.save(hid, next) }
    }

    /** Adds the ingredients of one week of the menu to the list, tagged with their dish. */
    fun addMenuToList(week: Int) {
        val (hid, uid, _) = ctx() ?: return
        val items = MealPlanner.ingredientsForWeek(meals.value, week)
        if (items.isEmpty()) { _messages.tryEmit("אין מאכלים בשבוע הזה"); return }
        launchSafe {
            var lines = snapshot.value?.lines.orEmpty()
            items.forEach { (ingredient, dish) ->
                val parsed = QuickEntryParser.parse(ingredient) ?: return@forEach
                container.service.add(hid, uid, parsed.item, parsed.quantity, LineSource.SUGGESTED, lines, recipe = dish)
                lines = container.service.snapshot(hid).first().lines
            }
            _messages.tryEmit("נוספו ${items.size} מרכיבים מהתפריט לרשימה")
        }
    }
    fun setDisplay(value: DisplayOptions) { prefs.display = value; display.value = value }
    fun selectStore(id: String?) { prefs.currentStoreId = id; currentStoreId.value = id }

    val stores: StateFlow<List<Store>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.stores(hid) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<Set<String>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.favorites(hid) }
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Unit prices the household entered (until a price database is connected). */
    val prices: StateFlow<Map<String, Double>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.prices(hid) }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setPrice(itemId: String, price: Double?) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.collab.setPrice(hid, itemId, price?.takeIf { it > 0 }) }
    }

    /** A known store near the device right now (set by [refreshLocation]). */
    val nearbyStore = MutableStateFlow<Store?>(null)
    private var lastLocation: android.location.Location? = null

    fun refreshLocation() {
        val location = LocationHelper.lastKnown(container.appContext) ?: return
        lastLocation = location
        nearbyStore.value = Stores.nearest(stores.value, location.latitude, location.longitude)
    }

    fun hasLocationPermission() = LocationHelper.hasPermission(container.appContext)

    /** Saves a new store at the last known position and makes it the current one. */
    fun addStore(name: String, type: StoreType) {
        val (hid, _, _) = ctx() ?: return
        val location = lastLocation ?: LocationHelper.lastKnown(container.appContext)
        val store = Store(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim().ifBlank { return },
            type = type,
            latitude = location?.latitude,
            longitude = location?.longitude,
        )
        launchSafe {
            container.collab.saveStore(hid, store)
            selectStore(store.id)
            if (location == null) _messages.tryEmit("החנות נשמרה בלי מיקום (אין הרשאת מיקום)")
        }
    }

    fun addByName(name: String) {
        val (hid, uid, lines) = ctx() ?: return
        launchSafe { container.service.addFromText(hid, uid, name, lines) }
    }

    fun toggleFavorite(itemId: String) {
        val (hid, _, _) = ctx() ?: return
        val now = itemId in favorites.value
        launchSafe { container.collab.setFavorite(hid, itemId, !now) }
    }

    fun adjustQuantity(line: ListLine, delta: Int) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.adjustQuantity(hid, line, delta) }
    }

    fun moveLine(line: ListLine, category: app.necessityq.domain.Category, previous: ListLine?, next: ListLine?) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.moveLine(hid, line, category, previous, next) }
    }

    fun setMissing(line: ListLine, missing: Boolean) {
        val (hid, uid, _) = ctx() ?: return
        launchSafe { container.service.setMissing(hid, uid, line, missing) }
        if (missing) _undo.tryEmit(UndoEvent("${line.displayName} סומן כחסר") { setMissing(line, false) })
    }

    // ---------- item conversations, notifications ----------

    val me: String get() = (session.value as? Session.SignedIn)?.userId.orEmpty()

    private val allMessages: StateFlow<List<ItemMessage>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.messages(hid) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Conversations of the current shopping event (what the list and the messages screen show). */
    val itemMessages: StateFlow<List<ItemMessage>> = allMessages
        .map { all -> all.filter { !it.isArchived } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Conversations of finished shopping events, kept until their retention period ends. */
    val archivedMessages: StateFlow<List<ItemMessage>> = allMessages
        .map { all -> all.filter { it.isArchived } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Days an ended conversation is kept; null until confirmed by the server. */
    val chatRetention: StateFlow<Int?> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.chatRetentionDays(hid) }
        .catch { }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** What is left of deleted conversations, per item. */
    val chatStats: StateFlow<Map<String, app.necessityq.domain.ChatStats>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.chatStats(hid) }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setChatRetention(days: Int) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.collab.setChatRetentionDays(hid, days) }
    }

    val notifySettings: StateFlow<NotifySettings> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.notifySettings(hid) }
        .catch { emit(NotifySettings()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, NotifySettings())

    /** uid → display name, for "added by" and chat. */
    val memberNames: StateFlow<Map<String, String>> = container.household.members
        .map { list -> list.associate { it.userId to it.displayName } }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Bumped when a conversation is opened so unread counters recompute. */
    private val readTick = MutableStateFlow(0)

    fun unreadCount(itemId: String, all: List<ItemMessage>): Int {
        val readAt = prefs.readAt(itemId)
        return all.count { it.itemId == itemId && it.authorId != me && it.atMillis > readAt }
    }

    val unreadTotal: StateFlow<Int> = combine(itemMessages, readTick) { all, _ ->
        all.filter { it.authorId != me && it.atMillis > prefs.readAt(it.itemId) }.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markRead(itemId: String) {
        val now = System.currentTimeMillis()
        prefs.markRead(itemId, now)
        readTick.value++
        val (hid, uid, _) = ctx() ?: return
        launchSafe { container.collab.markRead(hid, uid, itemId, now) }
    }

    val receipts: StateFlow<Map<String, app.necessityq.domain.Receipt>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.receipts(hid) }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun deliveryStatus(m: ItemMessage): app.necessityq.domain.DeliveryStatus =
        app.necessityq.domain.Receipts.status(m, me, receipts.value, memberNames.value.keys)

    private var lastSeenReported = 0L

    private fun authorName(uid: String) =
        memberNames.value[uid] ?: (session.value as? Session.SignedIn)?.account?.displayName ?: "אני"

    fun sendMessage(itemId: String, itemName: String, text: String, replyToId: String? = null) {
        val (hid, uid, _) = ctx() ?: return
        val clean = text.trim()
        if (clean.isEmpty()) return
        launchSafe {
            container.collab.sendMessage(
                hid,
                ItemMessage(
                    java.util.UUID.randomUUID().toString(), itemId, itemName, uid, authorName(uid), clean,
                    System.currentTimeMillis(), replyToId = replyToId,
                ),
            )
        }
    }

    /** Edits the text of an own message; only within [app.necessityq.domain.MessageRules.EDIT_WINDOW_MS] of writing it. */
    fun editMessage(message: ItemMessage, text: String) {
        val (hid, uid, _) = ctx() ?: return
        val clean = text.trim()
        if (clean == message.text) return
        if (message.kind == app.necessityq.domain.MessageKind.TEXT && clean.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!app.necessityq.domain.MessageRules.canEdit(message, uid, now)) {
            _messages.tryEmit("אפשר לערוך הודעה רק עד 15 דקות מהכתיבה"); return
        }
        launchSafe { container.collab.editMessage(hid, message.id, clean, now) }
    }

    fun deleteMessage(message: ItemMessage) {
        val (hid, uid, _) = ctx() ?: return
        if (!app.necessityq.domain.MessageRules.canDelete(message, uid)) return
        launchSafe { container.collab.deleteMessages(hid, listOf(message)) }
    }

    /** Deletes a whole conversation (the open one of an item, or an archived one). */
    fun deleteConversation(messages: List<ItemMessage>) {
        val (hid, _, _) = ctx() ?: return
        if (messages.isEmpty()) return
        launchSafe {
            container.collab.deleteMessages(hid, messages)
            _messages.tryEmit("השיחה נמחקה")
        }
    }

    /** Who received / read a message, and when. */
    fun receiptDetails(m: ItemMessage): List<app.necessityq.domain.MemberReceipt> =
        app.necessityq.domain.ReceiptDetails.of(m, memberNames.value.keys, receipts.value)

    /**
     * The shopping event of these items ended (lines cleared or list reset): their conversations leave
     * the list and wait out the retention period. Items still open on the list keep their conversation.
     */
    private fun archiveChats(itemIds: Collection<String>, remainingOpen: List<ListLine>): (() -> Unit)? {
        val (hid, _, _) = ctx() ?: return null
        val open = remainingOpen.filter { it.status == LineStatus.NEEDED }.map { it.itemId }.toSet()
        val withChat = itemMessages.value.map { it.itemId }.toSet()
        val targets = itemIds.toSet().filter { it !in open && it in withChat }
        if (targets.isEmpty()) return null
        val at = System.currentTimeMillis()
        launchSafe { targets.forEach { container.collab.archiveConversation(hid, it, at) } }
        return { launchSafe { targets.forEach { container.collab.unarchiveConversation(hid, it, at) } } }
    }

    /** Sends a picture or a recording: the bytes first, then the message that points at them. */
    fun sendMedia(
        itemId: String,
        itemName: String,
        kind: app.necessityq.domain.MessageKind,
        media: app.necessityq.domain.MediaBlob,
        caption: String = "",
        durationMs: Long? = null,
        replyToId: String? = null,
        waveform: List<Int>? = null,
    ) {
        val (hid, uid, _) = ctx() ?: return
        if (media.bytes.size > 900_000) { _messages.tryEmit("הקובץ גדול מדי לשליחה"); return }
        val mediaId = java.util.UUID.randomUUID().toString()
        mediaCache[mediaId] = media
        launchSafe {
            container.collab.saveMedia(hid, mediaId, uid, media)
            container.collab.sendMessage(
                hid,
                ItemMessage(
                    java.util.UUID.randomUUID().toString(), itemId, itemName, uid, authorName(uid), caption.trim(),
                    System.currentTimeMillis(), kind = kind, mediaId = mediaId, durationMs = durationMs, replyToId = replyToId, waveform = waveform,
                ),
            )
        }
    }

    private val mediaCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, app.necessityq.domain.MediaBlob>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, app.necessityq.domain.MediaBlob>?) = size > 40
        },
    )

    /** Loads a message's picture / recording (cached). */
    suspend fun loadMedia(mediaId: String): app.necessityq.domain.MediaBlob? {
        mediaCache[mediaId]?.let { return it }
        val (hid, _, _) = ctx() ?: return null
        return runCatching { container.collab.loadMedia(hid, mediaId) }.getOrNull()?.also { mediaCache[mediaId] = it }
    }

    val recorder = app.necessityq.platform.VoiceRecorder(container.appContext)
    val player = app.necessityq.platform.VoicePlayer(container.appContext)

    override fun onCleared() {
        recorder.stopQuietly()
        player.stop()
    }

    /** Sound for new messages — a quick toggle in the conversation and the messages screen. */
    val chatSound = MutableStateFlow(prefs.chatSound)
    fun setChatSound(on: Boolean) { prefs.chatSound = on; chatSound.value = on }

    fun setMissingRecipients(userIds: Set<String>) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.collab.setMissingRecipients(hid, userIds) }
    }

    init { checkStorePrompt() }

    init {
        // Retention: ended conversations older than the household's setting are deleted; only counts stay.
        viewModelScope.launch {
            combine(archivedMessages, chatRetention) { archived, days ->
                if (days == null) emptyList()
                else app.necessityq.domain.ConversationRetention.expired(archived, days, System.currentTimeMillis())
            }.collect { expired ->
                if (expired.isEmpty()) return@collect
                val (hid, _, _) = ctx() ?: return@collect
                runCatching { container.collab.purgeMessages(hid, expired) }
            }
        }
    }

    private var seenMessages: Set<String>? = null
    private var seenMissing: Set<String>? = null

    init {
        // Local notifications for what other members do while the app is running.
        viewModelScope.launch {
            householdId.flatMapLatest { hid ->
                seenMessages = null
                if (hid == null) emptyFlow() else container.collab.messages(hid)
            }.catch { }.collect { all ->
                // "received" receipt for messages from others
                val newest = all.filter { it.authorId != me && !it.pending }.maxOfOrNull { it.atMillis } ?: 0L
                if (newest > lastSeenReported) {
                    lastSeenReported = newest
                    ctx()?.let { (hid, uid, _) -> launchSafe { container.collab.markSeen(hid, uid, System.currentTimeMillis()) } }
                }
                val seen = seenMessages
                if (seen == null) {
                    seenMessages = all.map { it.id }.toSet()
                } else {
                    all.filter { it.id !in seen && it.authorId != me }.forEach {
                        container.notifier.notify(
                            it.id.hashCode(), "הודעה על ${it.itemName}", "${it.authorName}: ${it.preview}", sound = chatSound.value,
                        )
                    }
                    seenMessages = all.map { it.id }.toSet()
                }
            }
        }
        viewModelScope.launch {
            snapshot.collect { snap ->
                val lines = snap?.lines ?: return@collect
                val flagged = lines.filter { it.missing && it.status == LineStatus.NEEDED }.associateBy { it.id }
                val previous = seenMissing
                if (previous != null) {
                    val configured = notifySettings.value.missingRecipients
                    // nobody chosen yet = everyone gets notified
                    if (configured.isEmpty() || me in configured) {
                        flagged.values.filter { it.id !in previous && it.missingBy != me }.forEach {
                            val who = it.missingBy?.let { id -> memberNames.value[id] } ?: "מישהו"
                            container.notifier.notify(it.id.hashCode(), "חסר: ${it.displayName}", "$who סימן/ה שהמוצר חסר")
                        }
                    }
                }
                seenMissing = flagged.keys
            }
        }
    }

    // ---------- accounts & participants ----------

    val supportsAccounts: Boolean = container.household.supportsAccounts

    val members: StateFlow<List<Member>> = container.household.members
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun signInWithGoogle(idToken: String) = launchSafe {
        container.household.signInWithGoogle(idToken)
        _messages.tryEmit("התחברת עם חשבון Google")
    }

    fun signOut() = launchSafe { container.household.signOut() }
    fun updateDisplayName(name: String) = launchSafe { container.household.updateDisplayName(name) }
    fun renameHousehold(name: String) = launchSafe { container.household.renameHousehold(name) }
    fun removeMember(userId: String) = launchSafe {
        container.household.removeMember(userId)
        _messages.tryEmit("המשתתף הוסר מהבית")
    }
    fun regenerateJoinCode() = launchSafe {
        container.household.regenerateJoinCode()
        _messages.tryEmit("נוצר קוד הצטרפות חדש; הקוד הישן אינו תקף עוד")
    }
    fun reportError(message: String) { _messages.tryEmit(message) }

    // ---------- app updates from GitHub Releases ----------

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update

    val updater get() = container.updater

    init {
        checkForUpdates(manual = false)
    }

    fun checkForUpdates(manual: Boolean) {
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            runCatching { container.updater.latest() }
                .onSuccess { release ->
                    _update.value = when {
                        release == null -> UpdateState.UpToDate
                        Releases.isNewer(release.versionCode, BuildConfig.VERSION_CODE) -> {
                            if (!manual) _messages.tryEmit("יצאה גרסה חדשה ${release.versionName} — ב\"הבית\" אפשר לעדכן")
                            UpdateState.Available(release)
                        }
                        else -> UpdateState.UpToDate
                    }
                }
                .onFailure {
                    // a silent startup check must not nag when offline
                    _update.value = if (manual) UpdateState.Failed(it.message ?: "בדיקת העדכון נכשלה") else UpdateState.Idle
                }
        }
    }

    fun downloadUpdate() {
        val available = _update.value as? UpdateState.Available ?: return
        _update.value = UpdateState.Downloading(available.release, 0f)
        viewModelScope.launch {
            runCatching {
                container.updater.download(available.release) { p ->
                    _update.value = UpdateState.Downloading(available.release, p)
                }
            }.onSuccess { file -> _update.value = UpdateState.Ready(available.release, file) }
                .onFailure { _update.value = UpdateState.Failed(it.message ?: "ההורדה נכשלה") }
        }
    }

    fun createHousehold(name: String) = launchSafe { container.household.createHousehold(name) }
    fun joinHousehold(code: String) = launchSafe { container.household.joinHousehold(code) }
    fun leaveHousehold() = launchSafe { container.household.leaveHousehold() }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(container) }
        }
    }
}

/** State of the in-app update flow. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: app.necessityq.domain.ReleaseInfo) : UpdateState
    data class Downloading(val release: app.necessityq.domain.ReleaseInfo, val progress: Float) : UpdateState
    data class Ready(val release: app.necessityq.domain.ReleaseInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}
