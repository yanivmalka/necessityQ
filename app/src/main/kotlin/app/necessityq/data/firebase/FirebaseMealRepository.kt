package app.necessityq.data.firebase

import app.necessityq.data.MealJson
import app.necessityq.domain.MealData
import app.necessityq.domain.MealRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Meal planning is small and edited rarely: one JSON document, households/{hid}/settings/meals. */
class FirebaseMealRepository(private val db: FirebaseFirestore) : MealRepository {
    private fun doc(hid: String) = db.collection("households").document(hid).collection("settings").document("meals")

    override fun meals(householdId: String): Flow<MealData> =
        doc(householdId).observe().map { MealJson.decode(it.getString("json")) }

    override suspend fun save(householdId: String, data: MealData) {
        doc(householdId).set(mapOf("json" to MealJson.encode(data)))
    }
}
