package app.necessityq.domain

/**
 * Where a feature works: in the whole app (a tab, the chat), only while making the list, only in the shop, or in
 * both stages of the list — each stage then has its own switch (a feature can be on for the list and off in the
 * shop, or the other way round).
 */
enum class FeatureScope { APP, PLAN, SHOP, BOTH }

/**
 * A part of the app that can be turned off ("הגדרות" ← "פיצ'רים") and on again at any time. [description] is what the
 * "!" beside its name explains: everything the feature adds.
 */
enum class Feature(val label: String, val description: String, val scope: FeatureScope) {
    MENU_TAB(
        "לשונית \"תפריט\"",
        "לשונית בסרגל התחתון לתכנון הארוחות: בני המשפחה ומה כל אחד אוהב, מתכונים (גם מקישור), ותפריט שבועי " +
            "שממלא את רשימת הקניות במצרכים שחסרים.",
        FeatureScope.APP,
    ),
    PRICES_TAB(
        "לשונית \"מחירים\"",
        "לשונית בסרגל התחתון לבחירת הסניפים שהבית קונה בהם (לפי מיקום או חיפוש), חיפוש מוצר והשוואת המחיר שלו " +
            "בין הסניפים, והוספה לרשימה מתוצאת החיפוש.",
        FeatureScope.APP,
    ),
    CHAT(
        "צ'אט פוד",
        "שיחה על כל מוצר עם בני הבית: הודעות, תמונות והקלטות, תיוג ב-@ וציטוט, וסימן שיחה על אריח המוצר. " +
            "גם עמוד \"צ'אט פוד\" בלשונית הבית עם כל השיחות.",
        FeatureScope.APP,
    ),
    RUNNING_LOW(
        "\"כנראה נגמר בקרוב\"",
        "בראש הרשימה: מוצרים שלפי קצב הקנייה של הבית כנראה עומדים להיגמר, עם הוספה בנגיעה או דחייה.",
        FeatureScope.PLAN,
    ),
    FREQUENT(
        "\"קונים בדרך כלל\"",
        "בראש הרשימה: המוצרים שהבית קונה הכי הרבה, בשתי שורות שנגללות הצידה, להוספה מהירה בנגיעה.",
        FeatureScope.PLAN,
    ),
    TRIP_PLAN(
        "תכנון \"איפה הכי משתלם לקנות\"",
        "לפני שיוצאים לקנות (כשיש שני סניפים במעקב ומעלה): בדיקה באיזה סופר הרשימה הכי זולה, האם כדאי לעבור או " +
            "לפצל בין כמה סופרים, מאיזה סכום חיסכון, ומבצעי מועדון וקופונים.",
        FeatureScope.PLAN,
    ),
    PRICES(
        "מחירים על האריחים וסכום משוער",
        "המחיר של כל מוצר על האריח שלו (מהסניף הראשי, או טווח המחירים של המוצרים המתאימים), והסכום המשוער של " +
            "הרשימה: בתחתית הרשימה, ובקנייה — כמה נשאר לשלם.",
        FeatureScope.BOTH,
    ),
    PROMOS(
        "סימון מבצעים",
        "סמל מתנה בפינת האריח של מוצר שיש עליו מבצע באחד הסניפים שבמעקב. בדף המוצר: איפה המבצע, כמה הוא חוסך " +
            "ומה התנאים (מועדון, קופון, עד גמר המלאי).",
        FeatureScope.BOTH,
    ),
    BRANCH_COMPARE(
        "השוואה בין הסניפים שלך",
        "כשמוצר יקר או זול באופן בולט בסניף הראשי לעומת שאר הסניפים שבמעקב: סימן ▲/▼ ליד המחיר על האריח, ובדף " +
            "המוצר אריח \"השוואה לסניפים שלך\" עם המחיר בכל סניף והזול מסומן. אפשר לבחור מאיזה הפרש בשקלים להציג.",
        FeatureScope.BOTH,
    ),
    RECIPES_ON_LIST(
        "\"מתכונים ברשימה\"",
        "בראש הרשימה: המתכונים שהמצרכים שלהם ברשימה, עם שינוי מספר המנות (והכמויות ברשימה) או הסרת המתכון.",
        FeatureScope.PLAN,
    ),
    TILE_EXTRAS(
        "ידית גרירה וסטטיסטיקה על האריח",
        "אפשרות להציג על האריח ידית גרירה לסידור הרשימה ביד, ומתי המוצר נקנה לאחרונה ובאיזו תדירות.",
        FeatureScope.BOTH,
    ),
    ;

    /** Whether this feature has a switch of its own for [mode] (app-wide features have one switch for both). */
    fun appliesTo(mode: ListMode): Boolean = when (scope) {
        FeatureScope.APP, FeatureScope.BOTH -> true
        FeatureScope.PLAN -> mode == ListMode.PLAN
        FeatureScope.SHOP -> mode == ListMode.SHOP
    }

    companion object {
        /** The features of the whole app (not of a stage of the list). */
        val appWide: List<Feature> get() = entries.filter { it.scope == FeatureScope.APP }

        /** The features that belong to [mode] of the list: its own and those of both stages. */
        fun of(mode: ListMode): List<Feature> = entries.filter { it.scope != FeatureScope.APP && it.appliesTo(mode) }
    }
}

/**
 * The features that are on, for making the list ([plan]) and in the shop ([shop]). An app-wide feature is in both or
 * in neither. The first time the app opens it asks one question — "רשימה פשוטה" (all off) or "הכול" (all on). Stored
 * as the features turned off, so a feature added in a later version starts on.
 */
data class Features(val plan: Set<Feature>, val shop: Set<Feature> = plan) {
    /** On anywhere: app-wide features at all, a feature of one stage in that stage, one of both stages in either. */
    operator fun contains(feature: Feature): Boolean = when (feature.scope) {
        FeatureScope.APP, FeatureScope.PLAN -> feature in plan
        FeatureScope.SHOP -> feature in shop
        FeatureScope.BOTH -> feature in plan || feature in shop
    }

    /** On for [mode] of the list (app-wide features: on at all). */
    fun isOn(feature: Feature, mode: ListMode): Boolean = when (feature.scope) {
        FeatureScope.APP -> feature in plan
        FeatureScope.PLAN -> mode == ListMode.PLAN && feature in plan
        FeatureScope.SHOP -> mode == ListMode.SHOP && feature in shop
        FeatureScope.BOTH -> feature in (if (mode == ListMode.SHOP) shop else plan)
    }

    /** Turned on or off everywhere it applies. */
    fun with(feature: Feature, enabled: Boolean): Features =
        Features(if (enabled) plan + feature else plan - feature, if (enabled) shop + feature else shop - feature)

    /** Turned on or off for [mode] only; an app-wide feature changes everywhere. */
    fun with(feature: Feature, mode: ListMode, enabled: Boolean): Features = when {
        feature.scope == FeatureScope.APP -> with(feature, enabled)
        mode == ListMode.SHOP -> copy(shop = if (enabled) shop + feature else shop - feature)
        else -> copy(plan = if (enabled) plan + feature else plan - feature)
    }

    /**
     * What a tile of [mode] may show with these features: no price, chat, drag handle or statistics when they are off;
     * no check circle while making the list (an item is marked bought only in the shop).
     */
    fun tile(layout: TileLayout, mode: ListMode): TileLayout {
        var shown = layout.shown.filter { it.availableIn(mode) }.toSet()
        TileElement.entries.forEach { e -> e.feature?.let { f -> if (!isOn(f, mode)) shown = shown - e } }
        return TileLayout(shown)
    }

    /** [layout] saved for [mode]: the features its parts need are turned on there (showing the price turns prices on). */
    fun allowing(layout: TileLayout, mode: ListMode): Features {
        var out = this
        layout.shown.filter { it.availableIn(mode) }.mapNotNull { it.feature }.distinct().forEach { f ->
            if (!out.isOn(f, mode)) out = out.with(f, mode, true)
        }
        return out
    }

    /** The names of the features that are off while making the list (and app-wide), separated by commas. */
    fun encodeOff(): String = Feature.entries.filter { it !in plan }.joinToString(",") { it.name }

    /** The names of the features that are off in the shop, separated by commas. */
    fun encodeShopOff(): String = Feature.entries.filter { it !in shop }.joinToString(",") { it.name }

    companion object {
        val ALL = Features(Feature.entries.toSet())
        val SIMPLE = Features(emptySet())

        private fun off(text: String?): Set<Feature> =
            text.orEmpty().split(',').mapNotNull { n -> Feature.entries.firstOrNull { it.name == n.trim() } }.toSet()

        /**
         * From [encodeOff] and [encodeShopOff]; unknown names (from a newer version) are ignored. Before the shop had
         * switches of its own ([shopText] null), it follows the list. App-wide features follow [text].
         */
        fun decodeOff(text: String?, shopText: String? = null): Features {
            val plan = Feature.entries.toSet() - off(text)
            val shop = if (shopText == null) plan else Feature.entries.toSet() - off(shopText)
            val app = Feature.appWide.toSet()
            return Features(plan, (shop - app) + (plan intersect app))
        }
    }
}
