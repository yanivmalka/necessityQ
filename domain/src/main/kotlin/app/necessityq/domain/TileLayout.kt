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

    /** The feature ("הגדרות" ← "פיצ'רים") this part needs; null when it needs none. */
    val feature: Feature?
        get() = when (this) {
            PRICE -> Feature.PRICES
            CHAT -> Feature.CHAT
            DRAG, LAST_BOUGHT, FREQUENCY -> Feature.TILE_EXTRAS
            else -> null
        }

    /** The check circle marks an item bought, which is done only in the shop; every other part is in both stages. */
    fun availableIn(mode: ListMode): Boolean = this != CHECK || mode == ListMode.SHOP

    companion object {
        /** The parts a tile of [mode] can have. */
        fun of(mode: ListMode): List<TileElement> = entries.filter { it.availableIn(mode) }

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

        /**
         * A simple tile: the check circle, the name, the amount and the price — and the sign of a conversation, so a
         * message on a product is seen at once (it shows only while צ'אט פוד is on). Everything else is in "תצוגה".
         */
        val SIMPLE = TileLayout(setOf(TileElement.CHECK, TileElement.QUANTITY, TileElement.PRICE, TileElement.CHAT))

        /**
         * Both stages start from the simple tile (without the check circle while making the list, where nothing is
         * marked bought); each can then be changed on its own.
         */
        fun defaultFor(mode: ListMode): TileLayout = TileLayout(SIMPLE.shown.filter { it.availableIn(mode) }.toSet())

        /** Null when nothing was stored; unknown names (from a newer version) are ignored. */
        fun decode(text: String?): TileLayout? {
            if (text == null) return null
            val names = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            return TileLayout(names.mapNotNull { n -> TileElement.entries.firstOrNull { it.name == n } }.toSet())
        }
    }
}
