package app.necessityq.domain

// ---------- is it dear or cheap here, compared with the household's other branches ----------

/** How the comparison was made, strongest first. */
enum class CompareBasis {
    /** the household's chosen product, the same barcode at the other branches */
    SAME_PRODUCT,
    /** the item's products sold at both branches (same barcodes) */
    SHARED_PRODUCTS,
    /** the typical price of the item's products at each branch (different products, e.g. produce) */
    TYPICAL,
}

/** One followed branch's price for an item or a product. */
data class BranchPriceRange(val branchId: String, val low: Double, val high: Double)

/**
 * A price at one branch compared with the household's other followed branches ("the stores we usually buy in").
 * [diff] is relative: +0.12 = 12% dearer here than at the other branches (their median), -0.12 = 12% cheaper.
 */
data class PriceComparison(
    val branchId: String,
    val diff: Double,
    val basis: CompareBasis,
    /** the price here (exact product) or its typical range */
    val here: BranchPriceRange,
    /** the other branches that could be compared, cheapest first */
    val others: List<BranchPriceRange>,
) {
    /** CHEAP / EXPENSIVE once the gap is significant ([PriceComparisons.SIGNIFICANT]), FAIR otherwise. */
    val level: PriceLevel
        get() = when {
            diff >= PriceComparisons.SIGNIFICANT -> PriceLevel.EXPENSIVE
            diff <= -PriceComparisons.SIGNIFICANT -> PriceLevel.CHEAP
            else -> PriceLevel.FAIR
        }
    val notable: Boolean get() = level != PriceLevel.FAIR

    /** The cheapest of all compared branches (this one included). */
    val cheapestBranchId: String
        get() = (others + here).minByOrNull { (it.low + it.high) / 2 }!!.branchId

    /** "יקר ב-12% מהסניפים שלך" / "זול ב-12% מהסניפים שלך" / "כמו בסניפים שלך". */
    val label: String
        get() = when (level) {
            PriceLevel.EXPENSIVE -> "יקר ב-${PriceComparisons.percent(diff)} מהסניפים שלך"
            PriceLevel.CHEAP -> "זול ב-${PriceComparisons.percent(diff)} מהסניפים שלך"
            PriceLevel.FAIR -> "כמו בסניפים שלך"
        }

    /** Short tile marker: "▲ 12%" / "▼ 12%". */
    val shortLabel: String
        get() = (if (diff > 0) "▲ " else "▼ ") + PriceComparisons.percent(diff)
}

object PriceComparisons {
    /** A gap of 10% or more is "significantly" dearer or cheaper. */
    const val SIGNIFICANT = 0.10

    /** Fewer matching products than this at a branch and their typical price is not compared. */
    private const val MIN_TYPICAL = 3

    fun percent(diff: Double): String = "${kotlin.math.round(kotlin.math.abs(diff) * 100).toInt()}%"

    internal fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /** Products of a branch by barcode; chain-internal codes are left out (another chain's code means another product). */
    fun byCode(products: List<BranchProduct>): Map<String, BranchProduct> =
        products.asSequence().filter { !it.internalCode && it.code.isNotBlank() }.associateBy { it.code }

    /**
     * [other]'s price in the price unit of [here] (the same product at another branch). Packaged goods: as is.
     * Sold by weight: through the price per kilo, since one chain may publish it per 100 g and another per kg
     * (39 per kg against 4.90 per 100 g → 3.90). Null when they can't be compared: a price unit is not
     * recognised, or one is sold by weight and the other by the package.
     */
    fun inUnitOf(here: BranchProduct, other: BranchProduct): Double? = when {
        here.soldByWeight != other.soldByWeight -> null
        !here.soldByWeight -> other.price
        else -> {
            val kilos = here.priceUnitKilos
            val perKilo = other.pricePerKilo
            if (kilos == null || perKilo == null) null else perKilo * kilos
        }
    }

    /**
     * One product (barcode) at [branchId] against the same barcode at the other branches ([others]: branch id →
     * products by barcode, see [byCode]), their prices given in its own price unit ([inUnitOf]). Null when no
     * other branch sells it in a comparable unit.
     */
    fun product(product: BranchProduct, branchId: String, others: Map<String, Map<String, BranchProduct>>): PriceComparison? {
        if (product.internalCode || product.price <= 0) return null
        val quotes = others.filterKeys { it != branchId }.mapNotNull { (id, codes) ->
            codes[product.code]?.let { inUnitOf(product, it) }?.takeIf { it > 0 }?.let { BranchPriceRange(id, it, it) }
        }
        if (quotes.isEmpty()) return null
        val diff = product.price / median(quotes.map { it.low }) - 1
        return PriceComparison(branchId, diff, CompareBasis.SAME_PRODUCT, BranchPriceRange(branchId, product.price, product.price), quotes.sortedBy { it.low })
    }

    /**
     * A generic item ("קוטג' 5%") at the branch of [here] against the same item at the other branches ([others]: branch
     * id → its price picture there; [otherCodes]: branch id → products by barcode, for the household's chosen product).
     *
     * Per branch, the strongest evidence wins: the chosen product's barcode; else the products sold at both branches
     * (median of the price ratios); else, with enough products on both sides (or produce by the kilo), the typical
     * prices. The result is the median over the branches. Null when nothing could be compared.
     */
    fun item(
        here: ItemPriceInfo,
        others: Map<String, ItemPriceInfo?>,
        otherCodes: Map<String, Map<String, BranchProduct>> = emptyMap(),
    ): PriceComparison? {
        val preferred = here.preferred
        if (preferred != null && !preferred.internalCode) {
            val codes = others.keys.associateWith { id -> otherCodes[id] ?: others[id]?.let { byCode(it.candidates) }.orEmpty() }
            product(preferred, here.branchId, codes)?.let { return it }
        }
        val hereCodes = byCode(here.candidates)
        val hereTypical = typical(here)
        var basis = CompareBasis.TYPICAL
        val ratios = mutableListOf<Double>()
        val quotes = mutableListOf<BranchPriceRange>()
        for ((id, info) in others) {
            if (id == here.branchId || info == null || info.candidates.isEmpty()) continue
            // ranges per kilo and per package can't stand side by side
            if (info.perKilo != here.perKilo) continue
            // the same barcode at both branches, in the same price unit (per kilo when sold by weight)
            val shared = info.candidates.mapNotNull { o ->
                if (o.internalCode) return@mapNotNull null
                val h = hereCodes[o.code] ?: return@mapNotNull null
                inUnitOf(h, o)?.takeIf { it > 0 }?.let { h.price / it }
            }
            val ratio = when {
                shared.isNotEmpty() -> { basis = CompareBasis.SHARED_PRODUCTS; median(shared) }
                hereTypical != null -> typical(info)?.let { hereTypical / it }
                else -> null
            } ?: continue
            ratios += ratio
            quotes += BranchPriceRange(id, info.low, info.high)
        }
        if (ratios.isEmpty()) return null
        return PriceComparison(here.branchId, median(ratios) - 1, basis, BranchPriceRange(here.branchId, here.low, here.high), quotes.sortedBy { (it.low + it.high) / 2 })
    }

    /**
     * Median price of the item's products, when there are enough of them to stand for the branch. Sold by weight:
     * the median per kilo (one branch may price olives per 100 g, another per kg), unknown price units left out.
     */
    private fun typical(info: ItemPriceInfo): Double? {
        val byKilo = ItemPrices.byWeight(info.candidates)
        val prices = (if (byKilo) info.candidates.mapNotNull { it.pricePerKilo } else info.candidates.map { it.price }).filter { it > 0 }
        if (prices.isEmpty() || (prices.size < MIN_TYPICAL && !byKilo)) return null
        return median(prices)
    }
}

/** A product found at one or more of the chosen branches, with its price at each of them. */
data class ProductAcross(
    /** as listed at the first chosen branch that sells it */
    val product: BranchProduct,
    /** branch id → the product there */
    val at: Map<String, BranchProduct>,
) {
    /**
     * Branch id → the price there in the price unit of [product] (per 100 g, per kg, per package; see
     * [PriceComparisons.inUnitOf]), so a chain that publishes it per kg and one that publishes it per 100 g compare.
     * A branch where it can't be put in that unit (price unit not recognised, or sold by the package there) is
     * left out: it sells the product but is not compared.
     */
    val prices: Map<String, Double> by lazy {
        at.mapNotNull { (id, p) -> (if (p == product) p.price else PriceComparisons.inUnitOf(product, p))?.let { id to it } }.toMap()
    }
    val cheapest: Double get() = prices.values.minOrNull() ?: product.price
    val dearest: Double get() = prices.values.maxOrNull() ?: product.price
    /** Branches selling it at the lowest price (several when tied). */
    val cheapestBranchIds: Set<String> get() = prices.filterValues { it <= cheapest + 0.005 }.keys
    /** Price spread between the chosen branches: 0.25 = the dearest is 25% above the cheapest. */
    val spread: Double get() = if (cheapest > 0) dearest / cheapest - 1 else 0.0
}

object ProductSearch {
    /**
     * Searches the products of several branches at once ([branches]: branch id → products, in display order) and
     * merges hits by barcode, so each product shows its price at every chosen branch that sells it ([codes]: branch
     * id → products by barcode, see [PriceComparisons.byCode]). Chain-internal codes stay per branch. Exact barcode
     * first, then products that can be compared at more of the branches ([ProductAcross.prices]), then shorter names.
     */
    fun across(
        branches: List<Pair<String, List<BranchProduct>>>,
        codes: Map<String, Map<String, BranchProduct>>,
        query: String,
        limit: Int = 100,
    ): List<ProductAcross> {
        val code = query.trim()
        val found = LinkedHashMap<String, ProductAcross>()
        for ((branchId, products) in branches) {
            for (p in PriceData.search(products, query, limit)) {
                if (p.internalCode || p.code.isBlank()) {
                    found.getOrPut("$branchId:${p.code}") { ProductAcross(p, mapOf(branchId to p)) }
                } else if (p.code !in found) {
                    val at = branches.mapNotNull { (id, _) -> (codes[id]?.get(p.code) ?: p.takeIf { id == branchId })?.let { id to it } }.toMap()
                    found[p.code] = ProductAcross(p, at)
                }
            }
        }
        return found.values
            .sortedWith(compareBy<ProductAcross>({ if (it.product.code == code) 0 else 1 }, { -it.prices.size }, { it.product.name.length }, { it.cheapest }))
            .take(limit)
    }
}
