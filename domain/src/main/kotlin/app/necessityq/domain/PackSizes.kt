package app.necessityq.domain

import kotlin.math.ceil

/**
 * What can actually be bought: packaged goods come in fixed sizes, so a recipe's exact need ("1.1 ליטר חלב",
 * "חצי ק\"ג קמח") becomes the smallest amount the shop sells that covers it ("1.5 ליטר", "1 ק\"ג").
 *
 * A line on the list keeps the exact need (recipes, servings and the menu add and subtract it); [toBuy] is what
 * goes in the basket. Goods sold loose by weight (produce, fresh meat, cheese by the slice) have no pack size and
 * are left as they are.
 */
object PackSizes {
    /**
     * [sizes] in the base unit of [dimension] (gram / milliliter / piece), as the shop sells them.
     * [head]: names that start with the key word count too ("קמח מלא", "אורז בסמטי"); otherwise the name must equal it.
     */
    private class Rule(val dimension: Dimension, val sizes: List<Long>, val head: Boolean = false)

    private fun w(vararg grams: Long, head: Boolean = false) = Rule(Dimension.WEIGHT, grams.toList(), head)
    private fun v(vararg ml: Long, head: Boolean = false) = Rule(Dimension.VOLUME, ml.toList(), head)
    private fun c(vararg pieces: Long) = Rule(Dimension.COUNT, pieces.toList())

    /**
     * Every catalogue item that is sold in fixed packs and that a recipe can ask for by weight, volume or count.
     * Keyed by the catalogue's name ([Catalog.items]); aliases ("שמן קנולה", "ספגטי") reach it through [Catalog.lookup].
     * Sizes are the ones on Israeli shelves; to change what the app rounds to, change them here.
     */
    private val rules: Map<String, Rule> = listOf(
        // חלב וביצים
        "חלב" to v(1000, 1500, head = true), "שמנת מתוקה" to v(250, 500), "שמנת חמוצה" to w(200), "קוטג'" to w(250, 500),
        "חמאה" to w(200), "גבינה לבנה" to w(250, 500), "גבינת שמנת" to w(200), "מוצרלה" to w(200, 400),
        "יוגורט" to w(200, 500), "לבן" to w(200, 500, 1000), "ביצים" to c(6, 12, 30), "ביצה" to c(6, 12, 30),
        // מזווה
        "קמח" to w(1000, head = true), "סוכר" to w(1000, head = true), "סוכר חום" to w(500), "אבקת סוכר" to w(500),
        "מלח" to w(1000), "אורז" to w(500, 1000, head = true), "פסטה" to w(500, head = true), "סולת" to w(1000),
        "עדשים" to w(500, head = true), "קוסקוס" to w(500), "שיבולת שועל" to w(500), "טחינה" to w(500, 1000),
        "שמן" to v(1000), "שמן קנולה" to v(1000), "שמן חמניות" to v(1000), "שמן זית" to v(500, 750),
        "רסק עגבניות" to w(140, 500), "קטשופ" to w(500, 750), "מיונז" to w(500), "קפה" to w(100, 200),
        "דבש" to w(250, 500), "ממרח שוקולד" to w(350, 750), "שוקולד" to w(100),
        // חומוס: גביע מוכן (250 / 500) או שימורי גרגירים (400)
        "חומוס" to w(250, 400, 500), "תה" to c(25, 50, 100),
        // דגני בוקר
        "קורנפלקס" to w(375, 750),
        // מאפים באריזה
        "פיתות" to c(6, 10), "טורטיות" to c(6, 8, 10),
        // משקאות
        "מים מינרליים" to v(500, 1500), "סודה" to v(1500), "מיץ" to v(1000, 1500), "משקה קל" to v(1500, 2000),
        "מיץ ענבים" to v(750, 1000),
        // קפואים
        "ירקות קפואים" to w(400, 800), "גלידה" to v(500, 1000),
    ).associate { (name, rule) -> HebrewText.matchKey(name) to rule }

    /**
     * The rest of the catalogue, and why a recipe's amount of it is left as it is:
     *  - sold loose by weight or by the piece: produce, fresh meat and fish, deli cheese, bakery;
     *  - sold in packs that vary too much by brand and shop to round to (cereal, snacks, frozen pastry, desserts);
     *  - not food, so no recipe or menu asks for them (cleaning, toiletries, baby).
     */
    internal val notPacked: Set<String> = listOf(
        "עגבניות", "מלפפונים", "בצל", "שום", "תפוחי אדמה", "גזר", "פלפל", "חסה", "פטרוזיליה", "כוסברה", "לימון", "בננות",
        "תפוחים", "תפוזים", "אבוקדו", "קישואים", "בטטה",
        "גבינה צהובה", "גבינה בולגרית", "מעדנים",
        "לחם", "לחם מלא", "לחמניות", "חלות",
        "חזה עוף", "שניצל", "כרעיים", "בשר טחון", "נקניקיות", "פסטרמה", "סלמון", "טונה",
        "תבלינים", "דגני בוקר", "גרנולה", "עוגיות", "ביסלי", "במבה", "קרקרים", "חטיפי דגנים", "בורקס",
        "נייר טואלט", "מגבות נייר", "סבון כלים", "אבקת כביסה", "מרכך כביסה", "שקיות אשפה", "נייר כסף", "חומר ניקוי",
        "סבון ידיים", "שמפו", "משחת שיניים", "מברשות שיניים", "דאודורנט", "חיתולים", "מגבונים", "תמ\"ל",
    ).map(HebrewText::matchKey).toSet()

    /** Catalogue names that have a pack rule. */
    internal val packagedNames: Set<String> get() = rules.keys

    /** The head-word keys, longest first, so "שיבולת שועל" is tried before "שיבולת". */
    private val headKeys: List<String> = rules.filterValues { it.head }.keys.sortedByDescending { it.length }

    /** The rule for the item and the key it is stored under; the item's kind first ("סוכר חום" is not "סוכר"). */
    private fun ruleFor(name: String, variant: String? = null): Pair<String, Rule>? {
        if (!variant.isNullOrBlank()) rules[HebrewText.matchKey("$name $variant")]?.let { return HebrewText.matchKey("$name $variant") to it }
        val canonical = HebrewText.matchKey(Catalog.lookup(name)?.name ?: name)
        rules[canonical]?.let { return canonical to it }
        val key = HebrewText.matchKey(name)
        rules[key]?.let { return key to it }
        return headKeys.firstOrNull { key == it || key.startsWith("$it ") }?.let { it to rules.getValue(it) }
    }

    /** True when [name] is sold in fixed packs (and so [toBuy] may change its amount). */
    fun isPackaged(name: String, variant: String? = null): Boolean = ruleFor(name, variant) != null

    /** What a household's chosen pack size is stored under ([MealData.packs]); null when the item has no pack sizes. */
    fun keyFor(name: String, variant: String? = null): String? = ruleFor(name, variant)?.first

    /** The pack sizes the item is sold in, smallest first; empty when it has none. */
    fun sizesFor(name: String, variant: String? = null): List<Quantity> = ruleFor(name, variant)?.second?.let { rule ->
        rule.sizes.sorted().map { Quantity.fromBase(it.toDouble(), rule.dimension) }
    }.orEmpty()

    /** The smallest pack size of the item in the base unit, or null when it has no pack sizes. */
    fun smallestPack(name: String, variant: String? = null): Quantity? = sizesFor(name, variant).firstOrNull()

    /**
     * What to buy for [need] of [name]: the smallest total of packs that covers it (fewest packs on a tie).
     * [preferred]: the one pack size the household buys (a base amount, one of the item's sizes); null = any size.
     * The need itself when the item has no pack sizes, or is measured in another dimension than its packs.
     */
    fun toBuy(name: String, need: Quantity?, variant: String? = null, preferred: Long? = null): Quantity? {
        need ?: return null
        val rule = ruleFor(name, variant)?.second ?: return need
        if (rule.dimension != need.dimension) return need
        val sizes = if (preferred != null && preferred in rule.sizes) listOf(preferred) else rule.sizes
        val total = cover(need.baseAmount, sizes)
        return if (rule.dimension == Dimension.COUNT) Quantity(total.toDouble(), if (need.unit == QuantityUnit.PACKAGE) QuantityUnit.PACKAGE else QuantityUnit.PIECE)
        else Quantity.fromBase(total.toDouble(), rule.dimension)
    }

    /** The smallest sum of the pack [sizes] (each usable any number of times) that is at least [need]. */
    internal fun cover(need: Double, sizes: List<Long>): Long {
        val largest = sizes.max()
        val target = ceil(need - 1e-9).toLong().coerceAtLeast(1)
        // a very large need: whole packs of the largest size are the only sensible answer
        if (target > largest * 400) return ceil(target.toDouble() / largest).toLong() * largest
        val unit = sizes.reduce(::gcd)
        val steps = sizes.map { (it / unit).toInt() }
        val goal = ceil(target.toDouble() / unit).toInt()
        val limit = goal + steps.max()
        // fewest packs that make exactly t units; the first t >= goal that can be made wins
        val packs = IntArray(limit + 1) { Int.MAX_VALUE }
        packs[0] = 0
        for (t in 1..limit) {
            for (s in steps) if (t >= s && packs[t - s] != Int.MAX_VALUE) packs[t] = minOf(packs[t], packs[t - s] + 1)
        }
        val best = (goal..limit).first { packs[it] != Int.MAX_VALUE }
        return best.toLong() * unit
    }

    private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
}
