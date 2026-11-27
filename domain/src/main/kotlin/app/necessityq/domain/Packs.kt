package app.necessityq.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What one package of a product holds: [pieces] units, each of [each] when the units have a size. A tray of 30
 * eggs is `Pack(30)`, a carton of milk `Pack(each = 2 ליטר)`, eight yoghurts `Pack(8, 150 גרם)`, a six-pack of
 * water `Pack(6, 1.5 ליטר)`.
 *
 * It is the one answer to "how much is one" for an item: the amounts proposed, the amount shown on the list, the
 * +/- of the item page, the price and the order of the branch's products all go by it ([HouseholdPacks]), so a
 * household that buys trays of 30 is offered trays of 30 everywhere.
 */
data class Pack(val pieces: Int = 1, val each: Quantity? = null) {
    init {
        require(pieces >= 1) { "a pack holds at least one piece" }
    }

    /** All it holds by weight or volume (8 × 150 גרם = 1.2 ק"ג); null when it is counted only. */
    val total: Quantity? get() = each?.let { Quantity.fromBase(it.baseAmount * pieces, it.dimension) }

    /** "מארז 30", "2 ליטר", "מארז 8 × 150 גרם", "6 × 1.5 ליטר". */
    fun label(): String = when {
        pieces > 1 && each != null -> "$pieces × ${each.format()}"
        pieces > 1 -> "מארז $pieces"
        each != null -> each.format()
        else -> "יחידה"
    }

    /**
     * How many packs [q] is: packages as they are; pieces in packs of [pieces] (30 eggs: one tray); a weight or a
     * volume in packs of [total] (4 ליטר: two 2 L cartons). Null when [q] can't be read in packs of this one.
     */
    fun packsIn(q: Quantity): Double? = when {
        q.unit == QuantityUnit.PACKAGE -> q.amount
        q.unit == QuantityUnit.PIECE -> if (pieces > 1) q.amount / pieces else q.amount
        total != null && total!!.dimension == q.dimension -> q.baseAmount / total!!.baseAmount
        else -> null
    }

    /**
     * [packs] of it, written as the list writes amounts of it: in packages when [like] is in packages; else pieces
     * for packs of pieces ("60 יח'" for two trays); else the content ("4 ליטר" for two cartons); else packages.
     */
    fun quantityOf(packs: Int, like: Quantity? = null): Quantity {
        val n = max(1, packs)
        if (like?.unit == QuantityUnit.PACKAGE) return Quantity(n.toDouble(), QuantityUnit.PACKAGE)
        if (pieces > 1 && (like == null || like.dimension == Dimension.COUNT)) return Quantity.pieces(n * pieces)
        val t = total
        if (t != null && (like == null || like.dimension == t.dimension)) return Quantity.fromBase(t.baseAmount * n, t.dimension)
        if (pieces > 1) return Quantity.pieces(n * pieces)
        return Quantity(n.toDouble(), if (like?.unit == QuantityUnit.PIECE) QuantityUnit.PIECE else QuantityUnit.PACKAGE)
    }

    /** [q] in whole packs, the nearest (at least one): 19 yoghurts in packs of 8 are 16, 1.5 L in 2 L cartons is 2 L. */
    fun nearest(q: Quantity): Quantity? {
        val n = packsIn(q) ?: return null
        return quantityOf(max(1, Math.round(n).toInt()), q)
    }

    /** [q] covered by whole packs (a recipe's exact need): 19 yoghurts in packs of 8 are 24. */
    fun covering(q: Quantity): Quantity? {
        val n = packsIn(q) ?: return null
        return quantityOf(max(1, ceil(n - 1e-9).toInt()), q)
    }

    /** One pack more or less than [q] ([delta]), never below one; null when [q] can't be read in packs. */
    fun step(q: Quantity?, delta: Int): Quantity? {
        if (q == null) return if (delta > 0) quantityOf(1) else null
        val n = packsIn(q) ?: return null
        val whole = if (abs(n - Math.round(n)) < 1e-6) Math.round(n).toInt() else if (delta > 0) n.toInt() else ceil(n).toInt()
        return quantityOf(max(1, whole + delta), q)
    }

    /**
     * [q] as shown with its packs: "30 יח' · מארז 30", "16 יח' · 2 מארזים של 8", "4 ליטר · 2 × 2 ליטר". Just [q] when
     * it is a single pack whose content says it all ("2 ליטר"), or when it is not a whole number of packs.
     */
    fun describe(q: Quantity): String {
        packsOnly(q)?.let { return it }
        val n = packsIn(q) ?: return q.format()
        val whole = Math.round(n)
        if (abs(n - whole) > 1e-6 || whole < 1) return q.format()
        val packs = whole.toInt()
        if (q.unit == QuantityUnit.PACKAGE) return if (each != null) "${q.format()} · ${packsText(packs)}" else q.format()
        if (pieces == 1 && each != null && packs == 1) return q.format()
        if (pieces == 1 && each == null) return q.format()
        return "${q.format()} · ${packsText(packs)}"
    }

    /**
     * [q] in packs alone when this is a pack of pieces and [q] counts whole packs of it: a tray of 30 eggs is bought as
     * "מארז 30", not as "30 יח'". Null otherwise.
     */
    fun packsOnly(q: Quantity): String? {
        if (pieces < 2 || q.dimension != Dimension.COUNT) return null
        val n = packsIn(q) ?: return null
        val whole = Math.round(n)
        if (abs(n - whole) > 1e-6 || whole < 1) return null
        return packsText(whole.toInt())
    }

    /** "מארז 30", "2 מארזים של 30", "2 × 2 ליטר", "2 מארזים של 8 × 150 גרם". */
    fun packsText(packs: Int): String = when {
        pieces == 1 && each != null -> if (packs == 1) each.format() else "$packs × ${each.format()}"
        packs == 1 -> if (each != null) "מארז ${label()}" else label()
        else -> "$packs מארזים של " + if (each != null) label() else "$pieces"
    }

    /** Same size of pack (a tray of 30 is a tray of 30 whatever the brand). */
    fun sameSize(other: Pack): Boolean {
        if (pieces != other.pieces) return false
        val a = each
        val b = other.each
        if (a == null || b == null) return a == null && b == null || pieces > 1
        return a.dimension == b.dimension && abs(a.baseAmount - b.baseAmount) < 1e-6
    }
}

/**
 * What a household chose: per item id the pack of the product it picked at the branch ([ProductPacks.of]); per pack
 * key ([PackSizes.keyFor]) the pack size it picked (a base amount, [MealData.packs]).
 */
data class PackChoices(val products: Map<String, Pack> = emptyMap(), val sizes: Map<String, Long> = emptyMap())

/**
 * The amount of a line or a suggestion as shown: in packs when the household's pack is known ("מארז 30",
 * "4 ליטר · 2 × 2 ליטר"), else with the item's usual packs ([PackSizes.describe]).
 */
fun describeAmount(name: String, variant: String?, q: Quantity, pack: Pack?): String =
    pack?.describe(q) ?: PackSizes.describe(name, variant, q)

/**
 * The amount on the +/- of an item, short enough for it: packs of pieces as packs ("מארז 30", "2 מארזים של 12"),
 * anything else as it is ("2 ליטר").
 */
fun stepperAmount(name: String, variant: String?, q: Quantity, pack: Pack?): String =
    (if (pack != null) pack.packsOnly(q) else PackSizes.packLabel(name, variant, q)) ?: q.format()

/** The pack of a product in a price file, from its amount and unit and from its name. */
object ProductPacks {
    private const val NUM = """(\d+(?:[.,]\d+)?)"""
    private const val UNITS = """(ק"ג|קג|קילוגרם|קילו|גרם|גר'|גר|ג'|ג|מ"ל|מל|ליטר|ל'|ל)"""
    private val multi = Regex("""(\d{1,3})\s*[xX×*]\s*$NUM\s*$UNITS(?![\p{L}])""")
    private val multiRev = Regex("""$NUM\s*$UNITS\s*[xX×*]\s*(\d{1,3})(?![\d.])""")
    private val measure = Regex("""(?<![\d.])$NUM\s*$UNITS(?![\p{L}])""")

    /** A unit word of a name or a file ("ליטר", "גרם", "ק\"ג") as a [QuantityUnit]; null when it is not a measure. */
    fun unitOf(word: String): QuantityUnit? = when (word.replace("׳", "'").replace("״", "\"").trim().trimEnd('.')) {
        "ק\"ג", "קג", "קילוגרם", "קילו", "kg" -> QuantityUnit.KILOGRAM
        "גרם", "גר'", "גר", "ג'", "ג", "g", "gr" -> QuantityUnit.GRAM
        "מ\"ל", "מל", "ml" -> QuantityUnit.MILLILITER
        "ליטר", "ל'", "ל", "l" -> QuantityUnit.LITER
        else -> null
    }

    private fun number(s: String) = s.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

    /** One piece's size as printed ("2 ליטר", "150 גרם"), or the file's amount when it is a measure. */
    private fun each(product: BranchProduct, name: String): Quantity? {
        measure.find(name)?.let { m ->
            val n = number(m.groupValues[1]) ?: return@let
            val u = unitOf(m.groupValues[2]) ?: return@let
            return Quantity(n, u)
        }
        val size = product.packageSize?.takeIf { it > 0 } ?: return null
        val u = unitOf(product.unit) ?: return null
        return Quantity(size, u)
    }

    /**
     * What one package of [product] holds, or null when nothing says (or it is sold by weight, where there is no
     * package). A product of one piece with no size ("קוטג'") is `Pack(1)` only when its file says so.
     */
    fun of(product: BranchProduct): Pack? {
        if (product.soldByWeight) return null
        val name = product.name.replace('׳', '\'').replace('״', '"')
        multi.find(name)?.let { m ->
            val pieces = m.groupValues[1].toIntOrNull()
            val n = number(m.groupValues[2])
            val u = unitOf(m.groupValues[3])
            if (pieces != null && pieces in 2..PackCounts.MAX.toInt() && n != null && u != null) return Pack(pieces, Quantity(n, u))
        }
        multiRev.find(name)?.let { m ->
            val n = number(m.groupValues[1])
            val u = unitOf(m.groupValues[2])
            val pieces = m.groupValues[3].toIntOrNull()
            if (pieces != null && pieces in 2..PackCounts.MAX.toInt() && n != null && u != null) return Pack(pieces, Quantity(n, u))
        }
        val pieces = product.piecesPerPack?.roundToInt()?.takeIf { it >= 2 }
        val each = each(product, name)
        if (pieces != null) return Pack(pieces, each?.takeIf { !PackCounts.isPieceUnit(product.unit) || measure.containsMatchIn(name) })
        if (each != null) return Pack(1, each)
        return null
    }
}

/**
 * The pack a household buys of an item, from what it has said and done, most telling first:
 *  1. the product it chose at the branch ([ProductPacks.of]);
 *  2. the pack size it picked for the item ([MealData.packs], a base amount);
 *  3. what it usually buys ([ItemStats.typicalQuantity]) when that is a whole number of one of the item's pack
 *     sizes ([PackSizes]): 30 eggs every time is a tray of 30, 2 L of milk a 2 L carton.
 * Null when none says: then amounts are rounded to the item's pack sizes as before ([PackSizes]).
 */
object HouseholdPacks {
    fun resolve(name: String, variant: String?, chosenProduct: BranchProduct?, chosenSize: Long?, usual: Quantity?): Pack? {
        chosenProduct?.let { ProductPacks.of(it) }?.let { return it }
        val sizes = PackSizes.sizesFor(name, variant)
        if (chosenSize != null) sizes.firstOrNull { Math.round(it.baseAmount) == chosenSize }?.let { return packOfSize(it) }
        if (usual != null && usual.unit != QuantityUnit.PACKAGE) {
            // the largest pack size it buys a whole number of
            val fit = sizes.filter { it.dimension == usual.dimension && it.baseAmount > 0 }
                .filter { s -> val n = usual.baseAmount / s.baseAmount; n >= 1 - 1e-9 && abs(n - Math.round(n)) < 1e-6 }
                .maxByOrNull { it.baseAmount }
            if (fit != null && (fit.dimension != Dimension.COUNT || fit.amount >= 2)) return packOfSize(fit)
        }
        return null
    }

    private fun packOfSize(size: Quantity): Pack =
        if (size.dimension == Dimension.COUNT) Pack(Math.round(size.amount).toInt().coerceAtLeast(1)) else Pack(1, size)

    /** One branch product with its pack, and whether it is the size the household buys. */
    data class Offer(val product: BranchProduct, val pack: Pack?, val fits: Boolean)

    /**
     * The branch's products of an item for choosing one: the size the household buys first ([pack]; with none, the
     * size of the amount on the list, [wanted]: "חלב 2 ליטר" puts the 2 L cartons first), the rest after it, each
     * with its size so it is clear which is which. Order within each part is kept (cheapest first).
     */
    fun offers(candidates: List<BranchProduct>, pack: Pack?, wanted: Quantity?): List<Offer> {
        val all = candidates.map { it to ProductPacks.of(it) }
        fun fits(p: Pack?): Boolean {
            p ?: return false
            if (pack != null) return pack.sameSize(p)
            wanted ?: return false
            val n = p.packsIn(wanted) ?: return false
            return wanted.unit != QuantityUnit.PACKAGE && n >= 1 - 1e-9 && abs(n - Math.round(n)) < 1e-6 &&
                // "2 ליטר": a 2 L carton, not two of 1 L
                (Math.round(n) == 1L || all.none { (_, o) -> o?.packsIn(wanted)?.let { abs(it - 1.0) < 1e-6 } == true })
        }
        val anyFits = all.any { fits(it.second) }
        return all.map { (product, p) -> Offer(product, p, anyFits && fits(p)) }.sortedByDescending { it.fits }
    }

    /**
     * What the product chooser lists: when some of [offers] are the household's size (a tray of 30), those alone — of
     * every grade and brand (L and M, since none was chosen) — and the chosen product ([chosenCode]) whatever its
     * size; a 12-egg pack is not what it is looking for. Every size when [otherSizes] is asked for, or when none fits.
     */
    fun visible(offers: List<Offer>, chosenCode: String?, otherSizes: Boolean): List<Offer> {
        if (otherSizes || offers.none { it.fits }) return offers
        return offers.filter { it.fits || it.product.code == chosenCode }
    }
}
