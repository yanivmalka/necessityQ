package app.necessityq.domain

/** What pulling a product tile to one side does ("הגדרות" ← "הרשימה" ← "החלקה על פריט"). */
enum class SwipeChoice(val label: String) {
    NONE("כלום"),
    BOUGHT("נקנה"),
    MISSING("חסר"),
    REMOVE("הסרה"),
    CHAT("צ'אט פוד"),
    ;

    companion object {
        /**
         * The choices of [mode]. Making the list is where the household decides what is on it, and talks about it:
         * remove, or צ'אט פוד (nothing is bought or missing before the shop). In the shop an item is not deleted but
         * marked: bought, missing, or צ'אט פוד.
         */
        fun of(mode: ListMode): List<SwipeChoice> =
            if (mode == ListMode.SHOP) listOf(NONE, BOUGHT, MISSING, CHAT) else listOf(NONE, REMOVE, CHAT)

        fun decode(name: String?, fallback: SwipeChoice): SwipeChoice = entries.firstOrNull { it.name == name } ?: fallback
    }
}

/** The actions of a swipe to the left and to the right (the phone's sides, also in Hebrew), for one stage of the list. */
data class SwipeActions(val left: SwipeChoice, val right: SwipeChoice) {
    /** Only the choices [mode] allows; any other is nothing. */
    fun allowedIn(mode: ListMode): SwipeActions {
        val ok = SwipeChoice.of(mode)
        return SwipeActions(left.takeIf { it in ok } ?: SwipeChoice.NONE, right.takeIf { it in ok } ?: SwipeChoice.NONE)
    }

    /** "משכו שמאלה: נקנה · ימינה: חסר"; null when a swipe does nothing. */
    val hint: String?
        get() = listOfNotNull(
            left.takeIf { it != SwipeChoice.NONE }?.let { "משכו שמאלה: ${it.label}" },
            right.takeIf { it != SwipeChoice.NONE }?.let { "ימינה: ${it.label}" },
        ).joinToString(" · ").ifBlank { null }?.let { if (left == SwipeChoice.NONE) it.replaceFirst("ימינה", "משכו ימינה") else it }

    companion object {
        /** As before the choice existed: in the shop left = bought, right = missing; while making the list right removes. */
        fun defaultFor(mode: ListMode): SwipeActions =
            if (mode == ListMode.SHOP) SwipeActions(SwipeChoice.BOUGHT, SwipeChoice.MISSING) else SwipeActions(SwipeChoice.NONE, SwipeChoice.REMOVE)
    }
}
