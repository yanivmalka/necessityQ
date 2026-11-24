package app.necessityq.domain

/** A part of the app that can be turned off ("הגדרות" ← "פיצ'רים") and on again at any time. */
enum class Feature(val label: String) {
    MENU_TAB("לשונית \"תפריט\""),
    PRICES_TAB("לשונית \"מחירים\""),
    CHAT("צ'אט פוד"),
    RUNNING_LOW("\"כנראה נגמר בקרוב\""),
    FREQUENT("\"קונים בדרך כלל\""),
    TRIP_PLAN("תכנון \"איפה הכי משתלם לקנות\""),
    PRICES("מחירים על האריחים וסכום משוער"),
    PROMOS("כרטיס המבצעים"),
    RECIPES_ON_LIST("\"מתכונים ברשימה\""),
    TILE_EXTRAS("ידית גרירה וסטטיסטיקה על האריח"),
}

/**
 * The features that are on. The first time the app opens it asks one question — "רשימה פשוטה" (all off) or
 * "הכול" (all on). Stored as the features turned off, so a feature added in a later version starts on.
 */
data class Features(val on: Set<Feature>) {
    operator fun contains(feature: Feature): Boolean = feature in on

    fun with(feature: Feature, enabled: Boolean): Features = Features(if (enabled) on + feature else on - feature)

    /** What a tile may show with these features: no price, chat, drag handle or statistics when they are off. */
    fun tile(layout: TileLayout): TileLayout {
        var shown = layout.shown
        if (Feature.PRICES !in on) shown = shown - TileElement.PRICE
        if (Feature.CHAT !in on) shown = shown - TileElement.CHAT
        if (Feature.TILE_EXTRAS !in on) shown = shown - TileElement.DRAG - TileElement.LAST_BOUGHT - TileElement.FREQUENCY
        return TileLayout(shown)
    }

    /** The names of the features that are off, separated by commas. */
    fun encodeOff(): String = Feature.entries.filter { it !in on }.joinToString(",") { it.name }

    companion object {
        val ALL = Features(Feature.entries.toSet())
        val SIMPLE = Features(emptySet())

        /** From [encodeOff]; unknown names (from a newer version) are ignored. */
        fun decodeOff(text: String?): Features {
            val off = text.orEmpty().split(',').mapNotNull { n -> Feature.entries.firstOrNull { it.name == n.trim() } }.toSet()
            return Features(Feature.entries.toSet() - off)
        }
    }
}
