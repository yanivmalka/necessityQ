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

    /** Keyed by the name as [HebrewText.matchKey] writes it. */
    private val rules: Map<String, Rule> = listOf(
        // חלב וביצים
        "חלב" to v(1000, 1500, head = true), "שמנת מתוקה" to v(250, 500), "שמנת חמוצה" to w(200), "קוטג'" to w(250),
        "חמאה" to w(200), "גבינה לבנה" to w(250, 500), "ביצים" to c(6, 12, 30), "ביצה" to c(6, 12, 30),
        // מזווה
        "קמח" to w(1000, head = true), "סוכר" to w(1000, head = true), "סוכר חום" to w(500), "אבקת סוכר" to w(500),
        "מלח" to w(1000), "אורז" to w(500, 1000, head = true), "פסטה" to w(500, head = true), "סולת" to w(1000),
        "עדשים" to w(500, head = true), "קוסקוס" to w(500), "שיבולת שועל" to w(500), "טחינה" to w(500),
        "שמן" to v(1000), "שמן קנולה" to v(1000), "שמן חמניות" to v(1000), "שמן זית" to v(500, 750),
    ).associate { (name, rule) -> HebrewText.matchKey(name) to rule }

    /** The head-word keys, longest first, so "שיבולת שועל" is tried before "שיבולת". */
    private val headKeys: List<String> = rules.filterValues { it.head }.keys.sortedByDescending { it.length }

    private fun ruleFor(name: String): Rule? {
        val canonical = HebrewText.matchKey(Catalog.lookup(name)?.name ?: name)
        rules[canonical]?.let { return it }
        val key = HebrewText.matchKey(name)
        rules[key]?.let { return it }
        return headKeys.firstOrNull { key == it || key.startsWith("$it ") }?.let { rules[it] }
    }

    /** True when [name] is sold in fixed packs (and so [toBuy] may change its amount). */
    fun isPackaged(name: String): Boolean = ruleFor(name) != null

    /** The smallest pack size of the item in the base unit, or null when it has no pack sizes. */
    fun smallestPack(name: String): Quantity? = ruleFor(name)?.let { rule ->
        rule.sizes.minOrNull()?.let { Quantity.fromBase(it.toDouble(), rule.dimension) }
    }

    /**
     * What to buy for [need] of [name]: the smallest total of packs that covers it (fewest packs on a tie).
     * The need itself when the item has no pack sizes, or is measured in another dimension than its packs.
     */
    fun toBuy(name: String, need: Quantity?): Quantity? {
        need ?: return null
        val rule = ruleFor(name) ?: return need
        if (rule.dimension != need.dimension) return need
        val total = cover(need.baseAmount, rule.sizes)
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
