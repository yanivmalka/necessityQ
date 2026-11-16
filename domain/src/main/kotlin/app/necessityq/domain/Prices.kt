package app.necessityq.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

// ---------- price-transparency data (filled by the server-side ingestion) ----------

/** A retailer branch inside the data scope (e.g. Jerusalem). */
data class PriceBranch(
    /** `${chainKey}_${storeId}` */
    val id: String,
    val chainKey: String,
    val chainName: String,
    val storeId: String,
    val name: String,
    val address: String,
    val city: String,
    val latitude: Double?,
    val longitude: Double?,
    /** only the city centre is known */
    val approximate: Boolean = false,
) {
    val title: String get() = "$chainName · $name"
}

/** How fresh the prices of a branch are. */
data class BranchPriceStatus(
    val updatedAtMillis: Long?,
    val items: Int,
    val error: String?,
)

/** A product at one branch, with the price range of the configured window (default 90 days). */
data class BranchProduct(
    val code: String,
    val name: String,
    val brand: String,
    val packageSize: Double?,
    val unit: String,
    val weighted: Boolean,
    /** chain-internal code — not comparable with other chains */
    val internalCode: Boolean,
    val price: Double,
    val unitPrice: Double?,
    /** the current price holds since this day (yyyy-mm-dd) */
    val since: String,
    val low: Double,
    val high: Double,
    /** [day, price] changes, oldest first */
    val history: List<Pair<String, Double>> = emptyList(),
)

data class BranchPrices(
    val branchId: String,
    val updatedAtMillis: Long,
    val rangeDays: Int,
    val products: List<BranchProduct>,
)

/** A branch with its distance from the user. */
data class NearbyBranch(val branch: PriceBranch, val distanceMeters: Double?)

/** Price of one product across the household's branches. */
data class CrossBranchRange(val low: Double, val high: Double, val cheapestBranchIds: List<String>, val branches: Int)

object PriceData {
    /**
     * Branches sorted by distance from (lat, lng); those beyond [radiusKm] are dropped.
     * Without a location: alphabetical, no distance.
     */
    fun nearby(branches: List<PriceBranch>, latitude: Double?, longitude: Double?, radiusKm: Double = 15.0): List<NearbyBranch> {
        if (latitude == null || longitude == null) {
            return branches.sortedWith(compareBy({ it.chainName }, { it.name })).map { NearbyBranch(it, null) }
        }
        return branches.map { b ->
            val d = if (b.latitude != null && b.longitude != null) Stores.distanceMeters(latitude, longitude, b.latitude, b.longitude) else null
            NearbyBranch(b, d)
        }
            .filter { it.distanceMeters == null || it.distanceMeters <= radiusKm * 1000 }
            .sortedWith(compareBy<NearbyBranch> { it.distanceMeters ?: Double.MAX_VALUE }.thenBy { it.branch.title })
    }

    /** "1.2 ק\"מ" / "350 מ'" */
    fun formatDistance(meters: Double): String =
        if (meters >= 1000) String.format(java.util.Locale.US, "%.1f ק\"מ", meters / 1000) else "${(meters / 10).toInt() * 10} מ'"

    /**
     * Products whose name, brand or barcode contain every word of [query]
     * (Hebrew-normalised: quotes, geresh and hyphens ignored). Exact barcode first, then shorter names.
     */
    fun search(products: List<BranchProduct>, query: String, limit: Int = 100): List<BranchProduct> {
        val words = HebrewText.matchKey(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val code = query.trim()
        return products.asSequence()
            .filter { p ->
                if (p.code == code) return@filter true
                val hay = HebrewText.matchKey("${p.name} ${p.brand} ${p.code}")
                words.all { hay.contains(it) }
            }
            .sortedWith(compareBy<BranchProduct>({ if (it.code == code) 0 else 1 }, { it.name.length }, { it.price }))
            .take(limit)
            .toList()
    }

    /** Range of one barcode over several branches (internal chain codes are not compared). */
    fun acrossBranches(code: String, prices: Map<String, BranchPrices>): CrossBranchRange? {
        val found = prices.mapNotNull { (id, bp) -> bp.products.firstOrNull { it.code == code && !it.internalCode }?.let { id to it.price } }
        if (found.isEmpty()) return null
        val low = found.minOf { it.second }
        val high = found.maxOf { it.second }
        return CrossBranchRange(low, high, found.filter { it.second == low }.map { it.first }, found.size)
    }

    fun formatPrice(price: Double): String = String.format(java.util.Locale.US, "₪%.2f", price)

    /** "₪5.90" when the range is a single price, otherwise "₪5.50–₪6.90". */
    fun formatRange(low: Double, high: Double): String =
        if (kotlin.math.abs(high - low) < 0.005) formatPrice(low) else "${formatPrice(low)}–${formatPrice(high)}"
}

/** Port to the price data (Firestore index + per-branch snapshots in the backend). */
interface PriceRepository {
    /** All branches inside the data scope. */
    fun branches(): Flow<List<PriceBranch>>
    /** Branches the household follows (their prices are loaded daily). */
    fun selectedBranches(householdId: String): Flow<List<String>>
    suspend fun setSelectedBranches(householdId: String, branchIds: List<String>)
    fun status(branchIds: List<String>): Flow<Map<String, BranchPriceStatus>>
    /** The branch's products (cached on the device; downloaded again when the server has newer data). */
    suspend fun prices(branchId: String): BranchPrices?
    /** Asks the server to load fresh prices now (skipped by the server when they are recent). */
    suspend fun requestRefresh(branchId: String): Boolean
}

/** Used without Firebase (single-device builds): no price data. */
class NoPriceRepository : PriceRepository {
    private val selected = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    override fun branches(): Flow<List<PriceBranch>> = flowOf(emptyList())
    override fun selectedBranches(householdId: String) = selected.map { it[householdId].orEmpty() }
    override suspend fun setSelectedBranches(householdId: String, branchIds: List<String>) = selected.update { it + (householdId to branchIds) }
    override fun status(branchIds: List<String>): Flow<Map<String, BranchPriceStatus>> = flowOf(emptyMap())
    override suspend fun prices(branchId: String): BranchPrices? = null
    override suspend fun requestRefresh(branchId: String) = false
}
