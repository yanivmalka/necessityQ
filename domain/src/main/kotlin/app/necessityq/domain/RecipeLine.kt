package app.necessityq.domain

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Reads one ingredient line of a recipe ("2 כפיות סוכר", "1 וחצי כוסות אורז", "בצל גדול קצוץ דק",
 * "1 ק\"ג תפוחי אדמה, קלופים") into the item to buy and how much of it.
 *
 * [QuickEntryParser] reads what is typed on the shopping list, where units are shopping units (ק"ג, ליטר, חבילה).
 * A recipe also speaks in kitchen units (כוס, כף, כפית, קורט, שן שום) and adds how the food is prepared. This
 * reader leaves the preparation out of the item's name, turns kitchen units into grams or milliliters where the
 * item's density is known (a cup of flour is not a cup of milk), and passes the rest to [QuickEntryParser].
 */
object RecipeLine {
    private enum class Kind { CUP, TBSP, TSP, PINCH, HANDFUL, BUNCH, CLOVE, SLICE, CAN, PACK }

    private val kitchenUnits: Map<String, Kind> = buildMap {
        fun put(kind: Kind, vararg words: String) = words.forEach { put(HebrewText.matchKey(it), kind) }
        put(Kind.CUP, "כוס", "כוסות", "כוסית", "cup", "cups")
        put(Kind.TBSP, "כף", "כפות", "tbsp", "tablespoon", "tablespoons")
        put(Kind.TSP, "כפית", "כפיות", "tsp", "teaspoon", "teaspoons")
        put(Kind.PINCH, "קורט", "מעט", "קמצוץ", "pinch")
        put(Kind.HANDFUL, "חופן", "חופנים")
        put(Kind.BUNCH, "צרור", "צרורות", "bunch")
        put(Kind.CLOVE, "שן", "שיני", "שיניים", "clove", "cloves")
        put(Kind.SLICE, "פרוסה", "פרוסות", "פרוסת", "slice", "slices")
        put(Kind.CAN, "פחית", "פחיות", "קופסה", "קופסת", "קופסאות", "צנצנת", "צנצנות", "בקבוק", "בקבוקים", "בקבוקי", "can", "cans")
        put(Kind.PACK, "חבילה", "חבילת", "חבילות", "שקית", "שקיות", "אריזה", "אריזת", "אריזות", "מארז", "מארזים")
    }

    /** How food is prepared or sized: not part of what is bought. */
    private val descriptors: Set<String> = listOf(
        "גדול", "גדולה", "גדולים", "גדולות", "בינוני", "בינונית", "בינוניים", "בינוניות", "קטן", "קטנה", "קטנים", "קטנות",
        "קצוץ", "קצוצה", "קצוצים", "קצוצות", "כתוש", "כתושה", "כתושים", "כתושות", "מגורר", "מגוררת", "מגוררים", "מגוררות",
        "פרוס", "פרוסה", "פרוסים", "פרוסות", "חתוך", "חתוכה", "חתוכים", "חתוכות", "קלוף", "קלופה", "קלופים", "קלופות",
        "מוקצף", "מוקצפת", "מוקצפים", "מוקצפות", "מומס", "מומסת", "מותך", "מותכת", "רך", "רכה", "מנופה", "מנופים",
        "שטוף", "שטופים", "טרי", "טריה", "טריים", "טריות", "דק", "גס", "מעט", "קצת",
    ).map(HebrewText::matchKey).toSet()

    /** Phrases that say how much / how to use, not what to buy. */
    private val phrases = listOf(
        "לפי הטעם", "לפי הצורך", "לפי הרצון", "לטעם", "לקישוט", "להגשה", "לטיגון", "לשימון", "לאפייה", "לאפיה", "לציפוי",
        "לפיזור", "אופציונלי", "לא חובה", "אם רוצים",
    )

    private val fractionChars = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)
    private val numberToken = Regex("^(\\d+(?:[.,]\\d+)?)$")

    private const val CUP_ML = 240.0
    private const val TBSP_ML = 15.0
    private const val TSP_ML = 5.0

    /** Cloves in a head of garlic, to turn "4 שיני שום" into a head to buy. */
    private const val CLOVES_PER_HEAD = 10.0

    fun parse(input: String): ParsedEntry? {
        var text = HebrewText.clean(input)
        if (text.isBlank()) return null
        text = text.replace(Regex("\\([^)]*\\)"), " ") // (500 גרם), (אופציונלי)
        text = text.replace(Regex("^[^:\\d]{1,24}:\\s*"), "") // "לקישוט: פטרוזיליה"
        text = text.replace(Regex("\\s*,(?!\\d)[^\\n]*"), "") // "תפוחי אדמה, קלופים וחתוכים"
        phrases.forEach { text = text.replace(it, " ") }
        text = normalizeNumbers(text)

        val tokens = text.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        var amount: Double? = null
        var i = 0
        number(tokens[0])?.let {
            amount = it
            i = 1
            when (tokens.getOrNull(i)?.let(HebrewText::matchKey)) {
                "וחצי" -> { amount = it + 0.5; i++ }
                "ורבע" -> { amount = it + 0.25; i++ }
            }
        }
        val kind = tokens.getOrNull(i)?.trimEnd('.')?.let { kitchenUnits[HebrewText.matchKey(it)] }

        if (kind == null) {
            // shopping units and everything else: the shopping-list reader, without the preparation words
            val kept = stripDescriptors(tokens)
            return QuickEntryParser.parse(kept.joinToString(" "))?.let { it.copy(item = identity(it.nameText, it.item), raw = input) }
        }

        i++
        if (tokens.getOrNull(i) == "של") i++
        val name = stripDescriptors(tokens.drop(i)).joinToString(" ").trim()
        if (name.isBlank()) return null
        val item = identity(name)
        return ParsedEntry(item, quantityOf(kind, amount ?: 1.0, item.name), input, name)
    }

    /** The item for the words of a recipe line. "מים" is tap water here, not the catalogue's bottled water. */
    private fun identity(name: String, parsed: ItemIdentity = ItemIdentity.fromText(name)): ItemIdentity =
        if (HebrewText.matchKey(name) == "מים") parsed.copy(name = "מים", variant = null) else parsed

    /** What a number of [kind] of [itemName] comes to as something to buy; null when it cannot be told ("חופן", "קורט"). */
    private fun quantityOf(kind: Kind, amount: Double, itemName: String): Quantity? = when (kind) {
        Kind.CUP -> measured(amount * CUP_ML, itemName)
        Kind.TBSP -> measured(amount * TBSP_ML, itemName)
        Kind.TSP -> measured(amount * TSP_ML, itemName)
        Kind.BUNCH -> Quantity.pieces(amount.coerceAtLeast(1.0))
        Kind.CLOVE -> Quantity.pieces(ceil(amount / CLOVES_PER_HEAD).coerceAtLeast(1.0))
        Kind.CAN -> Quantity(amount, QuantityUnit.PIECE)
        Kind.PACK -> Quantity(amount, QuantityUnit.PACKAGE)
        Kind.PINCH, Kind.HANDFUL, Kind.SLICE -> null
    }

    /** [ml] of volume of the item: milliliters for liquids, grams for foods whose weight per cup is known, else unknown. */
    private fun measured(ml: Double, itemName: String): Quantity? {
        if (Density.isLiquid(itemName)) return Quantity.fromBase(ml.roundToInt().coerceAtLeast(1).toDouble(), Dimension.VOLUME)
        val gramsPerCup = Density.gramsPerCup(itemName) ?: return null
        return Quantity.fromBase((ml / CUP_ML * gramsPerCup).roundToInt().coerceAtLeast(1).toDouble(), Dimension.WEIGHT)
    }

    private fun number(token: String): Double? =
        numberToken.matchEntire(token)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            ?: QuickEntryParser.wordNumbers[HebrewText.matchKey(token)]

    /** Words that only describe the preparation, unless that would leave nothing. */
    private fun stripDescriptors(tokens: List<String>): List<String> {
        val kept = tokens.filter { HebrewText.matchKey(it) !in descriptors }
        return if (kept.none { number(it) == null }) tokens else kept
    }

    /** "½", "1/2", "1 1/2", "3-4", "2 עד 3" → one plain number ("0.5", "0.5", "1.5", "4", "3"). */
    private fun normalizeNumbers(text: String): String {
        fun fmt(d: Double) = String.format(Locale.ROOT, "%.3f", d).trimEnd('0').trimEnd('.')
        var t = text
        t = Regex("(\\d+)\\s*([½¼¾⅓⅔])").replace(t) { fmt(it.groupValues[1].toDouble() + fractionChars.getValue(it.groupValues[2][0])) }
        t = Regex("[½¼¾⅓⅔]").replace(t) { fmt(fractionChars.getValue(it.value[0])) }
        t = Regex("(\\d+)\\s+(\\d+)/(\\d+)").replace(t) { m ->
            val d = m.groupValues[3].toDouble()
            if (d == 0.0) m.value else fmt(m.groupValues[1].toDouble() + m.groupValues[2].toDouble() / d)
        }
        t = Regex("(\\d+)/(\\d+)").replace(t) { m ->
            val d = m.groupValues[2].toDouble()
            if (d == 0.0) m.value else fmt(m.groupValues[1].toDouble() / d)
        }
        t = Regex("(\\d+(?:[.,]\\d+)?)\\s*[-–]\\s*(\\d+(?:[.,]\\d+)?)").replace(t) { it.groupValues[2] }
        t = Regex("(\\d+(?:[.,]\\d+)?)\\s+עד\\s+(\\d+(?:[.,]\\d+)?)").replace(t) { it.groupValues[2] }
        return t.replace(Regex("\\s+"), " ").trim()
    }
}

/** What a cup of a food weighs, and which foods are liquids: for turning kitchen measures into things to buy. */
internal object Density {
    private val liquidWords = setOf(
        "חלב", "מים", "שמן", "מיץ", "יין", "חומץ", "שמנת", "סירופ", "רוטב", "ציר", "מרק", "בירה", "סודה", "לבן", "קפה",
    ).map(HebrewText::matchKey).toSet()

    fun isLiquid(name: String): Boolean = HebrewText.matchKey(name).split(' ').any { it in liquidWords }

    /** Most specific first: the first key the name contains decides. */
    private val gramsPerCup: List<Pair<String, Double>> = listOf(
        "אבקת סוכר" to 120.0, "סוכר חום" to 220.0, "סוכר" to 200.0, "קמח" to 130.0, "אורז" to 190.0,
        "שיבולת שועל" to 90.0, "סולת" to 170.0, "קקאו" to 100.0, "אגוזים" to 120.0, "שקדים" to 140.0, "צימוקים" to 150.0,
        "פירורי לחם" to 110.0, "פנקו" to 60.0, "עדשים" to 200.0, "קוסקוס" to 170.0, "בורגול" to 160.0, "קורנפלקס" to 30.0,
        "מלח" to 290.0, "גבינה צהובה" to 100.0, "גבינה" to 100.0, "קוטג" to 240.0, "יוגורט" to 245.0, "טחינה" to 250.0,
        "דבש" to 340.0, "חמאה" to 227.0, "שוקולד" to 170.0, "קוקוס" to 80.0, "פסטה" to 100.0, "תירס" to 160.0,
        "אפונה" to 150.0, "פטריות" to 70.0,
    ).map { (k, g) -> HebrewText.matchKey(k) to g }

    fun gramsPerCup(name: String): Double? {
        val key = HebrewText.matchKey(name)
        return gramsPerCup.firstOrNull { (word, _) -> key.contains(word) }?.second
    }
}
