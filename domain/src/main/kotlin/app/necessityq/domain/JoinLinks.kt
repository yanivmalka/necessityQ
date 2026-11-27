package app.necessityq.domain

/**
 * Joining a household is only by a one-time link. The shared text carries a web link (clickable in every messaging
 * app); the page it opens hands the invitation to the app (`necessityq://join?invite=…`), where one tap joins.
 * The invitation is used up by the first person who joins with it, and expires after [VALID_DAYS] days.
 */
object JoinLinks {
    /** The web page that opens the app (served from the repository's `docs/` folder by GitHub Pages). */
    const val PAGE = "https://yanivmalka.github.io/necessityQ/join.html"
    const val SCHEME = "necessityq"
    const val HOST = "join"
    const val PARAM = "invite"
    const val VALID_DAYS = 7

    private val tokenShape = Regex("^[A-Za-z0-9]{16,64}$")

    fun isToken(text: String): Boolean = tokenShape.matches(text)

    fun link(token: String): String = "$PAGE?$PARAM=${token.trim()}"

    /** The invitation text: only the link. */
    fun shareText(householdName: String, token: String): String =
        "הצטרפו לרשימת הקניות של \"$householdName\" ב-necessityQ: ${link(token)}\n" +
            "הקישור חד-פעמי, ותקף ל-$VALID_DAYS ימים."

    /**
     * The invitation token in a link that opened the app — `necessityq://join?invite=…` or the web page's own
     * address — or null when it is not a join link (or the token is not one).
     */
    fun tokenFrom(uri: String?): String? {
        val text = uri?.trim() ?: return null
        val isJoin = text.startsWith("$SCHEME://$HOST", ignoreCase = true) || text.startsWith(PAGE, ignoreCase = true)
        if (!isJoin) return null
        val query = text.substringAfter('?', "").substringBefore('#')
        val token = query.split('&').firstOrNull { it.startsWith("$PARAM=", ignoreCase = true) }?.substringAfter('=')
            ?.trim() ?: return null
        return token.takeIf { isToken(it) }
    }
}
