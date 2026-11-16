package app.necessityq.data.firebase

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Realtime listeners as cold flows. Firestore delivers the local (offline) cache first and then
 * server updates, including pending local writes — so the UI reacts instantly to the user's own
 * edits and within a moment to other members' edits.
 */
internal fun Query.observe(includeMetadata: Boolean = false): Flow<QuerySnapshot> = callbackFlow {
    val changes = if (includeMetadata) com.google.firebase.firestore.MetadataChanges.INCLUDE else com.google.firebase.firestore.MetadataChanges.EXCLUDE
    val registration = addSnapshotListener(changes) { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

internal fun DocumentReference.observe(includeMetadata: Boolean = false): Flow<DocumentSnapshot> = callbackFlow {
    val changes = if (includeMetadata) com.google.firebase.firestore.MetadataChanges.INCLUDE else com.google.firebase.firestore.MetadataChanges.EXCLUDE
    val registration = addSnapshotListener(changes) { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}
