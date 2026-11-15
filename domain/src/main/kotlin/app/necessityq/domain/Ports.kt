package app.necessityq.domain

import kotlinx.coroutines.flow.Flow

/** Who is using the app and which household they belong to. */
sealed interface Session {
    data object Loading : Session
    data class SignedIn(val userId: String, val household: Household?) : Session
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
    suspend fun joinHousehold(code: String): Household
    suspend fun leaveHousehold()
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
    suspend fun updateLineNote(householdId: String, lineId: String, note: String?, atMillis: Long)
    suspend fun deleteLine(householdId: String, lineId: String)

    /** Atomically marks the line bought and records the purchase (purchase id is derived from the line id). */
    suspend fun checkOff(householdId: String, lineId: String, purchase: Purchase)
    /** Atomically reopens the line and removes its purchase. */
    suspend fun reopen(householdId: String, lineId: String, purchaseId: String, atMillis: Long)
    suspend fun clearBought(householdId: String, lineIds: List<String>)

    suspend fun deletePurchase(householdId: String, purchaseId: String)
    suspend fun suppress(householdId: String, suppression: SuggestionSuppression)
}

interface Clock {
    fun nowMillis(): Long
}

interface IdGenerator {
    fun newId(): String
}
