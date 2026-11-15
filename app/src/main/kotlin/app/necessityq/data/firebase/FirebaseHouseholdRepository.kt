package app.necessityq.data.firebase

import app.necessityq.domain.Household
import app.necessityq.domain.HouseholdRepository
import app.necessityq.domain.Session
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/**
 * Anonymous Firebase sign-in + household membership.
 *
 *   users/{uid}            { householdId }
 *   households/{hid}       { name, joinCode, members[], createdAt }
 *   joinCodes/{code}       { householdId }
 *
 * A member invites others by sharing the 6-character join code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseHouseholdRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : HouseholdRepository {

    override val isShared: Boolean = true

    private val uidFlow: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        if (auth.currentUser == null) {
            auth.signInAnonymously().addOnFailureListener { close(it) }
        }
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override val session: Flow<Session> = uidFlow.flatMapLatest { uid ->
        if (uid == null) {
            flowOf<Session>(Session.Loading)
        } else {
            db.collection("users").document(uid).observe().flatMapLatest { userDoc ->
                val hid = userDoc.getString("householdId")
                if (hid == null) {
                    flowOf<Session>(Session.SignedIn(uid, null))
                } else {
                    db.collection("households").document(hid).observe()
                        .map<_, Session> { Session.SignedIn(uid, FirestoreMapping.householdFrom(it)) }
                        // removed from the household or no longer allowed to read it
                        .catch { emit(Session.SignedIn(uid, null)) }
                }
            }
        }
    }.catch { emit(Session.Error(it.message ?: "שגיאת התחברות")) }

    private fun requireUid(): String = auth.currentUser?.uid ?: error("עדיין לא מחובר")

    override suspend fun createHousehold(name: String): Household {
        val uid = requireUid()
        val householdRef = db.collection("households").document()
        val code = newJoinCode()
        val household = Household(householdRef.id, name.trim().ifBlank { "הבית שלנו" }, code, listOf(uid))
        // household first: the join-code and user documents are validated against it by the security rules
        householdRef.set(
            mapOf(
                "name" to household.name,
                "joinCode" to code,
                "members" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        db.batch()
            .set(db.collection("joinCodes").document(code), mapOf("householdId" to householdRef.id))
            .set(db.collection("users").document(uid), mapOf("householdId" to householdRef.id), SetOptions.merge())
            .commit().await()
        return household
    }

    override suspend fun joinHousehold(code: String): Household {
        val uid = requireUid()
        val normalized = code.trim().uppercase()
        val codeDoc = db.collection("joinCodes").document(normalized).get().await()
        val hid = codeDoc.getString("householdId") ?: error("קוד לא נמצא")
        val householdRef = db.collection("households").document(hid)
        householdRef.update("members", FieldValue.arrayUnion(uid)).await()
        db.collection("users").document(uid).set(mapOf("householdId" to hid), SetOptions.merge()).await()
        val snapshot = householdRef.get().await()
        return FirestoreMapping.householdFrom(snapshot) ?: error("משק הבית לא נמצא")
    }

    override suspend fun leaveHousehold() {
        val uid = requireUid()
        val userRef = db.collection("users").document(uid)
        val hid = userRef.get().await().getString("householdId") ?: return
        db.collection("households").document(hid).update("members", FieldValue.arrayRemove(uid)).await()
        userRef.update("householdId", FieldValue.delete()).await()
    }

    private fun newJoinCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O/1/I
        val random = SecureRandom()
        return (1..6).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }
}
