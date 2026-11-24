package app.necessityq.domain

/** Text helpers for Hebrew product names typed quickly on a phone keyboard. */
object HebrewText {
    private val niqqud = Regex("[\\u0591-\\u05C7&&[^\\u05BE\\u05F3\\u05F4]]")
    private val whitespace = Regex("\\s+")

    /**
     * Product names inside a sentence, so a message says which items it is about:
     * "חלב", "חלב וקוטג'", "חלב, קוטג' ולחם", and past [max] (+1) "חלב, קוטג', לחם ועוד 4".
     */
    fun nameList(names: List<String>, max: Int = 3): String = when {
        names.isEmpty() -> ""
        names.size == 1 -> names[0]
        names.size <= max + 1 -> names.dropLast(1).joinToString(", ") + " ו" + names.last()
        else -> names.take(max).joinToString(", ") + " ועוד ${names.size - max}"
    }

    /** Unifies geresh/gershayim look-alikes, strips niqqud, collapses whitespace. */
    fun clean(input: String): String = input
        .replace(niqqud, "")
        .replace('׳', '\'') // ׳ Hebrew geresh
        .replace('’', '\'') // ’
        .replace('‘', '\'') // ‘
        .replace('`', '\'')
        .replace('´', '\'') // ´
        .replace('״', '"') // ״ Hebrew gershayim
        .replace('“', '"') // “
        .replace('”', '"') // ”
        .replace(whitespace, " ")
        .trim()

    /**
     * Comparison key: ignores apostrophes, quotes, hyphens, case and spacing,
     * so "קוטג'", "קוטג" and "קוטג׳" all compare equal.
     */
    fun matchKey(input: String): String = clean(input)
        .lowercase()
        .replace("'", "")
        .replace("\"", "")
        .replace("-", " ")
        .replace(whitespace, " ")
        .trim()
}
