package app.necessityq.data.firebase

import app.necessityq.domain.FoodMeasure
import app.necessityq.domain.FoodPart
import app.necessityq.domain.NutritionFood
import app.necessityq.domain.NutritionIndex
import app.necessityq.domain.NutritionRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Tzameret snapshot written by the `nutritionMonthly` / `refreshNutrition` functions:
 *   nutrition/meta                       { updatedAt, foods, units, recipes, bytes }
 *   Storage nutrition/tzameret.json.gz   { updatedAt, source, foods[{ c, n, e, p, f, cb, fi, na, u?, r? }] }
 * The file is cached in [cacheDir] and downloaded again only when meta.updatedAt changes.
 */
class FirebaseNutritionRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val cacheDir: File,
) : NutritionRepository {

    private var memory: Pair<String, NutritionIndex>? = null
    private val lock = Mutex()

    override suspend fun index(): NutritionIndex? = lock.withLock {
        val dir = File(cacheDir, "nutrition")
        val data = File(dir, "tzameret.json.gz")
        val stamp = File(dir, "tzameret.stamp")
        val server = runCatching { db.document("nutrition/meta").get().await().getString("updatedAt") }.getOrNull()
        memory?.let { (at, idx) -> if (server == null || server == at) return@withLock idx }
        val cachedAt = withContext(Dispatchers.IO) { if (stamp.exists() && data.exists()) stamp.readText() else null }
        if (cachedAt != null && (server == null || server == cachedAt)) {
            val idx = withContext(Dispatchers.Default) { runCatching { parse(data.readBytes()) }.getOrNull() }
            if (idx != null) {
                memory = cachedAt to idx
                return@withLock idx
            }
        }
        server ?: return@withLock null // nothing published yet and no cache
        val bytes = storage.reference.child("nutrition/tzameret.json.gz").getBytes(MAX_BYTES).await()
        val idx = withContext(Dispatchers.Default) { parse(bytes) }
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            data.writeBytes(bytes)
            stamp.writeText(server)
        }
        memory = server to idx
        idx
    }

    companion object {
        private const val MAX_BYTES = 20L * 1024 * 1024

        fun parse(gz: ByteArray): NutritionIndex {
            val text = GZIPInputStream(gz.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = Json.parseToJsonElement(text).jsonObject
            val foods = root["foods"]?.jsonArray.orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                fun d(k: String) = (o[k] as? JsonPrimitive)?.doubleOrNull
                val code = (o["c"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                val name = (o["n"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                NutritionFood(
                    code = code,
                    name = name,
                    kcal = d("e"),
                    protein = d("p"),
                    fat = d("f"),
                    carbs = d("cb"),
                    fiber = d("fi"),
                    sodiumMg = d("na"),
                    measures = (o["u"] as? JsonArray).orEmpty().mapNotNull { m ->
                        val a = m as? JsonArray ?: return@mapNotNull null
                        val n = (a.getOrNull(0) as? JsonPrimitive)?.content ?: return@mapNotNull null
                        val g = (a.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                        FoodMeasure(n, g)
                    },
                    parts = (o["r"] as? JsonArray).orEmpty().mapNotNull { p ->
                        val a = p as? JsonArray ?: return@mapNotNull null
                        val c = (a.getOrNull(0) as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                        val n = (a.getOrNull(1) as? JsonPrimitive)?.content ?: ""
                        val pct = (a.getOrNull(2) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                        FoodPart(c, n, pct)
                    },
                )
            }
            return NutritionIndex(foods, FirebasePriceRepository.millis((root["updatedAt"] as? JsonPrimitive)?.content))
        }
    }
}
