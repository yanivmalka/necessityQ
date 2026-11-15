package app.necessityq

import android.content.Context
import app.necessityq.data.firebase.FirebaseHouseholdRepository
import app.necessityq.data.firebase.FirebaseShoppingRepository
import app.necessityq.data.local.LocalStore
import app.necessityq.domain.Clock
import app.necessityq.domain.HouseholdRepository
import app.necessityq.domain.IdGenerator
import app.necessityq.domain.ShoppingRepository
import app.necessityq.domain.ShoppingService
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import java.io.File
import java.util.UUID

/**
 * Composition root (manual DI). Chooses the shared Firebase backend when the app was built with
 * a google-services.json, otherwise the single-device local store. Everything above this line
 * depends only on the domain ports.
 */
class AppContainer(context: Context) {
    val household: HouseholdRepository
    val shopping: ShoppingRepository

    init {
        val firebase = if (BuildConfig.FIREBASE_CONFIGURED) FirebaseApp.initializeApp(context) else null
        if (firebase != null) {
            val db = FirebaseFirestore.getInstance(firebase)
            db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build()) // full offline support
                .build()
            household = FirebaseHouseholdRepository(FirebaseAuth.getInstance(firebase), db)
            shopping = FirebaseShoppingRepository(db)
        } else {
            val store = LocalStore(File(context.filesDir, "necessityq-state.json"))
            household = store.household
            shopping = store.shopping
        }
    }

    val service = ShoppingService(
        repository = shopping,
        clock = object : Clock {
            override fun nowMillis() = System.currentTimeMillis()
        },
        ids = object : IdGenerator {
            override fun newId() = UUID.randomUUID().toString()
        },
    )
}
