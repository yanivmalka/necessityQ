package app.necessityq.domain

/**
 * Joining a household by link. The shared text carries a web link (it is clickable in every messaging app); the
 * page it opens hands the code to the app (`necessityq://join?code=…`), where one tap joins. The 6-character
 * code stays in the text as a fallback.
 */
object JoinLinks {
    /** The web page that opens the app (served from the repository's `docs/` folder by GitHub Pages). */
    const val PAGE = "https://yanivmalka.github.io/necessityQ/join.html"
    const val SCHEME = "necessityq"
    const val HOST = "join"

    private val codeShape = Regex("^[A-Z0-9]{4,12}$")

    fun link(code: String): String = "$PAGE?code=${code.trim().uppercase()}"

    /** The invitation text: the link, and the code for typing by hand. */
    fun shareText(householdName: String, code: String): String =
        "הצטרפו לרשימת הקניות של \"$householdName\" ב-necessityQ: ${link(code)}\n" +
            "או בהקלדת הקוד באפליקציה: ${code.trim().uppercase()}"

    /**
     * The join code in a link that opened the app — `necessityq://join?code=…` or the web page's own address —
     * or null when it is not a join link (or the code is not one).
     */
    fun codeFrom(uri: String?): String? {
        val text = uri?.trim() ?: return null
        val isJoin = text.startsWith("$SCHEME://$HOST", ignoreCase = true) || text.startsWith(PAGE, ignoreCase = true)
        if (!isJoin) return null
        val query = text.substringAfter('?', "").substringBefore('#')
        val code = query.split('&').firstOrNull { it.startsWith("code=", ignoreCase = true) }?.substringAfter('=')
            ?.trim()?.uppercase() ?: return null
        return code.takeIf { codeShape.matches(it) }
    }
}
