package app.necessityq.domain

/** Text helpers for Hebrew product names typed quickly on a phone keyboard. */
object HebrewText {
    private val niqqud = Regex("[\\u0591-\\u05C7&&[^\\u05BE\\u05F3\\u05F4]]")
    private val whitespace = Regex("\\s+")

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
