package app.necessityq.domain

/**
 * How much of one thing a household normally buys in one trip, per measuring dimension:
 * [count] pieces or packages, [kilos], [liters]. Above it the amount is unusual (worth a second look);
 * far above it ([QuantityNorms.ABSURD_FACTOR] times) it is a slip — a mistyped or misread number.
 */
data class QuantityNorm(val count: Double, val kilos: Double, val liters: Double) {
    /** The usual upper amount, in the dimension's base unit (piece / gram / milliliter). */
    fun limitBase(dimension: Dimension): Double = when (dimension) {
        Dimension.COUNT -> count
        Dimension.WEIGHT -> kilos * 1000.0
        Dimension.VOLUME -> liters * 1000.0
    }
}

enum class QuantityLevel {
    /** Within what a household normally buys. */
    USUAL,
    /** More than usual: allowed, but the list flags it until someone confirms it. */
    LARGE,
    /** Far beyond any home purchase: almost surely a slip. */
    ABSURD,
}

/** The verdict on one amount of one item, with the usual upper amount to show ("בדרך כלל עד 4 יח'"). */
data class QuantityCheck(val level: QuantityLevel, val usualUpTo: Quantity) {
    val isUnusual: Boolean get() = level != QuantityLevel.USUAL

    /** "כמות גדולה מהרגיל ל־גבינה צהובה: בדרך כלל עד 3 יח'" */
    fun message(itemName: String): String = when (level) {
        QuantityLevel.USUAL -> ""
        QuantityLevel.LARGE -> "כמות גדולה מהרגיל ל$itemName — בדרך כלל עד ${usualUpTo.format()}"
        QuantityLevel.ABSURD -> "כמות חריגה מאוד ל$itemName — בדרך כלל עד ${usualUpTo.format()}. אולי טעות הקלדה?"
    }
}

/**
 * The household's sense of proportion: 30 cucumbers is a normal trip, 10 yellow cheeses is not.
 *
 * Each catalogue item has its own usual amount; unknown items take a keyword rule ("גבינ…") or their
 * category's. When the household itself usually buys more of an item (its median per trip), the usual
 * amount grows to fit it, so a big family is not warned about its own routine.
 */
object QuantityNorms {
    /** An amount this many times the usual one is a slip, not a purchase. */
    const val ABSURD_FACTOR = 5.0
    /** The household's own usual amount counts with this much room above it. */
    private const val HOUSEHOLD_ROOM = 1.5

    private fun n(count: Number, kilos: Number, liters: Number) = QuantityNorm(count.toDouble(), kilos.toDouble(), liters.toDouble())

    private val byCategory: Map<Category, QuantityNorm> = mapOf(
        Category.PRODUCE to n(30, 6, 5),
        Category.DAIRY to n(8, 2, 6),
        Category.BAKERY to n(6, 3, 2),
        Category.MEAT_FISH to n(6, 4, 2),
        Category.PANTRY to n(6, 5, 4),
        Category.BREAKFAST_SNACKS to n(10, 3, 2),
        Category.DRINKS to n(12, 10, 24),
        Category.FROZEN to n(8, 4, 4),
        Category.CLEANING to n(6, 6, 8),
        Category.TOILETRIES to n(5, 2, 3),
        Category.BABY to n(6, 4, 4),
        Category.OTHER to n(10, 5, 6),
    )

    /** Keyed by the catalogue's canonical name ([Catalog.lookup]). */
    private val byItem: Map<String, QuantityNorm> = mapOf(
        // ירקות ופירות
        "עגבניות" to n(25, 4, 5), "מלפפונים" to n(30, 4, 5), "בצל" to n(15, 4, 5), "שום" to n(6, 1, 1),
        "תפוחי אדמה" to n(25, 6, 5), "גזר" to n(20, 3, 5), "פלפל" to n(12, 3, 5), "חסה" to n(4, 2, 2),
        "פטרוזיליה" to n(4, 1, 1), "כוסברה" to n(4, 1, 1), "לימון" to n(15, 2, 2), "בננות" to n(20, 4, 5),
        "תפוחים" to n(20, 4, 5), "תפוזים" to n(25, 6, 5), "אבוקדו" to n(10, 3, 3), "קישואים" to n(15, 3, 3),
        "בטטה" to n(10, 4, 4),
        // מוצרי חלב וביצים
        "חלב" to n(8, 2, 10), "קוטג'" to n(8, 2, 3), "גבינה צהובה" to n(3, 1.5, 1), "גבינה לבנה" to n(6, 2, 3),
        "גבינת שמנת" to n(4, 1, 1), "גבינה בולגרית" to n(3, 1, 1), "מוצרלה" to n(3, 1, 1), "יוגורט" to n(16, 3, 3),
        "לבן" to n(6, 2, 3), "שמנת מתוקה" to n(4, 1, 2), "שמנת חמוצה" to n(4, 1, 2), "חמאה" to n(4, 1, 1),
        "ביצים" to n(60, 4, 1), "מעדנים" to n(16, 3, 3),
        // לחם ומאפים
        "לחם" to n(4, 3, 1), "לחם מלא" to n(4, 3, 1), "פיתות" to n(30, 3, 1), "לחמניות" to n(24, 3, 1),
        "חלות" to n(4, 3, 1), "טורטיות" to n(20, 2, 1),
        // בשר, עוף ודגים
        "חזה עוף" to n(6, 4, 1), "שניצל" to n(6, 3, 1), "כרעיים" to n(12, 4, 1), "בשר טחון" to n(4, 3, 1),
        "נקניקיות" to n(6, 2, 1), "פסטרמה" to n(4, 1, 1), "סלמון" to n(4, 2, 1), "טונה" to n(12, 2, 1),
        // מזווה ובישול
        "אורז" to n(4, 5, 1), "פסטה" to n(8, 4, 1), "קמח" to n(4, 5, 1), "סוכר" to n(4, 5, 1), "מלח" to n(3, 2, 1),
        "שמן" to n(4, 4, 5), "שמן זית" to n(3, 3, 3), "רסק עגבניות" to n(10, 2, 2), "טחינה" to n(4, 2, 2),
        "חומוס" to n(6, 3, 2), "קטשופ" to n(3, 2, 2), "מיונז" to n(3, 2, 2), "תבלינים" to n(6, 1, 1),
        "קפה" to n(4, 2, 1), "תה" to n(4, 1, 1), "דבש" to n(3, 2, 2), "ממרח שוקולד" to n(3, 2, 1),
        "עדשים" to n(4, 3, 1), "קוסקוס" to n(4, 3, 1),
        // דגני בוקר וחטיפים
        "קורנפלקס" to n(4, 3, 1), "דגני בוקר" to n(4, 3, 1), "גרנולה" to n(3, 2, 1), "שיבולת שועל" to n(3, 3, 1),
        "עוגיות" to n(6, 2, 1), "ביסלי" to n(12, 2, 1), "במבה" to n(12, 2, 1), "קרקרים" to n(6, 2, 1),
        "שוקולד" to n(10, 2, 1), "חטיפי דגנים" to n(6, 2, 1),
        // משקאות
        "מים מינרליים" to n(36, 10, 54), "סודה" to n(24, 10, 36), "מיץ" to n(6, 10, 8), "משקה קל" to n(12, 10, 18),
        "מיץ ענבים" to n(4, 5, 4),
        // קפואים
        "ירקות קפואים" to n(6, 4, 1), "גלידה" to n(3, 3, 4), "בורקס" to n(4, 4, 1),
        // ניקיון ובית
        "נייר טואלט" to n(40, 5, 1), "מגבות נייר" to n(12, 3, 1), "סבון כלים" to n(3, 3, 4), "אבקת כביסה" to n(3, 6, 6),
        "מרכך כביסה" to n(3, 6, 6), "שקיות אשפה" to n(6, 2, 1), "נייר כסף" to n(3, 2, 1), "חומר ניקוי" to n(6, 4, 8),
        // טואלטיקה ותינוקות
        "סבון ידיים" to n(4, 2, 3), "שמפו" to n(4, 2, 3), "משחת שיניים" to n(4, 1, 1), "מברשות שיניים" to n(8, 1, 1),
        "דאודורנט" to n(4, 1, 1), "חיתולים" to n(6, 6, 1), "מגבונים" to n(10, 3, 1),
    )

    /** For names outside the catalogue: the first keyword the name contains decides. */
    private val byKeyword: List<Pair<String, QuantityNorm>> = listOf(
        "גבינ" to n(4, 1.5, 1),
        "שמנת" to n(4, 1, 2),
        "יוגורט" to n(16, 3, 3),
        "חלב" to n(8, 2, 10),
        "לחם" to n(4, 3, 1),
        "בשר" to n(6, 4, 1),
        "עוף" to n(6, 4, 1),
        "שמן" to n(4, 4, 5),
        "תבלין" to n(6, 1, 1),
    )

    fun normFor(name: String, category: Category): QuantityNorm {
        Catalog.lookup(name)?.let { def -> byItem[def.name]?.let { return it } }
        val key = HebrewText.matchKey(name)
        byKeyword.firstOrNull { (word, _) -> key.contains(HebrewText.matchKey(word)) }?.let { return it.second }
        return byCategory[category] ?: byCategory.getValue(Category.OTHER)
    }

    /**
     * Judges [quantity] of the item. [householdUsual] is what this household usually buys of it (the
     * median per trip, [ItemStats.typicalQuantity]); a larger habit widens the usual amount. Null for no quantity.
     */
    fun check(name: String, category: Category, quantity: Quantity?, householdUsual: Quantity? = null): QuantityCheck? {
        quantity ?: return null
        val dim = quantity.dimension
        var limit = normFor(name, category).limitBase(dim)
        if (householdUsual != null && householdUsual.dimension == dim) limit = maxOf(limit, householdUsual.baseAmount * HOUSEHOLD_ROOM)
        // the usual amount of an item sold in packs of pieces (eggs, pittas) is in pieces: "30 חב' ביצים" is 30 trays
        val piecesInPack = if (quantity.unit == QuantityUnit.PACKAGE) PackSizes.smallestPack(name)?.takeIf { it.dimension == Dimension.COUNT }?.amount else null
        if (piecesInPack != null && piecesInPack >= 2.0) {
            val pieces = quantity.amount * piecesInPack
            val level = when {
                pieces > limit * ABSURD_FACTOR + EPS -> QuantityLevel.ABSURD
                pieces > limit + EPS -> QuantityLevel.LARGE
                else -> QuantityLevel.USUAL
            }
            return QuantityCheck(level, Quantity(limit, QuantityUnit.PIECE))
        }
        val amount = quantity.baseAmount
        val level = when {
            amount > limit * ABSURD_FACTOR + EPS -> QuantityLevel.ABSURD
            amount > limit + EPS -> QuantityLevel.LARGE
            else -> QuantityLevel.USUAL
        }
        return QuantityCheck(level, usual(limit, quantity.unit))
    }

    fun check(item: ItemIdentity, quantity: Quantity?, householdUsual: Quantity? = null): QuantityCheck? =
        check(item.name, item.category, quantity, householdUsual)

    /** The check for a line on the list, unless someone already confirmed this amount (or more). */
    fun warningFor(line: ListLine, householdUsual: Quantity?): QuantityCheck? {
        val check = check(line.name, line.category, line.quantity, householdUsual)?.takeIf { it.isUnusual } ?: return null
        val approved = line.approvedQuantity
        val q = line.quantity!!
        if (approved != null && approved.dimension == q.dimension && q.baseAmount <= approved.baseAmount + EPS) return null
        return check
    }

    /**
     * For amounts the app puts on the list by itself (suggestions, past purchases, recipes, the menu): a slip
     * becomes a usual amount — the household's own if it has one, otherwise the item's first amount in the slip's kind
     * ("300 ליטר חלב": a litre; "300 ביצים": one pack, see [QuantitySteps.firstFor]), else one unit. Anything else is kept
     * (a large one is flagged on the list, see [warningFor]).
     */
    fun homeSized(name: String, category: Category, quantity: Quantity?, householdUsual: Quantity? = null): Quantity? {
        val check = check(name, category, quantity, householdUsual) ?: return null
        if (check.level != QuantityLevel.ABSURD) return quantity
        return householdUsual?.takeIf { check(name, category, it)?.level != QuantityLevel.ABSURD } ?: QuantitySteps.firstFor(name).takeIf { it.dimension == quantity?.dimension } ?: Quantity.pieces(1)
    }

    /** The usual upper amount in a readable unit close to the one used ("4 יח'", "3 ק\"ג", "1.5 ליטר"). */
    private fun usual(limitBase: Double, unit: QuantityUnit): Quantity = when (unit) {
        QuantityUnit.PIECE, QuantityUnit.PACKAGE -> Quantity(limitBase, unit)
        else -> Quantity.fromBase(limitBase, unit.dimension)
    }

    private const val EPS = 1e-6
}
