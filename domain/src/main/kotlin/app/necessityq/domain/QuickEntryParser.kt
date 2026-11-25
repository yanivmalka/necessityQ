package app.necessityq.domain

data class ParsedEntry(
    val item: ItemIdentity,
    val quantity: Quantity?,
    val raw: String,
    /** The words that name the item, as written (without the amount and unit). */
    val nameText: String = "",
)

/**
 * Parses quick free-text entries typed on the list:
 *
 *  - "2 קוטג' 5%"        → קוטג' 5%, 2 יח'
 *  - "קוטג 9% x3"        → קוטג' 9%, 3 יח'
 *  - "1.5 ק\"ג עגבניות"  → עגבניות, 1.5 ק"ג
 *  - "חצי קילו גבינה צהובה" → גבינה צהובה, 0.5 ק"ג
 *  - "שתי חבילות פסטה"   → פסטה, 2 חב'
 *  - "חלב 3% 2 ליטר"     → חלב 3%, 2 ליטר
 */
object QuickEntryParser {

    internal val wordNumbers: Map<String, Double> = mapOf(
        "רבע" to 0.25, "חצי" to 0.5,
        "אחד" to 1.0, "אחת" to 1.0,
        "שניים" to 2.0, "שתיים" to 2.0, "שני" to 2.0, "שתי" to 2.0, "זוג" to 2.0,
        "שלושה" to 3.0, "שלוש" to 3.0, "ארבעה" to 4.0, "ארבע" to 4.0,
        "חמישה" to 5.0, "חמש" to 5.0, "שישה" to 6.0, "שש" to 6.0,
        "שבעה" to 7.0, "שבע" to 7.0, "שמונה" to 8.0, "תשעה" to 9.0, "תשע" to 9.0,
        "עשרה" to 10.0, "עשר" to 10.0, "תריסר" to 12.0,
    )

    /** Unit words, compared by [HebrewText.matchKey] (quotes/apostrophes removed). */
    internal val unitWords: Map<String, QuantityUnit> = mapOf(
        "קג" to QuantityUnit.KILOGRAM, "קילו" to QuantityUnit.KILOGRAM, "קילוגרם" to QuantityUnit.KILOGRAM, "kg" to QuantityUnit.KILOGRAM,
        "גרם" to QuantityUnit.GRAM, "גר" to QuantityUnit.GRAM, "g" to QuantityUnit.GRAM,
        "ליטר" to QuantityUnit.LITER, "ליטרים" to QuantityUnit.LITER, "l" to QuantityUnit.LITER,
        "מל" to QuantityUnit.MILLILITER, "ml" to QuantityUnit.MILLILITER,
        "יח" to QuantityUnit.PIECE, "יחידה" to QuantityUnit.PIECE, "יחידות" to QuantityUnit.PIECE,
        "חבילה" to QuantityUnit.PACKAGE, "חבילות" to QuantityUnit.PACKAGE, "חב" to QuantityUnit.PACKAGE,
        "אריזה" to QuantityUnit.PACKAGE, "אריזות" to QuantityUnit.PACKAGE,
        "מארז" to QuantityUnit.PACKAGE, "מארזים" to QuantityUnit.PACKAGE,
        "שקית" to QuantityUnit.PACKAGE, "שקיות" to QuantityUnit.PACKAGE,
        "בקבוק" to QuantityUnit.PIECE, "בקבוקים" to QuantityUnit.PIECE,
    )

    private val numberToken = Regex("^(?:[xX×*])?(\\d+(?:[.,]\\d+)?)(?:[xX×*])?$")
    private val percentToken = Regex("^\\d+(?:[.,]\\d+)?%$")

    fun parse(input: String): ParsedEntry? {
        val text = HebrewText.clean(input)
        if (text.isBlank()) return null
        val tokens = text.split(' ').filter { it.isNotBlank() }.toMutableList()

        var amount: Double? = null
        var unit: QuantityUnit? = null

        fun numberAt(i: Int): Double? {
            val token = tokens.getOrNull(i) ?: return null
            if (percentToken.matches(token)) return null
            numberToken.matchEntire(token)?.let { return it.groupValues[1].replace(',', '.').toDoubleOrNull() }
            return wordNumbers[HebrewText.matchKey(token)]
        }

        fun unitAt(i: Int): QuantityUnit? = tokens.getOrNull(i)?.let { unitWords[HebrewText.matchKey(it)] }

        // Leading quantity: "2 ...", "2 ק\"ג ...", "חצי קילו ...", "קילו ..."
        val leadNumber = numberAt(0)
        if (leadNumber != null && tokens.size > 1) {
            amount = leadNumber
            tokens.removeAt(0)
            unitAt(0)?.let { unit = it; tokens.removeAt(0) }
        } else if (unitAt(0) != null && tokens.size > 1 && unitAt(0) != QuantityUnit.PIECE) {
            unit = unitAt(0)
            amount = 1.0
            tokens.removeAt(0)
        }

        // Trailing quantity: "... x3", "... 3", "... 2 ליטר", "... 500 גרם"
        if (amount == null && tokens.size > 1) {
            val last = tokens.lastIndex
            val trailingUnit = unitAt(last)
            if (trailingUnit != null && tokens.size > 2 && numberAt(last - 1) != null) {
                amount = numberAt(last - 1)
                unit = trailingUnit
                tokens.removeAt(last)
                tokens.removeAt(last - 1)
            } else if (numberAt(last) != null) {
                amount = numberAt(last)
                tokens.removeAt(last)
            }
        }

        // Inline multiplier anywhere: "קוטג x2 5%"
        if (amount == null) {
            val idx = tokens.indexOfFirst { it.startsWith("x") || it.startsWith("X") || it.startsWith("×") }
            if (idx >= 0 && tokens.size > 1) {
                numberAt(idx)?.let { amount = it; tokens.removeAt(idx) }
            }
        }

        val nameText = tokens.joinToString(" ")
        if (nameText.isBlank()) return null
        val identity = ItemIdentity.fromText(nameText)
        val quantity = amount?.takeIf { it > 0 }?.let { Quantity(it, unit ?: QuantityUnit.PIECE) }
        return ParsedEntry(identity, quantity, input, nameText)
    }
}
