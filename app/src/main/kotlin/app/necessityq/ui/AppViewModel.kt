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
import app.necessityq.domain.Member
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
        launchSafe { container.service.checkOff(hid, uid, line) }
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
    }

    fun clearBought() {
        val (hid, _, lines) = ctx() ?: return
        launchSafe { container.service.clearBought(hid, lines) }
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
