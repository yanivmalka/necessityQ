package app.necessityq.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Who is using the app and which household they belong to. */
sealed interface Session {
    data object Loading : Session
    data class SignedIn(
        val userId: String,
        val household: Household?,
        val account: Account = Account(null, null, isAnonymous = true),
    ) : Session
    data class Error(val message: String) : Session
}

/**
 * Port for identity + household membership. Implemented by Firebase (shared, realtime)
 * and by a local single-device store.
 */
interface HouseholdRepository {
    val session: Flow<Session>
    /** True when data is shared across devices (Firebase is configured). */
    val isShared: Boolean
    suspend fun createHousehold(name: String): Household
    /** Joins through a one-time invitation ([inviteToken] from a join link); the invitation is used up by joining. */
    suspend fun joinHousehold(inviteToken: String): Household
    suspend fun leaveHousehold()

    /** True when real accounts (Google) and member management are available. */
    val supportsAccounts: Boolean get() = false
    /** Participants of the current household, live. */
    val members: Flow<List<Member>> get() = flowOf(emptyList())
    /** Links the current (anonymous) identity to a Google account, or switches to it if it already exists. */
    suspend fun signInWithGoogle(idToken: String): Unit = throw UnsupportedOperationException("דורש חיבור Firebase")
    /** Signs out of the Google account; the device continues as a fresh anonymous guest. */
    suspend fun signOut(): Unit = throw UnsupportedOperationException("דורש חיבור Firebase")
    /** Tells the others this member is in the app now ([active]) or left it; a heartbeat while it is in front. */
    suspend fun setPresence(active: Boolean) {}
    suspend fun updateDisplayName(name: String): Unit = throw UnsupportedOperationException("דורש חיבור Firebase")
    /** Sets the profile picture (a small Base64 JPEG); null goes back to the Google account's picture. */
    suspend fun updatePhoto(photo: String?): Unit = throw UnsupportedOperationException("דורש חיבור Firebase")
    suspend fun renameHousehold(name: String): Unit = throw UnsupportedOperationException("דורש חיבור Firebase")
    /** Owner only. */
    suspend fun removeMember(userId: String): Unit = throw UnsupportedOperationException("דורש חיבור Firebase")
    /** Any member: issues a new one-time invitation and returns its token (for [JoinLinks.link]). Valid for [JoinLinks.VALID_DAYS] days, until someone joins with it. */
    suspend fun createInvite(): String = throw UnsupportedOperationException("דורש חיבור Firebase")
}

/**
 * Port for the household's list, purchases and suggestion feedback.
 * Every write is a small, field-level change so that concurrent edits by
 * several members merge instead of overwriting each other.
 */
interface ShoppingRepository {
    fun lines(householdId: String): Flow<List<ListLine>>
    fun purchases(householdId: String): Flow<List<Purchase>>
    fun suppressions(householdId: String): Flow<List<SuggestionSuppression>>

    suspend fun addLine(householdId: String, line: ListLine)
    suspend fun updateLineQuantity(householdId: String, lineId: String, quantity: Quantity?, atMillis: Long)
    /** Someone confirmed an unusually large amount: the line is no longer flagged up to [quantity]. */
    suspend fun approveLineQuantity(householdId: String, lineId: String, quantity: Quantity, atMillis: Long)
    suspend fun updateLineNote(householdId: String, lineId: String, note: String?, atMillis: Long)
    /** Which dishes the line is for, and how much each needs. */
    suspend fun updateLineRecipes(householdId: String, lineId: String, recipe: String?, parts: List<RecipePart>, atMillis: Long)
    /** The user renamed the product: its identity (item id, name, variant, category) changes. */
    suspend fun renameLine(householdId: String, lineId: String, item: ItemIdentity, atMillis: Long)
    suspend fun deleteLine(householdId: String, lineId: String)

    /** Atomically marks the line bought and records the purchase (purchase id is derived from the line id). */
    suspend fun checkOff(householdId: String, lineId: String, purchase: Purchase)
    /** Atomically reopens the line and removes its purchase. */
    suspend fun reopen(householdId: String, lineId: String, purchaseId: String, atMillis: Long)
    /** Hard delete (recycle-bin purge). */
    suspend fun purgeLines(householdId: String, lineIds: List<String>)
    /** Soft delete into the recycle bin. */
    suspend fun removeLines(householdId: String, lines: List<ListLine>, reason: RemoveReason, atMillis: Long)
    /** Takes a line out of the recycle bin with the given status. */
    suspend fun restoreLine(householdId: String, lineId: String, status: LineStatus, atMillis: Long)

    suspend fun deletePurchase(householdId: String, purchaseId: String)
    /** Records a purchase directly (history import). Idempotent: the purchase id decides the document. */
    suspend fun addPurchase(householdId: String, purchase: Purchase)
    /** Records the household's size on purchases made before it was recorded ([Purchase.householdPeople]). */
    suspend fun setPurchasesPeople(householdId: String, purchaseIds: List<String>, people: Double)
    suspend fun suppress(householdId: String, suppression: SuggestionSuppression)
    /** Forgets what the household learned: the given purchases and the suppressions (dismissals, "יש לי בבית") of the given items. */
    suspend fun clearHistory(householdId: String, purchaseIds: List<String>, suppressedItemIds: List<String>)

    /** Manual ordering / regrouping by drag. */
    suspend fun moveLine(householdId: String, lineId: String, category: Category, position: Long, atMillis: Long)
    suspend fun setMissing(householdId: String, lineId: String, missing: Boolean, byUserId: String?, atMillis: Long)
}

interface Clock {
    fun nowMillis(): Long
}

interface IdGenerator {
    fun newId(): String
}
