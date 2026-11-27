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
    val appContext: Context = context.applicationContext
    val prefs = app.necessityq.platform.AppPrefs(context)
    val notifier = app.necessityq.platform.Notifier(context)
    val updater = app.necessityq.update.AppUpdater(context.applicationContext)
    val household: HouseholdRepository
    val shopping: ShoppingRepository
    val collab: app.necessityq.domain.CollabRepository
    val meals: app.necessityq.domain.MealRepository
    val prices: app.necessityq.domain.PriceRepository
    val nutrition: app.necessityq.domain.NutritionRepository
    /** צ'אט פוד push to tagged / quoted members; null in single-device mode. */
    val chatPush: app.necessityq.data.firebase.ChatPush?
    /** Reads quick-add text the app did not understand (Gemini, through a cloud function); null in single-device mode. */
    val interpreter: app.necessityq.domain.ItemInterpreter?
    /** Estimates the first-list survey's amounts from the ages at home (Gemini, through a cloud function); null in single-device mode. */
    val estimator: app.necessityq.domain.BaselineEstimator?
    /** Builds a whole list (products and amounts) by AI from the ages at home; null in single-device mode. */
    val suggester: app.necessityq.domain.ListSuggester?

    init {
        val firebase = if (BuildConfig.FIREBASE_CONFIGURED) FirebaseApp.initializeApp(context) else null
        if (firebase != null) {
            // crashes reach the developer from release builds only (a debug build is the developer's own)
            runCatching { com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG) }
            val db = FirebaseFirestore.getInstance(firebase)
            db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build()) // full offline support
                .build()
            household = FirebaseHouseholdRepository(FirebaseAuth.getInstance(firebase), db)
            chatPush = app.necessityq.data.firebase.ChatPush(db, com.google.firebase.functions.FirebaseFunctions.getInstance(firebase, "me-west1"))
            interpreter = app.necessityq.data.firebase.FirebaseItemInterpreter(
                com.google.firebase.functions.FirebaseFunctions.getInstance(firebase, "me-west1"),
            )
            estimator = app.necessityq.data.firebase.FirebaseBaselineEstimator(
                com.google.firebase.functions.FirebaseFunctions.getInstance(firebase, "me-west1"),
            )
            suggester = app.necessityq.data.firebase.FirebaseListSuggester(
                com.google.firebase.functions.FirebaseFunctions.getInstance(firebase, "me-west1"),
            )
            shopping = FirebaseShoppingRepository(db)
            collab = app.necessityq.data.firebase.FirebaseCollabRepository(db)
            meals = app.necessityq.data.firebase.FirebaseMealRepository(db)
            prices = app.necessityq.data.firebase.FirebasePriceRepository(
                db,
                com.google.firebase.storage.FirebaseStorage.getInstance(firebase),
                com.google.firebase.functions.FirebaseFunctions.getInstance(firebase, "me-west1"),
                context.cacheDir,
            )
            nutrition = app.necessityq.data.firebase.FirebaseNutritionRepository(
                db,
                com.google.firebase.storage.FirebaseStorage.getInstance(firebase),
                context.cacheDir,
            )
        } else {
            val store = LocalStore(File(context.filesDir, "necessityq-state.json"))
            household = store.household
            shopping = store.shopping
            collab = app.necessityq.domain.InMemoryCollabRepository()
            meals = app.necessityq.domain.InMemoryMealRepository()
            prices = app.necessityq.domain.NoPriceRepository()
            nutrition = app.necessityq.domain.NoNutritionRepository()
            chatPush = null
            interpreter = null
            estimator = null
            suggester = null
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
