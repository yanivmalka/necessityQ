package app.necessityq.data.firebase

import app.necessityq.NecessityApp
import app.necessityq.platform.MentionAlerter
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.tasks.await

/**
 * צ'אט פוד push: each phone registers its push token on its user document
 * (`users/{uid}.pushTokens`), and after sending a message that tags someone or quotes their message
 * the app asks the server (`chatPush`) to push it to exactly those members.
 */
class ChatPush(private val db: FirebaseFirestore, private val functions: FirebaseFunctions) {

    /** Saves this phone's push token for [uid] (called at sign-in; the token may change at any time). */
    suspend fun register(uid: String) {
        val token = FirebaseMessaging.getInstance().token.await()
        saveToken(db, uid, token)
    }

    /** Signing out: this phone stops receiving the user's alerts. */
    suspend fun unregister(uid: String) {
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull() ?: return
        db.collection("users").document(uid).update("pushTokens", FieldValue.arrayRemove(token)).await()
    }

    /**
     * Asks the server to push message [messageId] to the members it is aimed at. Fails while the message
     * has not reached the server yet (sent offline); the caller retries.
     */
    suspend fun alert(householdId: String, messageId: String) {
        functions.getHttpsCallable("chatPush").call(mapOf("householdId" to householdId, "messageId" to messageId)).await()
    }

    companion object {
        fun saveToken(db: FirebaseFirestore, uid: String, token: String) {
            db.collection("users").document(uid).set(mapOf("pushTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
        }
    }
}

/** Receives the pushes of [ChatPush] — also when the app is closed — and alerts with sound even on silent. */
class ChatPushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        if (FirebaseApp.getApps(this).isEmpty()) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        ChatPush.saveToken(FirebaseFirestore.getInstance(), uid, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "chat") return
        val messageId = data["messageId"] ?: return
        val itemId = data["itemId"] ?: return
        // a phone shared by two accounts: only the one signed in now is alerted
        val me = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        if (data["to"] != null && me != null && data["to"] != me) return
        val prefs = (application as? NecessityApp)?.container?.prefs ?: app.necessityq.platform.AppPrefs(this)
        MentionAlerter.alert(
            context = this,
            messageId = messageId,
            itemId = itemId,
            itemName = data["itemName"].orEmpty(),
            title = data["title"] ?: "צ'אט פוד",
            text = data["body"].orEmpty(),
            loud = prefs.loudMentions,
        )
    }
}
