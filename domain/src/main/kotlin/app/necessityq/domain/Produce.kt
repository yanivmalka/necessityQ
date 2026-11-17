package app.necessityq.domain

import kotlin.math.roundToLong

/**
 * Typical weight of one piece of produce sold by weight, so "15 מלפפונים" can be priced and shown as
 * ≈1.6 ק"ג. Rough averages for Israeli produce; only used for estimates, never stored as a quantity.
 */
object PieceWeights {
    private val grams: Map<String, Double> = listOf(
        listOf("עגבניות", "עגבניה", "עגבנייה") to 130.0,
        listOf("מלפפונים", "מלפפון") to 110.0,
        listOf("בצל", "בצלים", "בצל יבש", "בצל סגול") to 150.0,
        listOf("שום") to 50.0,
        listOf("תפוחי אדמה", "תפוח אדמה", "תפוא") to 200.0,
        listOf("גזר") to 90.0,
        listOf("פלפל", "פלפלים", "גמבה") to 180.0,
        listOf("חסה") to 400.0,
        listOf("לימון", "לימונים") to 110.0,
        listOf("בננות", "בננה") to 150.0,
        listOf("תפוחים", "תפוח", "תפוחי עץ") to 180.0,
        listOf("תפוזים", "תפוז") to 220.0,
        listOf("אבוקדו") to 220.0,
        listOf("קישואים", "קישוא") to 180.0,
        listOf("בטטה", "בטטות") to 300.0,
        listOf("חצילים", "חציל") to 350.0,
        listOf("אגסים", "אגס") to 180.0,
        listOf("קלמנטינות", "קלמנטינה") to 80.0,
        listOf("אשכוליות", "אשכולית") to 350.0,
        listOf("מנגו") to 350.0,
        listOf("קולרבי") to 250.0,
        listOf("סלק") to 200.0,
        listOf("כרובית") to 800.0,
        listOf("כרוב") to 1200.0,
        listOf("ברוקולי") to 400.0,
        listOf("מלון") to 1500.0,
        listOf("אבטיח") to 6000.0,
        listOf("שסק", "שסקים") to 40.0,
        listOf("אפרסקים", "אפרסק", "נקטרינות", "נקטרינה") to 150.0,
        listOf("קיווי") to 80.0,
        listOf("רימונים", "רימון") to 300.0,
    ).flatMap { (names, g) -> names.map { HebrewText.matchKey(it) to g } }.toMap()

    /** Grams per piece of the generic item, or null when unknown (not produce, or not listed). */
    fun gramsPerPiece(name: String): Double? {
        val canonical = Catalog.lookup(name)?.name ?: name
        return grams[HebrewText.matchKey(canonical)] ?: grams[HebrewText.matchKey(name)]
    }

    /** Estimated weight of a count of pieces ("15 יח'" of cucumbers → 1.65 ק"ג); null when not counted in pieces or unknown. */
    fun estimate(name: String, quantity: Quantity?): Quantity? {
        if (quantity?.unit != QuantityUnit.PIECE) return null
        val g = gramsPerPiece(name) ?: return null
        val base = quantity.amount * g
        // a readable estimate: 50 g steps below a kilo, 0.1 kg above
        val rounded = if (base < 1000) maxOf(50.0, (base / 50).roundToLong() * 50.0) else (base / 100).roundToLong() * 100.0
        return Quantity.fromBase(rounded, Dimension.WEIGHT)
    }

    private val kiloUnits = setOf("קג", "קילו", "קילוגרם", "kg", "1קג", "1 קג", "לקג")

    /** True for a price-file unit meaning a kilo ("ק\"ג", "קילוגרם", "1 ק\"ג"). */
    fun isKiloUnit(unit: String): Boolean {
        val k = HebrewText.matchKey(unit).replace(".", "").trim()
        return k in kiloUnits || k.endsWith("קג") || k.contains("קילו")
    }

    /** "≈1.7 ק\"ג" */
    fun label(estimate: Quantity): String = "≈${estimate.format()}"
}

/** Cost of a line of produce sold by weight, for the quantity on the list. */
data class LineCost(val low: Double, val high: Double, val estimatedWeight: Quantity?) {
    val isRange: Boolean get() = high - low >= 0.005
}

object LineCosts {
    /**
     * For a line whose matching products are sold by weight (price per kg) and that has a quantity:
     * the price times the weight — the estimated one when counted in pieces. Null otherwise
     * (packaged products keep their per-package display).
     */
    fun weighted(line: ListLine, info: ItemPriceInfo): LineCost? {
        val quantity = line.quantity ?: return null
        val (low, high) = when {
            info.preferred != null -> if (info.preferred.soldByWeight) info.preferred.price to info.preferred.price else return null
            else -> ItemPrices.typicalRange(info.candidates.filter { it.soldByWeight }.map { it.price }) ?: return null
        }
        val kilos = ListCost.packages(quantity, true, PieceWeights.gramsPerPiece(line.name)) ?: return null
        return LineCost(low * kilos, high * kilos, PieceWeights.estimate(line.name, quantity))
    }
}
