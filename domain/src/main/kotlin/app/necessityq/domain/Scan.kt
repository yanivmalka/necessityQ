package app.necessityq.domain

// ---------- barcode scanning and "which branch am I in" ----------

object Barcodes {
    /**
     * Digits of a scanned code, or null when it is not a product barcode. Leading zeros are dropped
     * while at least 8 digits remain — the same rule the ingestion applies to the price files, so a
     * UPC-A read as "0729…" matches the stored "729…".
     */
    fun normalize(raw: String): String? {
        val digits = raw.trim().filter { it.isDigit() }
        if (digits.length < 4 || digits.length > 14 || digits.length != raw.trim().length) return null
        return digits.replace(Regex("^0+(?=\\d{8,})"), "")
    }

    /** Same product code, ignoring leading zeros. */
    fun same(a: String, b: String): Boolean = a.trimStart('0') == b.trimStart('0')
}

/** A scanned product found at one of the household's branches. */
data class ScanHit(val branchId: String, val product: BranchProduct)

/** Everything the scan sheet shows for one barcode. */
data class ScanResult(
    val code: String,
    /** branch the lookup was made for (the one the user is in, else the primary one) */
    val branchId: String?,
    /** the product at [branchId] */
    val here: BranchProduct?,
    /** the product at the household's other loaded branches */
    val elsewhere: List<ScanHit>,
    /** open (or already bought) list line this product belongs to */
    val line: ListLine?,
    /** generic item for adding it to the list: the household's own wording when known */
    val identity: ItemIdentity?,
    /** the item already has this exact product as its chosen product */
    val alreadyPreferred: Boolean,
    /** is the price here reasonable, unusually high or low (null when there is nothing to compare with) */
    val verdict: PriceVerdict? = null,
) {
    val product: BranchProduct? get() = here ?: elsewhere.firstOrNull()?.product
    val found: Boolean get() = product != null

    /**
     * Branch id → the price there in the price unit of [product] (per 100 g, per kg or per package; see
     * [PriceComparisons.inUnitOf]). Branches where it can't be put in that unit are left out.
     */
    val prices: Map<String, Double> get() {
        val reference = product ?: return emptyMap()
        return all().mapNotNull { (id, p) -> PriceData.inUnitOf(reference, p)?.let { id to it } }.toMap()
    }

    /** Range over the branches, in the price unit of [product]; branches whose unit can't be matched are left out. */
    val range: CrossBranchRange? get() = product?.let { PriceData.rangeIn(it, all()) }

    private fun all(): List<Pair<String, BranchProduct>> =
        listOfNotNull(here?.let { branchId!! to it }) + elsewhere.map { it.branchId to it.product }
}

object Scan {
    private val size = Regex(
        """\d+(?:[.,]\d+)?\s*(?:ק"ג|קג|קילו|גרם|גר'|גר|ג'|ג|מ"ל|מל|ליטר|ל'|ל|יח'|יחידות|יח|מ"ר|x|X)(?=\s|$)""",
    )
    private val count = Regex("""(?<=\s|^)\d+\s*(?:x|X|\*)\s*\d+[^\s]*""")
    private val bareNumber = Regex("""(?<=\s|^)\d+(?:[.,]\d+)?(?=\s|$)""")

    /**
     * Generic item for a product name as printed by the chain: brand, package size and stray numbers
     * are dropped, the percentage kept ("קוטג' 5% תנובה 250 גרם" → קוטג' 5%).
     */
    fun genericIdentity(product: BranchProduct): ItemIdentity {
        var text = HebrewText.clean(product.name)
        product.brand.trim().takeIf { it.length > 1 }?.let { brand ->
            text = text.replace(brand, " ")
            brand.split(' ').filter { it.length > 2 }.forEach { text = text.replace(Regex("(?<=\\s|^)${Regex.escape(it)}(?=\\s|$)"), " ") }
        }
        text = count.replace(text, " ")
        text = size.replace(text, " ")
        text = bareNumber.replace(text, " ")
        text = HebrewText.clean(text.replace(Regex("[*,]"), " "))
        return ItemIdentity.fromText(text.ifBlank { product.name })
    }

    /** Does [product] belong to the generic item (name, variant) — the same matching the price ranges use. */
    fun belongsTo(product: BranchProduct, name: String, variant: String?): Boolean =
        ItemPrices.candidates(ProductIndex("", 0, listOf(product)), name, variant).isNotEmpty()

    /** How specific a name is: a longer match ("קוטג' 5%") wins over a shorter one ("קוטג'"). */
    private fun specificity(name: String, variant: String?) = HebrewText.matchKey(name).length + HebrewText.matchKey(variant.orEmpty()).length

    /**
     * Looks the code up at [branchId] and at the other loaded branches, and finds the household item it
     * belongs to: an item that chose exactly this product, else a list line / known item whose generic
     * name matches the product.
     */
    fun lookup(
        rawCode: String,
        branchId: String?,
        prices: Map<String, BranchPrices>,
        lines: List<ListLine>,
        known: List<ItemIdentity>,
        preferred: Map<String, String>,
    ): ScanResult? {
        val code = Barcodes.normalize(rawCode) ?: return null
        fun at(bp: BranchPrices) = bp.products.firstOrNull { Barcodes.same(it.code, code) }
        val here = branchId?.let { prices[it] }?.let(::at)
        // cheapest first, per kilo for those sold by weight (4.90 per 100 g is dearer than 39 per kg); unknown units last
        val elsewhere = prices.filterKeys { it != branchId }.mapNotNull { (id, bp) -> at(bp)?.let { ScanHit(id, it) } }
            .sortedWith(compareBy({ it.product.soldByWeight && it.product.pricePerKilo == null }, { it.product.pricePerKilo ?: it.product.price }))
        val product = here ?: elsewhere.firstOrNull()?.product

        val live = lines.filter { it.status == LineStatus.NEEDED || it.status == LineStatus.BOUGHT }
        val byPreferred = preferred.filterValues { Barcodes.same(it, code) }.keys
        val line = live.filter { it.itemId in byPreferred }.minByOrNull { if (it.status == LineStatus.NEEDED) 0 else 1 }
            ?: product?.let { p ->
                live.filter { belongsTo(p, it.name, it.variant) }
                    .sortedWith(compareBy<ListLine> { if (it.status == LineStatus.NEEDED) 0 else 1 }.thenByDescending { specificity(it.name, it.variant) })
                    .firstOrNull()
            }
        val identity = line?.let { ItemIdentity(it.name, it.variant, it.category) }
            ?: known.firstOrNull { it.id in byPreferred }
            ?: product?.let { p -> known.filter { belongsTo(p, it.name, it.variant) }.maxByOrNull { specificity(it.name, it.variant) } }
            ?: product?.let(::genericIdentity)
        val similar = if (here != null && branchId != null && identity != null) {
            prices[branchId]?.let { bp -> ItemPrices.candidates(ProductIndex(branchId, bp.updatedAtMillis, bp.products), identity.name, identity.variant) }
        } else null
        // the other branches' prices in this product's unit; those that can't be matched are not compared
        val verdict = here?.let { PriceVerdicts.of(it, elsewhere.mapNotNull { h -> PriceComparisons.inUnitOf(it, h.product) }, similar.orEmpty()) }
        return ScanResult(
            verdict = verdict,
            code = code,
            branchId = branchId,
            here = here,
            elsewhere = elsewhere,
            line = line,
            identity = identity,
            alreadyPreferred = identity != null && preferred[identity.id]?.let { Barcodes.same(it, code) } == true,
        )
    }
}

object BranchLocator {
    /**
     * The branch the user is standing in: the nearest exactly-geocoded branch within [maxMeters]
     * (widened to the fix accuracy, up to 400 m). Branches known only by their city centre never match.
     */
    fun branchAt(
        branches: List<PriceBranch>,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double? = null,
        maxMeters: Double = 200.0,
    ): NearbyBranch? {
        val limit = maxOf(maxMeters, minOf(accuracyMeters ?: 0.0, 400.0))
        return branches.asSequence()
            .filter { !it.approximate && it.latitude != null && it.longitude != null }
            .map { NearbyBranch(it, Stores.distanceMeters(latitude, longitude, it.latitude!!, it.longitude!!)) }
            .filter { it.distanceMeters!! <= limit }
            .minByOrNull { it.distanceMeters!! }
    }

    /** A household store name for a branch ("רמי לוי תלפיות"), unless the branch name already carries the chain. */
    fun storeName(branch: PriceBranch): String {
        val name = branch.name.trim()
        return if (name.isBlank()) branch.chainName
        else if (HebrewText.matchKey(name).contains(HebrewText.matchKey(branch.chainName))) name
        else "${branch.chainName} $name"
    }
}

enum class PriceLevel(val label: String) {
    CHEAP("זול מהרגיל"),
    FAIR("מחיר סביר"),
    EXPENSIVE("יקר מהרגיל"),
}

data class PriceVerdict(val level: PriceLevel, val reasons: List<String>)

object PriceVerdicts {
    private const val NOTABLE = 0.08

    private fun pct(x: Double) = "${kotlin.math.round(kotlin.math.abs(x) * 100).toInt()}%"

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /**
     * Judges the price of [here] against: the same barcode at the household's other branches (strongest
     * evidence), the product's own recent range at this branch, and similar products of the same generic
     * item at this branch by unit price (only when sizes can be compared). Null with nothing to compare.
     */
    fun of(here: BranchProduct, otherBranches: List<Double>, similar: List<BranchProduct>): PriceVerdict? {
        val reasons = mutableListOf<String>()
        var score = 0
        if (otherBranches.isNotEmpty()) {
            val m = median(otherBranches)
            val diff = (here.price - m) / m
            when {
                diff >= NOTABLE -> { score += 2; reasons += "יקר ב-${pct(diff)} מהסניפים שאתם קונים בהם (${PriceData.formatPrice(m)})" }
                diff <= -NOTABLE -> { score -= 2; reasons += "זול ב-${pct(diff)} מהסניפים שאתם קונים בהם (${PriceData.formatPrice(m)})" }
                else -> reasons += "דומה לסניפים שאתם קונים בהם (${PriceData.formatRange(otherBranches.min(), otherBranches.max())})"
            }
        }
        if (here.high - here.low >= 0.01) {
            when {
                here.price >= here.high - 0.005 -> { score += 1; reasons += "המחיר הגבוה ביותר בסניף בתקופה האחרונה (היה גם ${PriceData.formatPrice(here.low)})" }
                here.price <= here.low + 0.005 -> { score -= 1; reasons += "המחיר הנמוך ביותר בסניף בתקופה האחרונה (היה עד ${PriceData.formatPrice(here.high)})" }
            }
        }
        val unit = here.unitPrice
        val peers = similar.filter { it.code != here.code && it.unitPrice != null && it.weighted == here.weighted }.mapNotNull { it.unitPrice }
        if (unit != null && peers.size >= 3) {
            val (lo, hi) = ItemPrices.typicalRange(peers)!!
            when {
                unit > hi * (1 + NOTABLE) -> { score += 1; reasons += "יקר ממוצרים דומים בסניף לפי מחיר ליחידת מידה" }
                unit < lo * (1 - NOTABLE) -> { score -= 1; reasons += "זול ממוצרים דומים בסניף לפי מחיר ליחידת מידה" }
                else -> reasons += "בטווח של מוצרים דומים בסניף לפי מחיר ליחידת מידה"
            }
        }
        if (reasons.isEmpty()) return null
        val level = when {
            score >= 1 -> PriceLevel.EXPENSIVE
            score <= -1 -> PriceLevel.CHEAP
            else -> PriceLevel.FAIR
        }
        return PriceVerdict(level, reasons)
    }
}
