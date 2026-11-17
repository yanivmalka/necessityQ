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
    /** Product (barcode) the household chose for a generic item, by item id — optional precision. */
    fun preferredProducts(householdId: String): Flow<Map<String, String>>
    suspend fun setPreferredProduct(householdId: String, itemId: String, code: String?)
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
    private val preferred = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    override fun preferredProducts(householdId: String) = preferred.map { it[householdId].orEmpty() }
    override suspend fun setPreferredProduct(householdId: String, itemId: String, code: String?) = preferred.update { all ->
        val mine = all[householdId].orEmpty()
        all + (householdId to if (code == null) mine - itemId else mine + (itemId to code))
    }
}

// ---------- generic item (as the household names it) → products at a branch ----------

/** Price picture of a generic list item ("קוטג' 5%") at one branch. */
data class ItemPriceInfo(
    val branchId: String,
    /** typical range of the matching products (outliers trimmed) */
    val low: Double,
    val high: Double,
    /** matching products, cheapest first */
    val candidates: List<BranchProduct>,
    /** the product the household chose for this item, when sold at this branch */
    val preferred: BranchProduct?,
)

/** Products of a branch with their search keys computed once (searching runs per list item). */
class ProductIndex(val branchId: String, val updatedAtMillis: Long, products: List<BranchProduct>) {
    internal val entries: List<Pair<BranchProduct, String>> = products.map { it to HebrewText.matchKey("${it.name} ${it.brand}") }
    internal val byCode: Map<String, BranchProduct> = products.associateBy { it.code }
}

object ItemPrices {
    private val percent = Regex("""\d+(?:\.\d+)?%""")

    /**
     * Products matching a generic item: every word of the name must appear; percentages of the
     * variant must appear as whole numbers ("5%" does not match "15%"). When some products start with
     * the item name ("קוטג' 5% טרה"), products that only mention it ("פסטה עם קוטג'") are dropped.
     */
    fun candidates(index: ProductIndex, name: String, variant: String?): List<BranchProduct> {
        val nameKey = HebrewText.matchKey(name)
        val words = nameKey.split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val variantKey = variant?.let { HebrewText.matchKey(it) }.orEmpty()
        val percents = percent.findAll(variantKey).map { it.value }.toList()
        val otherVariantWords = percent.replace(variantKey, " ").split(' ').filter { it.isNotBlank() }
        val matches = index.entries.filter { (_, key) ->
            words.all { key.contains(it) } &&
                otherVariantWords.all { key.contains(it) } &&
                percents.all { p -> Regex("""(?<![\d.])${Regex.escape(p)}""").containsMatchIn(key) }
        }
        val leading = matches.filter { (_, key) -> key.startsWith(nameKey) }
        return (leading.ifEmpty { matches }).map { it.first }.sortedBy { it.price }
    }

    /** Typical range: the cheapest and dearest 10% are left out (from ten products on), otherwise min–max. */
    fun typicalRange(prices: List<Double>): Pair<Double, Double>? {
        if (prices.isEmpty()) return null
        val sorted = prices.sorted()
        val trim = sorted.size / 10
        return sorted[trim] to sorted[sorted.size - 1 - trim]
    }

    fun info(index: ProductIndex, name: String, variant: String?, preferredCode: String?): ItemPriceInfo? {
        val preferred = preferredCode?.let { index.byCode[it] }
        val candidates = candidates(index, name, variant)
        val range = typicalRange(candidates.map { it.price }) ?: preferred?.let { it.price to it.price } ?: return null
        return ItemPriceInfo(index.branchId, range.first, range.second, candidates, preferred)
    }

    /** Distinct brands among the candidates (for the optional "choose exactly" filter). */
    fun brands(candidates: List<BranchProduct>): List<String> =
        candidates.map { it.brand.trim() }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }

    /** Fat / strength percentages seen in the candidates' names ("3%", "5%", "9%"). */
    fun percents(candidates: List<BranchProduct>): List<String> =
        candidates.flatMap { percent.findAll(it.name).map { m -> m.value }.toList() }.distinct()
            .sortedBy { it.removeSuffix("%").toDoubleOrNull() ?: 0.0 }

    fun hasPercent(product: BranchProduct, p: String): Boolean =
        Regex("""(?<![\d.])${Regex.escape(p)}""").containsMatchIn(product.name)
}

/** Estimated cost of the open lines as a range: the household's own prices, else the branch data. */
data class ListCostEstimate(val low: Double, val high: Double, val pricedLines: Int, val openLines: Int) {
    val complete: Boolean get() = openLines > 0 && pricedLines == openLines
    val isRange: Boolean get() = high - low >= 0.005
}

object ListCost {
    /**
     * Packages to buy for a branch price: counted units as is; weights only for products sold by weight.
     * Null when it cannot be priced: produce sold by weight but counted in pieces ("7 לימונים") —
     * the weight of a piece is not known, so no number is made up.
     */
    fun packages(quantity: Quantity?, weighted: Boolean): Double? = when (quantity?.unit) {
        null -> 1.0
        QuantityUnit.PIECE, QuantityUnit.PACKAGE -> if (weighted && quantity.unit == QuantityUnit.PIECE) null else quantity.amount
        QuantityUnit.KILOGRAM, QuantityUnit.LITER -> if (weighted) quantity.amount else 1.0
        QuantityUnit.GRAM, QuantityUnit.MILLILITER -> if (weighted) quantity.amount / 1000.0 else 1.0
    }

    fun of(lines: List<ListLine>, householdPrices: Map<String, Double>, branch: Map<String, ItemPriceInfo>): ListCostEstimate {
        val open = lines.filter { it.status == LineStatus.NEEDED }
        var low = 0.0
        var high = 0.0
        var priced = 0
        for (line in open) {
            val own = householdPrices[line.itemId]
            val info = branch[line.itemId]
            when {
                own != null -> (own * PriceEstimate.units(line.quantity)).let { low += it; high += it }
                info?.preferred != null -> {
                    val n = packages(line.quantity, info.preferred.weighted) ?: continue
                    (info.preferred.price * n).let { low += it; high += it }
                }
                info != null -> {
                    val n = packages(line.quantity, info.candidates.firstOrNull()?.weighted == true) ?: continue
                    low += info.low * n
                    high += info.high * n
                }
                else -> continue
            }
            priced++
        }
        return ListCostEstimate(low, high, priced, open.size)
    }
}
