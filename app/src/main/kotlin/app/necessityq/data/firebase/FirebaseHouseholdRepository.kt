package app.necessityq.data.firebase

import app.necessityq.domain.Account
import app.necessityq.domain.Household
import app.necessityq.domain.HouseholdRepository
import app.necessityq.domain.Member
import app.necessityq.domain.Members
import app.necessityq.domain.Session
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import app.necessityq.domain.JoinLinks
import app.necessityq.platform.AvatarCodec
import java.security.SecureRandom

/**
 * Firebase identity (anonymous, upgradable to a Google account) + household membership.
 *
 *   users/{uid}                   { householdId, joinToken?, displayName?, googleName?, email?, photoUrl?, photo?, googlePhoto? }
 *   households/{hid}              { name, ownerId, members[], createdAt }
 *   households/{hid}/members/{uid} { displayName, email, photo }   (each member writes only their own)
 *
 * Pictures are small Base64 JPEGs: `photo` is one the user picked, `googlePhoto` a copy of the Google
 * account's picture (fetched once, so other members never need to reach Google for it).
 *   invites/{token}               { householdId, createdBy, createdAt, expiresAt, usedBy?, usedAt? }
 *
 * Joining is only through a one-time invitation link: any member creates an invitation (a long random token,
 * valid [JoinLinks.VALID_DAYS] days), and the first person who joins with it uses it up. The join is one batch —
 * mark the invitation used, add the member, set the user's pointer — which the security rules accept only together
 * (the user's transient `joinToken` names the invitation they claim). Only the owner can remove participants or
 * rename the household.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseHouseholdRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : HouseholdRepository {

    override val isShared: Boolean = true
    override val supportsAccounts: Boolean = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastProfileKey: String? = null
    @Volatile private var lastSession: Session? = null

    private val uidFlow: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            trySend(user?.uid)
            // after sign-out the device continues as a fresh guest
            if (user == null) firebaseAuth.signInAnonymously().addOnFailureListener { close(it) }
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override val session: Flow<Session> = uidFlow.flatMapLatest { uid ->
        if (uid == null) {
            flowOf<Session>(Session.Loading)
        } else {
            db.collection("users").document(uid).observe().flatMapLatest { userDoc ->
                val account = accountFrom(userDoc)
                cacheGooglePhoto(uid, userDoc)
                val hid = userDoc.getString("householdId")
                if (hid == null) {
                    flowOf<Session>(Session.SignedIn(uid, null, account))
                } else {
                    db.collection("households").document(hid).observe()
                        .map<_, Session> { Session.SignedIn(uid, FirestoreMapping.householdFrom(it), account) }
                        .onEach { s -> lastSession = s; (s as? Session.SignedIn)?.let { publishProfile(it) } }
                        // removed from the household or no longer allowed to read it
                        .catch { emit(Session.SignedIn(uid, null, account)) }
                }
            }
        }
    }.catch { emit(Session.Error(it.message ?: "שגיאת התחברות")) }

    override val members: Flow<List<Member>> = session
        .map { (it as? Session.SignedIn)?.household }
        .distinctUntilChanged()
        .flatMapLatest { household ->
            if (household == null) {
                flowOf(emptyList())
            } else {
                membersRef(household.id).observe().map { snap ->
                    val profiles = snap.documents.associate { it.id to (it.getString("displayName") to it.getString("email")) }
                    val photos = snap.documents.associate { it.id to it.getString("photo") }
                    val seen = snap.documents.associate { it.id to it.getTimestamp("lastSeen")?.toDate()?.time }
                    Members.resolve(household, profiles, photos, seen)
                }.catch { emit(Members.resolve(household, emptyMap())) }
            }
        }

    private fun membersRef(hid: String) = db.collection("households").document(hid).collection("members")

    private fun accountFrom(userDoc: DocumentSnapshot): Account {
        val user = auth.currentUser
        val custom = userDoc.getString("photo")?.takeIf { it.isNotBlank() }
        val googleName = userDoc.getString("googleName") ?: user?.takeIf { !it.isAnonymous }?.displayName
        return Account(
            displayName = userDoc.getString("displayName") ?: googleName,
            email = userDoc.getString("email") ?: user?.email,
            isAnonymous = user?.isAnonymous ?: true,
            photo = custom ?: userDoc.getString("googlePhoto")?.takeIf { it.isNotBlank() },
            googleName = googleName,
            customPhoto = custom != null,
        )
    }

    private val photoFetches = mutableSetOf<String>()

    /**
     * Accounts that signed in with Google before pictures were kept (or whose picture changed) get a
     * copy of the Google picture, once per session.
     */
    private fun cacheGooglePhoto(uid: String, userDoc: DocumentSnapshot) {
        val user = auth.currentUser ?: return
        if (user.isAnonymous || user.uid != uid) return
        val url = user.photoUrl?.toString() ?: userDoc.getString("photoUrl") ?: return
        // the picture is copied once per Google picture URL: a new URL means the picture changed
        if (userDoc.getString("googlePhoto") != null && userDoc.getString("photoUrl") == url) return
        if (!photoFetches.add(url)) return
        scope.launch {
            val photo = AvatarCodec.fromUrl(url) ?: return@launch
            runCatching {
                db.collection("users").document(uid).set(
                    mapOf(
                        "googlePhoto" to photo,
                        "photoUrl" to url,
                        "googleName" to user.displayName,
                        "email" to user.email,
                    ),
                    SetOptions.merge(),
                ).await()
            }
        }
    }

    /** Keeps this user's own member document (name, email) current so others see who they are. */
    private fun publishProfile(session: Session.SignedIn) {
        val household = session.household ?: return
        if (session.userId !in household.members) return
        val name = session.account.displayName.orEmpty()
        val photo = session.account.photo
        val key = "${household.id}|${session.userId}|$name|${session.account.email}|${photo?.hashCode()}"
        if (key == lastProfileKey) return
        lastProfileKey = key
        scope.launch {
            runCatching {
                membersRef(household.id).document(session.userId).set(
                    mapOf("displayName" to name, "email" to session.account.email, "photo" to photo),
                    SetOptions.merge(),
                ).await()
            }.onFailure { lastProfileKey = null }
        }
    }

    override suspend fun setPresence(active: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val hid = (lastSession as? Session.SignedIn)?.household?.id ?: return
        runCatching {
            membersRef(hid).document(uid).set(
                mapOf("lastSeen" to if (active) FieldValue.serverTimestamp() else FieldValue.delete()),
                SetOptions.merge(),
            ).await()
        }
    }

    private fun requireUid(): String = auth.currentUser?.uid ?: error("עדיין לא מחובר")

    private suspend fun currentHousehold(): Pair<String, Household> {
        val uid = requireUid()
        val hid = db.collection("users").document(uid).get().await().getString("householdId") ?: error("אינך משויך לבית")
        val household = FirestoreMapping.householdFrom(db.collection("households").document(hid).get().await())
            ?: error("הבית לא נמצא")
        return uid to household
    }

    override suspend fun createHousehold(name: String): Household {
        val uid = requireUid()
        val householdRef = db.collection("households").document()
        val household = Household(householdRef.id, name.trim().ifBlank { "הבית שלנו" }, listOf(uid), ownerId = uid)
        // household first: the user document is validated against it by the security rules
        householdRef.set(
            mapOf(
                "name" to household.name,
                "ownerId" to uid,
                "members" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        db.collection("users").document(uid).set(mapOf("householdId" to householdRef.id), SetOptions.merge()).await()
        return household
    }

    override suspend fun joinHousehold(inviteToken: String): Household {
        val uid = requireUid()
        val token = inviteToken.trim()
        require(JoinLinks.isToken(token)) { "הקישור אינו תקין" }
        val inviteRef = db.collection("invites").document(token)
        val invite = inviteRef.get().await()
        val hid = invite.getString("householdId") ?: error("ההזמנה לא נמצאה, או שכבר נוצלה")
        if (invite.getString("usedBy") != null) error("הקישור כבר נוצל. בקשו קישור חדש")
        val expiresAt = invite.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
        if (expiresAt <= System.currentTimeMillis()) error("תוקף הקישור פג. בקשו קישור חדש")
        val householdRef = db.collection("households").document(hid)
        val userRef = db.collection("users").document(uid)
        // one batch: the rules accept the member only together with the used-up invitation
        db.batch()
            .update(inviteRef, mapOf("usedBy" to uid, "usedAt" to FieldValue.serverTimestamp()))
            .update(householdRef, "members", FieldValue.arrayUnion(uid))
            .set(userRef, mapOf("householdId" to hid, "joinToken" to token), SetOptions.merge())
            .commit().await()
        runCatching { userRef.update("joinToken", FieldValue.delete()).await() }
        val snapshot = householdRef.get().await()
        return FirestoreMapping.householdFrom(snapshot) ?: error("משק הבית לא נמצא")
    }

    override suspend fun leaveHousehold() {
        val (uid, household) = currentHousehold()
        val householdRef = db.collection("households").document(household.id)
        runCatching { membersRef(household.id).document(uid).delete().await() }
        val others = household.members - uid
        val updates = mutableMapOf<String, Any>("members" to FieldValue.arrayRemove(uid))
        // an owner who leaves hands the household to the next member
        if (household.isOwner(uid) && others.isNotEmpty()) updates["ownerId"] = others.first()
        householdRef.update(updates).await()
        db.collection("users").document(uid).update("householdId", FieldValue.delete()).await()
    }

    override suspend fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val current = auth.currentUser
        val user = try {
            if (current != null && current.isAnonymous) {
                current.linkWithCredential(credential).await().user
            } else {
                auth.signInWithCredential(credential).await().user
            }
        } catch (e: FirebaseAuthUserCollisionException) {
            // this Google account already has a profile (another device / reinstall): switch to it
            auth.signInWithCredential(e.updatedCredential ?: credential).await().user
        } ?: error("ההתחברות נכשלה")
        db.collection("users").document(user.uid).set(
            mapOf(
                "googleName" to user.displayName,
                "email" to user.email,
                "photoUrl" to user.photoUrl?.toString(),
            ),
            SetOptions.merge(),
        ).await()
        // the session then keeps a copy of the Google picture (cacheGooglePhoto)
    }

    override suspend fun signOut() {
        lastProfileKey = null
        auth.signOut()
    }

    override suspend fun updateDisplayName(name: String) {
        val uid = requireUid()
        db.collection("users").document(uid)
            .set(mapOf("displayName" to name.trim().ifBlank { null }), SetOptions.merge()).await()
    }

    override suspend fun updatePhoto(photo: String?) {
        val uid = requireUid()
        db.collection("users").document(uid)
            .set(mapOf("photo" to (photo ?: FieldValue.delete())), SetOptions.merge()).await()
    }

    override suspend fun renameHousehold(name: String) {
        val (_, household) = currentHousehold()
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "שם הבית ריק" }
        db.collection("households").document(household.id).update("name", trimmed).await()
    }

    override suspend fun removeMember(userId: String) {
        val (uid, household) = currentHousehold()
        require(household.isOwner(uid)) { "רק מנהל הבית יכול להסיר משתתפים" }
        require(userId != uid) { "כדי לצאת בעצמך השתמשו ב\"יציאה מהבית\"" }
        db.batch()
            .update(db.collection("households").document(household.id), "members", FieldValue.arrayRemove(userId))
            .delete(membersRef(household.id).document(userId))
            .commit().await()
    }

    override suspend fun createInvite(): String {
        val (uid, household) = currentHousehold()
        val token = newInviteToken()
        val expires = java.util.Date(System.currentTimeMillis() + JoinLinks.VALID_DAYS * 24L * 60 * 60 * 1000)
        db.collection("invites").document(token).set(
            mapOf(
                "householdId" to household.id,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to com.google.firebase.Timestamp(expires),
            ),
        ).await()
        return token
    }

    /** 24 random characters (about 120 bits): cannot be guessed, so the link itself is the key. */
    private fun newInviteToken(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        val random = SecureRandom()
        return (1..24).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }
}
