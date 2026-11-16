package app.necessityq.data.firebase

import app.necessityq.domain.BranchPriceStatus
import app.necessityq.domain.BranchPrices
import app.necessityq.domain.BranchProduct
import app.necessityq.domain.PriceBranch
import app.necessityq.domain.PriceRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.time.Instant
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Price data written by the ingestion functions (firebase/functions, region me-west1):
 *   prices/branchIndex                 { updatedAt, cities[], branches[{ id, chainKey, chainName, storeId, name, address, city, lat, lng, approx }] }
 *   priceBranches/{branchId}           { updatedAt, items, error, … }   — freshness per branch
 *   Storage prices/{branchId}.json.gz  { updatedAt, rangeDays, items[{ c, n, b, q, u, w, i, p, up, s, lo, hi, h }] }
 *   households/{hid}/settings/priceBranches { kind: "priceBranches", branchIds[], preferred{itemId: barcode} } — what the server keeps fresh,
 *                                       and the product the household chose for a generic item (optional)
 * Snapshots are cached in [cacheDir] and downloaded again only when the server's updatedAt changes.
 */
class FirebasePriceRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val functions: FirebaseFunctions,
    private val cacheDir: File,
) : PriceRepository {

    private val memory = mutableMapOf<String, BranchPrices>()
    private val lock = Mutex()

    override fun branches(): Flow<List<PriceBranch>> =
        db.document("prices/branchIndex").observe().map { doc ->
            @Suppress("UNCHECKED_CAST")
            (doc.get("branches") as? List<Map<String, Any?>>).orEmpty().mapNotNull(::branchFrom)
        }

    override fun selectedBranches(householdId: String): Flow<List<String>> =
        settings(householdId).observe().map { doc ->
            @Suppress("UNCHECKED_CAST")
            (doc.get("branchIds") as? List<String>).orEmpty()
        }

    override suspend fun setSelectedBranches(householdId: String, branchIds: List<String>) {
        settings(householdId).set(mapOf("kind" to "priceBranches", "branchIds" to branchIds.distinct()), SetOptions.merge())
    }

    override fun preferredProducts(householdId: String): Flow<Map<String, String>> =
        settings(householdId).observe().map { doc ->
            val m = doc.get("preferred") as? Map<*, *> ?: return@map emptyMap<String, String>()
            m.entries.mapNotNull { e -> val k = e.key as? String; val v = e.value as? String; if (k != null && v != null) k to v else null }.toMap()
        }

    override suspend fun setPreferredProduct(householdId: String, itemId: String, code: String?) {
        if (code != null) {
            settings(householdId).set(mapOf("kind" to "priceBranches", "preferred" to mapOf(itemId to code)), SetOptions.merge())
        } else {
            settings(householdId).update(FieldPath.of("preferred", itemId), FieldValue.delete())
        }
    }

    override fun status(branchIds: List<String>): Flow<Map<String, BranchPriceStatus>> {
        if (branchIds.isEmpty()) return flowOf(emptyMap())
        // `in` queries take up to 30 values; a household follows only a few branches
        return db.collection("priceBranches").whereIn(FieldPath.documentId(), branchIds.take(30)).observe()
            .map { snap -> snap.documents.associate { it.id to statusFrom(it) } }
    }

    override suspend fun prices(branchId: String): BranchPrices? = lock.withLock {
        val status = runCatching { db.document("priceBranches/$branchId").get().await() }.getOrNull()
        val serverUpdated = status?.getString("updatedAt") ?: return@withLock memory[branchId] ?: readCache(branchId)?.second
        memory[branchId]?.takeIf { it.updatedAtMillis == millis(serverUpdated) }?.let { return@withLock it }
        readCache(branchId)?.takeIf { it.first == serverUpdated }?.let { (_, cached) ->
            memory[branchId] = cached
            return@withLock cached
        }
        val bytes = storage.reference.child("prices/$branchId.json.gz").getBytes(MAX_BYTES).await()
        withContext(Dispatchers.IO) {
            val dir = File(cacheDir, "prices").apply { mkdirs() }
            File(dir, "$branchId.json.gz").writeBytes(bytes)
            File(dir, "$branchId.stamp").writeText(serverUpdated)
        }
        parse(bytes).also { memory[branchId] = it }
    }

    override suspend fun requestRefresh(branchId: String): Boolean {
        val result = functions.getHttpsCallable("refreshBranch").call(mapOf("branchId" to branchId)).await()
        return (result.data as? Map<*, *>)?.get("refreshed") == true
    }

    private fun settings(hid: String) = db.collection("households").document(hid).collection("settings").document("priceBranches")

    private suspend fun readCache(branchId: String): Pair<String, BranchPrices>? = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "prices")
        val data = File(dir, "$branchId.json.gz")
        val stamp = File(dir, "$branchId.stamp")
        if (!data.exists() || !stamp.exists()) return@withContext null
        runCatching { stamp.readText() to parse(data.readBytes()) }.getOrNull()
    }

    companion object {
        private const val MAX_BYTES = 40L * 1024 * 1024

        fun millis(iso: String?): Long = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

        fun branchFrom(m: Map<String, Any?>): PriceBranch? {
            val id = m["id"] as? String ?: return null
            return PriceBranch(
                id = id,
                chainKey = m["chainKey"] as? String ?: "",
                chainName = m["chainName"] as? String ?: "",
                storeId = m["storeId"] as? String ?: "",
                name = m["name"] as? String ?: "",
                address = m["address"] as? String ?: "",
                city = m["city"] as? String ?: "",
                latitude = (m["lat"] as? Number)?.toDouble(),
                longitude = (m["lng"] as? Number)?.toDouble(),
                approximate = m["approx"] as? Boolean ?: false,
            )
        }

        fun statusFrom(d: DocumentSnapshot) = BranchPriceStatus(
            updatedAtMillis = d.getString("updatedAt")?.let(::millis),
            items = d.getLong("items")?.toInt() ?: 0,
            error = d.getString("error"),
        )

        /** Gzipped snapshot JSON → products. */
        fun parse(gz: ByteArray): BranchPrices {
            val text = GZIPInputStream(gz.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = Json.parseToJsonElement(text).jsonObject
            val items = root["items"]?.jsonArray.orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                fun s(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
                fun d(k: String) = (o[k] as? JsonPrimitive)?.doubleOrNull
                fun b(k: String) = (o[k] as? JsonPrimitive)?.booleanOrNull ?: false
                val price = d("p") ?: return@mapNotNull null
                BranchProduct(
                    code = s("c"),
                    name = s("n"),
                    brand = s("b"),
                    packageSize = d("q"),
                    unit = s("u"),
                    weighted = b("w"),
                    internalCode = b("i"),
                    price = price,
                    unitPrice = d("up"),
                    since = s("s"),
                    low = d("lo") ?: price,
                    high = d("hi") ?: price,
                    history = (o["h"] as? JsonArray).orEmpty().mapNotNull { pair ->
                        val arr = pair as? JsonArray ?: return@mapNotNull null
                        val day = arr.getOrNull(0)?.jsonPrimitive?.content ?: return@mapNotNull null
                        val p = arr.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                        day to p
                    },
                )
            }
            return BranchPrices(
                branchId = (root["branchId"] as? JsonPrimitive)?.content ?: "",
                updatedAtMillis = millis((root["updatedAt"] as? JsonPrimitive)?.content),
                rangeDays = (root["rangeDays"] as? JsonPrimitive)?.intOrNull ?: 90,
                products = items,
            )
        }
    }
}
