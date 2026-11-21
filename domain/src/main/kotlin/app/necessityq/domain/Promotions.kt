package app.necessityq.domain

// ---------- promotions and coupons of the household's branches (PromoFull files, refreshed every 30 minutes) ----------

/**
 * One promotion at a branch, as the chain published it. The server keeps every promotion (also ended ones, those that
 * depend on stock and those on products out of stock); [PromoOffers] decides what is offered.
 */
data class Promotion(
    val id: String,
    val description: String,
    /** barcodes it applies to */
    val codes: Set<String>,
    /** buy at least this many for the reward ("2 ב-10" → 2) */
    val minQty: Double = 1.0,
    /** price for [minQty] units as published; null when the reward is a rate */
    val discountedPrice: Double? = null,
    /** discount as a fraction (0.2 = 20%) */
    val discountRate: Double? = null,
    /** local Israel time "yyyy-mm-ddThh:mm:ss"; blank when not published */
    val start: String = "",
    val end: String = "",
    /** for a customer club or a credit card only */
    val clubOnly: Boolean = false,
    /** needs a coupon */
    val coupon: Boolean = false,
    /** "עד גמר המלאי": depends on stock, so never offered (it may be gone by the time one gets there) */
    val stockLimited: Boolean = false,
    /** minimum basket amount for the promotion, null when none */
    val minBasket: Double? = null,
    /** a discount on the whole basket, not on items */
    val wholeBasket: Boolean = false,
) {
    fun runningAt(nowLocal: String): Boolean = (start.isBlank() || start <= nowLocal) && (end.isBlank() || end >= nowLocal)

    /** Members only: a club, a card or a coupon. */
    val forMembers: Boolean get() = clubOnly || coupon

    /**
     * The price of one unit with the promotion, for a product whose regular price is [regular]; null when it is no
     * discount. "2 ב-10" is 5 a unit. Some chains publish the price of one unit even for "2 ב-…": when the total
     * reading is implausibly low (under 30% of the regular price) the published price is taken per unit.
     */
    fun unitPrice(regular: Double): Double? {
        if (regular <= 0) return null
        val unit = when {
            discountedPrice != null -> {
                val q = maxOf(minQty, 1.0)
                val per = discountedPrice / q
                if (q > 1 && per < regular * 0.3 && discountedPrice < regular) discountedPrice else per
            }
            discountRate != null -> regular * (1 - discountRate)
            else -> null
        }
        return unit?.takeIf { it > 0 && it < regular - 0.005 }
    }

    /** "2 ב-10.00 ₪", "20% הנחה" — used when the description is blank. */
    val shortLabel: String
        get() = description.ifBlank {
            when {
                discountRate != null -> "${kotlin.math.round(discountRate * 100).toInt()}% הנחה"
                discountedPrice != null && minQty > 1 -> "${minQty.toInt()} ב-${PriceData.formatPrice(discountedPrice)}"
                discountedPrice != null -> "ב-${PriceData.formatPrice(discountedPrice)}"
                else -> "מבצע"
            }
        }

    /**
     * A whole-basket promotion's discount on a basket of [total]: a rate of the total, or the published amount off.
     * Null when the basket is under the minimum, or when the amount makes no sense (half the basket or more).
     */
    fun basketDiscount(total: Double): Double? {
        if (!wholeBasket || total <= 0) return null
        if (minBasket != null && total + 0.005 < minBasket) return null
        val off = when {
            discountRate != null -> total * discountRate
            discountedPrice != null -> discountedPrice
            else -> null
        }
        return off?.takeIf { it > 0.005 && it < total * 0.5 }
    }

    /** "עד גמר המלאי — ייתכן שייגמר" for a promotion that depends on stock; null otherwise. */
    val stockWarning: String? get() = if (stockLimited) "עד גמר המלאי — ייתכן שייגמר" else null

    /** "במועדון", "בקופון", "בקנייה מעל 300 ₪" — what it takes, empty for every customer. */
    val conditions: List<String>
        get() = buildList {
            if (clubOnly) add("במועדון / בכרטיס")
            if (coupon) add("בקופון")
            minBasket?.let { add("בקנייה מעל ${PriceData.formatPrice(it, 0)}") }
        }
}

/** A branch's running promotions. */
data class BranchPromotions(val branchId: String, val updatedAtMillis: Long, val promotions: List<Promotion>) {
    val byCode: Map<String, List<Promotion>> by lazy {
        val m = HashMap<String, MutableList<Promotion>>()
        promotions.forEach { p -> p.codes.forEach { c -> m.getOrPut(c) { mutableListOf() } += p } }
        m
    }
}

/** A promotion on one of the list's items at one branch. */
data class PromoOffer(
    val itemId: String,
    val itemName: String,
    val branchId: String,
    val promotion: Promotion,
    val product: BranchProduct,
    /** the product's regular price and its price with the promotion (a unit) */
    val regular: Double,
    val promoUnit: Double,
) {
    val savingPerUnit: Double get() = regular - promoUnit
    val percent: Double get() = if (regular > 0) savingPerUnit / regular else 0.0
}

object PromoOffers {
    /**
     * An item promotion the app may offer: running now (not ended, not yet to start), on items (whole-basket ones are
     * [basket]), and (unless [includeMembers]) open to every customer, without a club, a card or a coupon. One that
     * depends on stock is offered only with [allowStockLimited] (shown with its warning, never counted in a plan).
     * Out of stock is checked by the product: offers look only at products in the branch's current price file.
     */
    fun usable(p: Promotion, nowLocal: String, includeMembers: Boolean, allowStockLimited: Boolean = false): Boolean =
        p.runningAt(nowLocal) && (allowStockLimited || !p.stockLimited) && !p.wholeBasket && (includeMembers || !p.forMembers)

    /** A branch's running whole-basket promotions ("10 ₪ הנחה בקנייה מעל 300 ₪"), members' ones with [includeMembers]. */
    fun basket(promos: BranchPromotions?, nowLocal: String, includeMembers: Boolean): List<Promotion> =
        promos?.promotions.orEmpty().filter { p ->
            p.wholeBasket && p.runningAt(nowLocal) && (includeMembers || !p.forMembers) &&
                (p.discountRate != null || p.discountedPrice != null)
        }

    /** Whole-basket promotions of every branch, as (branch id, promotion). */
    fun basketOffers(promos: Map<String, BranchPromotions>, nowLocal: String, includeMembers: Boolean): List<Pair<String, Promotion>> =
        promos.flatMap { (id, bp) -> basket(bp, nowLocal, includeMembers).map { id to it } }

    /**
     * The best promotion on an item at a branch: among the item's products in stock there (the household's chosen
     * one only, when it has one), the lowest price a unit. Products by weight take only a rate (a price "for 2" of a
     * weighed product means nothing).
     */
    fun best(
        info: ItemPriceInfo,
        promos: BranchPromotions?,
        nowLocal: String,
        includeMembers: Boolean,
        allowStockLimited: Boolean = false,
    ): Triple<BranchProduct, Promotion, Double>? {
        if (promos == null) return null
        val products = info.preferred?.let { listOf(it) } ?: info.candidates
        var best: Triple<BranchProduct, Promotion, Double>? = null
        for (product in products) {
            if (product.internalCode && product.code.isBlank()) continue
            for (p in promos.byCode[product.code].orEmpty()) {
                if (!usable(p, nowLocal, includeMembers, allowStockLimited)) continue
                if (product.soldByWeight && p.discountRate == null) continue
                val unit = p.unitPrice(product.price) ?: continue
                if (best == null || unit < best.third) best = Triple(product, p, unit)
            }
        }
        return best
    }

    /**
     * "יש למוצרים אלו מבצעים ב…": per open line and branch, its best usable promotion ([infos]: branch id → the price
     * picture of each item there). Biggest saving first.
     */
    fun forList(
        lines: List<ListLine>,
        infos: Map<String, Map<String, ItemPriceInfo>>,
        promos: Map<String, BranchPromotions>,
        nowLocal: String,
        includeMembers: Boolean,
    ): List<PromoOffer> {
        val open = lines.filter { it.status == LineStatus.NEEDED }.distinctBy { it.itemId }
        val out = mutableListOf<PromoOffer>()
        for ((branchId, byItem) in infos) {
            val branchPromos = promos[branchId] ?: continue
            for (line in open) {
                val info = byItem[line.itemId] ?: continue
                // also those that depend on stock: shown with their warning
                val (product, promo, unit) = best(info, branchPromos, nowLocal, includeMembers, allowStockLimited = true) ?: continue
                out += PromoOffer(line.itemId, line.displayName, branchId, promo, product, product.price, unit)
            }
        }
        return out.sortedWith(compareByDescending<PromoOffer> { it.percent }.thenBy { it.itemName })
    }
}
