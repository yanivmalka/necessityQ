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
    /**
     * The file's `UnitOfMeasure`: what [price] is per when sold by weight ("100 גרם", "ק\"ג"). Blank in
     * snapshots written before it was kept; [unit] is used then.
     */
    val measure: String = "",
) {
    /**
     * Priced per kilo. Many chains don't set the "weighted" flag on produce; a chain-internal (or short) code
     * whose unit is a kilo counts too. Packaged goods of 1 kg carry a real barcode, so they are not caught.
     */
    val soldByWeight: Boolean
        get() = weighted || (PieceWeights.isKiloUnit(unit) && (internalCode || code.length <= 7))

    /** Kilos [price] is for: 0.1 for "100 גרם", 1 for "ק\"ג"; null when the unit is not recognised. */
    val priceUnitKilos: Double?
        get() = WeightUnits.kilos(measure.ifBlank { unit })

    /**
     * [price] per kilo for a product sold by weight (×10 when priced per 100 g). Null when not sold by weight,
     * or when its unit is not recognised — then the price is not multiplied by a weight at all.
     */
    val pricePerKilo: Double?
        get() = if (soldByWeight) priceUnitKilos?.let { price / it } else null
}

/**
 * Weight units of the price files. A weighted item's price is per `UnitOfMeasure`, which is not always a kilo:
 * "קילוגרם", "ק\"ג", "1קילוגרם", "100 גרם", with any spacing.
 */
object WeightUnits {
    private val kilo = setOf("קג", "קילו", "קילוגרם", "kg")
    private val gram = setOf("גרם", "גר", "ג", "g", "gr")
    private val form = Regex("""^(?:ל\s*)?(\d+(?:\.\d+)?)?\s*(\D+)$""")

    /**
     * Kilos a price unit stands for, or null when it is not a recognised weight. Ambiguous forms are not
     * guessed: "גרם" with no amount, or a kilo unit with an amount other than 1 (e.g. "100 ק\"ג").
     */
    fun kilos(unit: String): Double? {
        val k = HebrewText.matchKey(unit)
        val m = form.matchEntire(k) ?: return null
        val amount = m.groupValues[1].takeIf { it.isNotEmpty() }?.toDouble()
        val name = m.groupValues[2].replace(".", "").trim()
        return when (name) {
            in kilo -> if (amount == null || amount == 1.0) 1.0 else null
            in gram -> amount?.takeIf { it > 0 }?.let { it / 1000.0 }
            else -> null
        }
    }
}

data class BranchPrices(
    val branchId: String,
    val updatedAtMillis: Long,
    val rangeDays: Int,
    val products: List<BranchProduct>,
)

/** A branch with its distance from the user. */
data class NearbyBranch(val branch: PriceBranch, val distanceMeters: Double?)

/**
 * One product at one branch with its previous price (null = no change on record). Both are in the price unit of the
 * first branch that sells it (see [PriceData.quotes]); when it can't be put in that unit ([comparable] false) they are
 * the branch's own, and it is not compared.
 */
data class BranchQuote(val branch: PriceBranch, val price: Double, val previous: Double?, val comparable: Boolean = true) {
    /** positive = went up, negative = went down, null/zero = unchanged */
    val change: Double? get() = previous?.let { price - it }?.takeIf { kotlin.math.abs(it) >= 0.005 }
}

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
        val stems = HebrewStem.words(query)
        val code = query.trim()
        return products.asSequence()
            .filter { p ->
                if (p.code == code) return@filter true
                val hay = HebrewText.matchKey("${p.name} ${p.brand} ${p.code}")
                if (words.all { hay.contains(it) }) return@filter true
                // singular/plural: "עגבניות" finds "עגבניה"
                val hayStems = HebrewStem.words("${p.name} ${p.brand}")
                stems.all { s -> HebrewStem.matchesAny(hayStems, s) }
            }
            .sortedWith(compareBy<BranchProduct>({ if (it.code == code) 0 else 1 }, { it.name.length }, { it.price }))
            .take(limit)
            .toList()
    }

    /**
     * [search] over a branch's [ProductIndex] (its keys and stems are computed once, so this is a scan of strings). Two
     * passes: [stems] false = the words are contained in the name, brand or barcode (fast, nearly always what the user
     * meant); true = only the products the first pass did not find, by their stems ("עגבניות" finds "עגבניה").
     */
    fun searchIndexed(index: ProductIndex, query: String, limit: Int = 100, stems: Boolean = false): List<BranchProduct> {
        val words = HebrewText.matchKey(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val queryStems = if (stems) HebrewStem.words(query) else emptyList()
        val code = query.trim()
        val hits = ArrayList<BranchProduct>()
        for (i in index.entries.indices) {
            val (p, key) = index.entries[i]
            val direct = p.code == code || words.all { key.contains(it) || p.code.contains(it) }
            val hit = if (stems) !direct && queryStems.all { s -> HebrewStem.matchesAny(index.stems[i], s) } else direct
            if (hit) hits += p
        }
        return hits.sortedWith(compareBy<BranchProduct>({ if (it.code == code) 0 else 1 }, { it.name.length }, { it.price })).take(limit)
    }

    /**
     * Range of one barcode over several branches (internal chain codes are not compared), in the price unit of the
     * first branch that sells it (see [rangeIn]).
     */
    fun acrossBranches(code: String, prices: Map<String, BranchPrices>): CrossBranchRange? {
        val found = prices.mapNotNull { (id, bp) -> bp.products.firstOrNull { it.code == code && !it.internalCode }?.let { id to it } }
        return rangeIn(found.firstOrNull()?.second ?: return null, found)
    }

    /**
     * Range of one product over branches ([at]: branch id → the product there), in the price unit of [reference]
     * (per 100 g, per kg or per package; see [PriceComparisons.inUnitOf]). Branches where it can't be put in that unit
     * are left out; null when none is left.
     */
    fun rangeIn(reference: BranchProduct, at: List<Pair<String, BranchProduct>>): CrossBranchRange? {
        val found = at.mapNotNull { (id, p) -> inUnitOf(reference, p)?.let { id to it } }
        if (found.isEmpty()) return null
        val low = found.minOf { it.second }
        val high = found.maxOf { it.second }
        return CrossBranchRange(low, high, found.filter { it.second == low }.map { it.first }, found.size)
    }

    /** [other]'s price in [reference]'s price unit; the reference itself is always its own price. */
    internal fun inUnitOf(reference: BranchProduct, other: BranchProduct): Double? =
        if (other == reference) other.price else PriceComparisons.inUnitOf(reference, other)

    /**
     * The chosen product at each of [branches] (in their order), like a stock ticker: today's price and the
     * one before its last change. Barcodes are compared across chains; a chain-internal code only within
     * [homeChain]. Branches whose prices are not loaded, or that don't sell the product, are left out.
     * Prices are in the price unit of the first branch listed (a chain publishing it per kg next to one publishing
     * it per 100 g); a branch where it can't be put in that unit keeps its own prices, marked not comparable.
     */
    fun quotes(code: String, internalCode: Boolean, homeChain: String, branches: List<PriceBranch>, prices: Map<String, BranchPrices>): List<BranchQuote> {
        val found = branches.mapNotNull { b ->
            if (internalCode && b.chainKey != homeChain) return@mapNotNull null
            prices[b.id]?.products?.firstOrNull { it.code == code }?.let { b to it }
        }
        val reference = found.firstOrNull()?.second ?: return emptyList()
        return found.map { (b, p) ->
            // the same factor turns today's and the previous price into the reference's unit
            val scale = inUnitOf(reference, p)?.takeIf { p.price > 0 }?.let { it / p.price }
            if (scale == null) BranchQuote(b, p.price, previousPrice(p), comparable = false)
            else BranchQuote(b, p.price * scale, previousPrice(p)?.let { it * scale })
        }
    }

    /** The price before the current one ([BranchProduct.history] ends with the current price, or close to it). */
    fun previousPrice(p: BranchProduct): Double? =
        p.history.map { it.second }.dropLastWhile { kotlin.math.abs(it - p.price) < 0.005 }.lastOrNull()

    /**
     * "5.90 ₪": the amount, then the shekel sign on its right. Wrapped in left-to-right marks so that inside
     * Hebrew (right-to-left) text the whole price stays one left-to-right unit and the sign is never moved.
     */
    fun formatPrice(price: Double, decimals: Int = 2): String = ltr(amount(price, decimals) + NBSP + SHEKEL)

    /**
     * "5.90 ₪" when the range is a single price, otherwise "5.50–6.90 ₪": cheap on the left, dear on the right,
     * one shekel sign at the far right. [prefix] (e.g. "~") goes on the left, inside the same unit.
     */
    fun formatRange(low: Double, high: Double, decimals: Int = 2, prefix: String = ""): String =
        if (kotlin.math.abs(high - low) < 0.5 * Math.pow(10.0, -decimals.toDouble())) ltr(prefix + amount(low, decimals) + NBSP + SHEKEL)
        else ltr(prefix + amount(minOf(low, high), decimals) + "–" + amount(maxOf(low, high), decimals) + NBSP + SHEKEL)

    private const val SHEKEL = "₪"
    private const val NBSP = "\u00A0"
    private const val LRM = "\u200E"
    private fun ltr(text: String) = LRM + text + LRM
    private fun amount(value: Double, decimals: Int) = String.format(java.util.Locale.US, "%.${decimals}f", value)
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
    /** Running promotions of a branch (cached on the device; downloaded again when the server has newer ones). */
    suspend fun promotions(branchId: String): BranchPromotions? = null
    /** When each branch's promotions were last loaded by the server (millis), to reload them when they change. */
    fun promotionsStatus(branchIds: List<String>): Flow<Map<String, Long>> = flowOf(emptyMap())
    /** Asks the server to check promotions and stock of these branches now (skipped for those checked in the last 30 minutes). */
    suspend fun requestOffersRefresh(branchIds: List<String>) {}
    /** The cached products of a branch, read without asking the server (null when nothing is cached). */
    suspend fun cachedPrices(branchId: String): BranchPrices? = null
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
    /** typical range of the matching products (outliers trimmed); per kilo when [perKilo] */
    val low: Double,
    val high: Double,
    /** matching products, cheapest first (per kilo for those sold by weight) */
    val candidates: List<BranchProduct>,
    /** the product the household chose for this item, when sold at this branch */
    val preferred: BranchProduct?,
    /**
     * [low]–[high] is a price per kilo: every candidate is sold by weight and at least one has a recognised
     * price unit. False for packaged goods, and for products by weight whose price unit is not known.
     */
    val perKilo: Boolean = false,
)

/**
 * The weight a price by weight is shown for, as the household chose: per kilo or per 100 grams. Every price of a
 * product sold by weight is shown in it (the list, the item page, the comparison between branches).
 */
enum class WeightPriceUnit(val kilos: Double, val label: String, val title: String) {
    KILO(1.0, "לק\"ג", "לקילוגרם"),
    HUNDRED_GRAMS(0.1, "ל-100 ג'", "ל-100 גרם"),
    ;

    /** A price per kilo, in this unit. */
    fun fromKilo(perKilo: Double): Double = perKilo * kilos

    /** A price for [fromKilos] kilos (0.1 = per 100 g), in this unit. */
    fun convert(price: Double, fromKilos: Double): Double = price / fromKilos * kilos

    /** [p]'s price in this unit when it is sold by weight and its price unit is known; null otherwise. */
    fun of(p: BranchProduct): Double? = p.pricePerKilo?.let(::fromKilo)
}

/**
 * The price shown next to an item: always for the basic amount, not for the quantity on the list. Packaged goods per
 * unit ("ליח'"), goods sold by weight per kilo or per 100 g ([WeightPriceUnit]).
 */
data class UnitPrice(val low: Double, val high: Double, val per: String)

object UnitPrices {
    const val PER_UNIT = "ליח'"

    /**
     * [info]'s basic price: the chosen product's, else the typical range. Null when the item is sold by weight but per
     * what weight is not known (no price rather than a wrong one).
     */
    fun of(info: ItemPriceInfo, unit: WeightPriceUnit): UnitPrice? {
        val p = info.preferred
        return when {
            p != null && p.soldByWeight -> unit.of(p)?.let { UnitPrice(it, it, unit.label) }
            p != null -> UnitPrice(p.price, p.price, PER_UNIT)
            info.perKilo -> UnitPrice(unit.fromKilo(info.low), unit.fromKilo(info.high), unit.label)
            ItemPrices.byWeight(info.candidates) -> null
            else -> UnitPrice(info.low, info.high, PER_UNIT)
        }
    }

    /** One product's price as shown: by weight in [unit] (when its price unit is known), otherwise as published. */
    fun of(p: BranchProduct, unit: WeightPriceUnit): UnitPrice =
        unit.of(p)?.let { UnitPrice(it, it, unit.label) } ?: UnitPrice(p.price, p.price, if (p.soldByWeight) "" else PER_UNIT)
}

/** Products of a branch with their search keys computed once (searching runs per list item). */
class ProductIndex(val branchId: String, val updatedAtMillis: Long, products: List<BranchProduct>) {
    internal val entries: List<Pair<BranchProduct, String>> = products.map { it to HebrewText.matchKey("${it.name} ${it.brand}") }
    internal val stems: List<List<String>> = products.map { HebrewStem.words(it.name + " " + it.brand) }
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
        // stems, so the generic plural matches the files' singular ("עגבניות" ↔ "עגבניה", "מלפפונים" ↔ "מלפפון")
        val stems = HebrewStem.words(name)
        if (stems.isEmpty()) return emptyList()
        val variantKey = variant?.let { HebrewText.matchKey(it) }.orEmpty()
        val percents = percent.findAll(variantKey).map { it.value }.toList()
        val otherVariantStems = HebrewStem.words(percent.replace(variantKey, " "))
        fun has(words: List<String>, stem: String) = HebrewStem.matchesAny(words, stem)
        val matches = index.entries.indices.filter { i ->
            val (_, key) = index.entries[i]
            val words = index.stems[i]
            stems.all { has(words, it) } &&
                otherVariantStems.all { has(words, it) } &&
                percents.all { p -> Regex("""(?<![\d.])${Regex.escape(p)}""").containsMatchIn(key) }
        }
        val leading = matches.filter { i -> index.stems[i].firstOrNull()?.startsWith(stems.first()) == true || index.entries[i].second.startsWith(nameKey) }
        val found = leading.ifEmpty { matches }.map { index.entries[it].first }
        // produce: the fresh item sold by the kilo, not pickles or juice that share its name
        val fresh = if (PieceWeights.gramsPerPiece(name) != null) found.filter { it.soldByWeight } else emptyList()
        // by weight, cheapest per kilo (4.90 per 100 g is dearer than 39 per kg); unknown price units last
        return fresh.ifEmpty { found }.sortedWith(compareBy({ it.soldByWeight && it.pricePerKilo == null }, { it.pricePerKilo ?: it.price }))
    }

    /** Every product is sold by weight: its price is per a weight, not per package. */
    fun byWeight(products: List<BranchProduct>): Boolean = products.isNotEmpty() && products.all { it.soldByWeight }

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
        // sold by weight: the range is per kilo (a price per 100 g counts ×10), leaving out unknown price units;
        // when none is known, the prices as published (and perKilo = false, so nobody reads them per kilo)
        val perKilo = if (byWeight(candidates)) candidates.mapNotNull { it.pricePerKilo } else emptyList()
        val range = typicalRange(perKilo.ifEmpty { candidates.map { it.price } }) ?: preferred?.let { it.price to it.price } ?: return null
        return ItemPriceInfo(index.branchId, range.first, range.second, candidates, preferred, perKilo = perKilo.isNotEmpty())
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
     * Produce sold by weight but counted in pieces ("7 לימונים") uses the typical weight of a piece
     * ([gramsPerPiece], see [PieceWeights]); null when that is not known, so no number is made up.
     */
    fun packages(quantity: Quantity?, weighted: Boolean, gramsPerPiece: Double? = null): Double? = when (quantity?.unit) {
        null -> 1.0
        QuantityUnit.PIECE, QuantityUnit.PACKAGE ->
            if (weighted && quantity.unit == QuantityUnit.PIECE) gramsPerPiece?.let { quantity.amount * it / 1000.0 } else quantity.amount
        QuantityUnit.KILOGRAM, QuantityUnit.LITER -> if (weighted) quantity.amount else 1.0
        QuantityUnit.GRAM, QuantityUnit.MILLILITER -> if (weighted) quantity.amount / 1000.0 else 1.0
    }

    fun of(lines: List<ListLine>, householdPrices: Map<String, Double>, branch: Map<String, ItemPriceInfo>): ListCostEstimate {
        val open = lines.filter { it.status == LineStatus.NEEDED }
        var low = 0.0
        var high = 0.0
        var priced = 0
        for (line in open) {
            val grams = PieceWeights.gramsPerPiece(line.name)
            val own = householdPrices[line.itemId]
            val info = branch[line.itemId]
            when {
                own != null -> (own * PriceEstimate.units(line.quantity)).let { low += it; high += it }
                info?.preferred != null -> {
                    val p = info.preferred
                    val n = packages(line.quantity, p.soldByWeight, grams) ?: continue
                    // a weight on the list is priced per kilo; unknown unit of the price → not priced
                    val each = if (p.soldByWeight && line.quantity != null) p.pricePerKilo ?: continue else p.price
                    (each * n).let { low += it; high += it }
                }
                info != null -> {
                    val byWeight = LineCosts.weighted(line, info)
                    val firstByWeight = info.candidates.firstOrNull()?.soldByWeight == true
                    when {
                        byWeight != null -> {
                            low += byWeight.low
                            high += byWeight.high
                        }
                        // sold by weight, but the unit of the price is not known: don't multiply it by a weight,
                        // nor take the published price for a kilo
                        firstByWeight && line.quantity != null -> continue
                        ItemPrices.byWeight(info.candidates) && !info.perKilo -> continue
                        else -> {
                            val n = packages(line.quantity, firstByWeight, grams) ?: continue
                            low += info.low * n
                            high += info.high * n
                        }
                    }
                }
                else -> continue
            }
            priced++
        }
        return ListCostEstimate(low, high, priced, open.size)
    }
}
