package app.necessityq.domain

/**
 * What "סיום קנייה" says in one line: what was bought, the missing items that stay on the list,
 * and what was not bought ("נקנו 12 · 2 חסרים נשארים ברשימה · 3 לא נקנו").
 */
data class TripSummary(val bought: Int, val missing: Int, val notBought: Int) {
    fun text(): String {
        val parts = listOfNotNull(
            when (bought) { 0 -> null; 1 -> "נקנה פריט אחד"; else -> "נקנו $bought" },
            when (missing) { 0 -> null; 1 -> "חסר אחד נשאר ברשימה"; else -> "$missing חסרים נשארים ברשימה" },
            when (notBought) { 0 -> null; 1 -> "אחד לא נקנה"; else -> "$notBought לא נקנו" },
        )
        return if (parts.isEmpty()) "הקנייה הסתיימה" else parts.joinToString(" · ")
    }

    companion object {
        /** From the list as it is at the end of the trip: bought lines, and open lines with or without "חסר". */
        fun of(lines: Collection<ListLine>): TripSummary {
            val open = lines.filter { it.status == LineStatus.NEEDED }
            val missing = open.count { it.missing }
            return TripSummary(
                bought = lines.count { it.status == LineStatus.BOUGHT },
                missing = missing,
                notBought = open.size - missing,
            )
        }
    }
}
