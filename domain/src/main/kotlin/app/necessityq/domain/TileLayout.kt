package app.necessityq.domain

/** A part of a product tile on the list that can be shown or hidden. */
enum class TileElement(val label: String) {
    CHECK("עיגול סימון"),
    DRAG("ידית גרירה"),
    QUANTITY("כמות"),
    STEPPER("כפתורי כמות"),
    WEIGHT("משקל מוערך"),
    PRICE("מחיר"),
    ALERTS("חסר / כמות חריגה"),
    KIND("קבוע / לפעמים / מיוחד"),
    SOURCE("מתכון / תפריט / אוטומטי"),
    FAVORITE("מועדף"),
    CHAT("שיחה"),
    NOTE("הערה"),
    LAST_BOUGHT("נקנה לאחרונה"),
    FREQUENCY("תדירות"),
    ADDED_BY("מי הוסיף"),
    ;

    companion object {
        /** Which tile part a badge belongs to. */
        fun forTag(kind: TagKind): TileElement = when (kind) {
            TagKind.MISSING, TagKind.LARGE_AMOUNT -> ALERTS
            TagKind.ROUTINE, TagKind.OCCASIONAL, TagKind.SPECIAL -> KIND
            TagKind.RECIPE, TagKind.AUTO -> SOURCE
            TagKind.FAVORITE -> FAVORITE
        }
    }
}

/**
 * What a product tile shows. Each stage of use has its own layout: while making the list the statistics
 * help decide, in the shop they are mostly noise.
 */
data class TileLayout(val shown: Set<TileElement>) {
    operator fun contains(element: TileElement): Boolean = element in shown

    fun toggle(element: TileElement): TileLayout = TileLayout(if (element in shown) shown - element else shown + element)

    fun showsTag(kind: TagKind): Boolean = TileElement.forTag(kind) in shown

    /** Stored as the element names separated by commas (an empty string is a tile with nothing optional). */
    fun encode(): String = shown.sortedBy { it.ordinal }.joinToString(",") { it.name }

    companion object {
        val ALL = TileLayout(TileElement.entries.toSet())

        fun defaultFor(mode: ListMode): TileLayout = when (mode) {
            ListMode.PLAN -> TileLayout(ALL.shown - TileElement.ADDED_BY)
            ListMode.SHOP -> TileLayout(ALL.shown - TileElement.LAST_BOUGHT - TileElement.FREQUENCY - TileElement.ADDED_BY)
        }

        /** Null when nothing was stored; unknown names (from a newer version) are ignored. */
        fun decode(text: String?): TileLayout? {
            if (text == null) return null
            val names = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            return TileLayout(names.mapNotNull { n -> TileElement.entries.firstOrNull { it.name == n } }.toSet())
        }
    }
}
