package app.necessityq.domain

/** What a group of features is for, in the order the settings show them ("מה האפליקציה עושה"). */
enum class FeatureGroup(val label: String, val hint: String) {
    LEARNING("למידה והצעות", "האפליקציה לומדת מה הבית קונה ובאיזה קצב, ומזכירה לפני שמשהו נגמר."),
    SAVING("חיסכון ומחירים", "כמה הסל עולה, מה במבצע, ואיפה זול יותר."),
    MEALS("ארוחות ומתכונים", "תפריט שבועי ומתכונים שממלאים את הרשימה בעצמם."),
    SHARING("שיתוף עם בני הבית", "התייעצות על מוצר עם מי שבבית או בחנות."),
    ADVANCED("למתקדמים", "פרטים נוספים על כל פריט ברשימה."),
}

/**
 * Where a feature works: in the whole app (a tab, the chat), only while making the list, only in the shop, or in
 * both stages of the list — each stage then has its own switch (a feature can be on for the list and off in the
 * shop, or the other way round).
 */
enum class FeatureScope { APP, PLAN, SHOP, BOTH }

/**
 * A part of the app that can be turned off ("הגדרות" ← "יכולות") and on again at any time. [description] is what the
 * "!" beside its name explains: everything the feature adds.
 */
enum class Feature(val label: String, val description: String, val scope: FeatureScope, val group: FeatureGroup) {
    MENU_TAB(
        "לשונית \"תפריט\"",
        "לשונית בסרגל התחתון לתכנון הארוחות: בני המשפחה ומה כל אחד אוהב, מתכונים (גם מקישור), ותפריט שבועי " +
            "שממלא את רשימת הקניות במצרכים שחסרים.",
        FeatureScope.APP,
        FeatureGroup.MEALS,
    ),
    PRICES_TAB(
        "לשונית \"מחירים\"",
        "לשונית בסרגל התחתון לבחירת הסניפים שהבית קונה בהם (לפי מיקום או חיפוש), חיפוש מוצר והשוואת המחיר שלו " +
            "בין הסניפים, והוספה לרשימה מתוצאת החיפוש.",
        FeatureScope.APP,
        FeatureGroup.SAVING,
    ),
    CHAT(
        "צ'אט פוד",
        "שיחה על כל מוצר עם בני הבית: הודעות, תמונות והקלטות, תיוג ב-@ וציטוט, וסימן שיחה על אריח המוצר. " +
            "גם עמוד \"צ'אט פוד\" בלשונית הבית עם כל השיחות.",
        FeatureScope.APP,
        FeatureGroup.SHARING,
    ),
    RUNNING_LOW(
        "\"כנראה נגמר בקרוב\"",
        "בראש הרשימה: מוצרים שלפי קצב הקנייה של הבית כנראה עומדים להיגמר, עם הוספה בנגיעה או דחייה.",
        FeatureScope.PLAN,
        FeatureGroup.LEARNING,
    ),
    FREQUENT(
        "\"קונים בדרך כלל\"",
        "בראש הרשימה: המוצרים שהבית קונה הכי הרבה, בשתי שורות שנגללות הצידה, להוספה מהירה בנגיעה.",
        FeatureScope.PLAN,
        FeatureGroup.LEARNING,
    ),
    TRIP_PLAN(
        "תכנון \"איפה הכי משתלם לקנות\"",
        "לפני שיוצאים לקנות (כשיש שני סניפים במעקב ומעלה): בדיקה באיזה סופר הרשימה הכי זולה, האם כדאי לעבור או " +
            "לפצל בין כמה סופרים, מאיזה סכום חיסכון, ומבצעי מועדון וקופונים.",
        FeatureScope.PLAN,
        FeatureGroup.SAVING,
    ),
    PRICES(
        "מחירים על האריחים וסכום משוער",
        "המחיר של כל מוצר על האריח שלו (מהסניף הראשי, או טווח המחירים של המוצרים המתאימים), והסכום המשוער של " +
            "הרשימה: בתחתית הרשימה, ובקנייה — כמה נשאר לשלם.",
        FeatureScope.BOTH,
        FeatureGroup.SAVING,
    ),
    PROMOS(
        "מבצעים",
        "על כל מוצר ברשימה שיש עליו מבצע: סמל מתנה בפינת האריח ופס מתחתיו (מה המבצע, המחיר איתו ואיפה). בדף " +
            "המוצר: איפה המבצע, כמה הוא חוסך ומה התנאים (מועדון, קופון, עד גמר המלאי).",
        FeatureScope.BOTH,
        FeatureGroup.SAVING,
    ),
    BRANCH_COMPARE(
        "השוואה בין הסניפים שלך",
        "כשמוצר יקר או זול באופן בולט בסניף הראשי לעומת שאר הסניפים שבמעקב: סימן ▲/▼ ליד המחיר על האריח, ובדף " +
            "המוצר אריח \"השוואה לסניפים שלך\" עם המחיר בכל סניף והזול מסומן. אפשר לבחור מאיזה הפרש בשקלים להציג.",
        FeatureScope.BOTH,
        FeatureGroup.SAVING,
    ),
    RECIPES_ON_LIST(
        "\"מתכונים ברשימה\"",
        "בראש הרשימה: המתכונים שהמצרכים שלהם ברשימה, עם שינוי מספר המנות (והכמויות ברשימה) או הסרת המתכון.",
        FeatureScope.PLAN,
        FeatureGroup.MEALS,
    ),
    TILE_EXTRAS(
        "ידית גרירה וסטטיסטיקה על האריח",
        "אפשרות להציג על האריח ידית גרירה לסידור הרשימה ביד, ומתי המוצר נקנה לאחרונה ובאיזו תדירות.",
        FeatureScope.BOTH,
        FeatureGroup.ADVANCED,
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
 * in neither. The first time the app opens it asks a few questions about the household's habits ([FirstRunAnswers]);
 * the settings also offer three ready choices: [SIMPLE], [RECOMMENDED] and [ALL]. Stored as the features turned off,
 * so a feature added in a later version starts on.
 *
 * Turning a feature off only hides it: every purchase is still recorded, so the suggestions are ready (with all the
 * history) the moment it is turned on again.
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

        /**
         * "רשימה פשוטה": the list and the household — and still the one thing that makes it more than a note:
         * "כנראה נגמר בקרוב".
         */
        val SIMPLE = Features(setOf(Feature.RUNNING_LOW))

        /** "מומלץ": what helps almost every household — the suggestions, prices and promotions — without the menu, chat or extras. */
        val RECOMMENDED = Features(setOf(Feature.RUNNING_LOW, Feature.FREQUENT, Feature.PRICES, Feature.PROMOS))

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

/** One first-run question: about the household's habits, not about feature names. */
enum class FirstRunQuestion(
    val question: String,
    /** What answering "yes" gives. */
    val gain: String,
    /** What answering "no" gives up — so the choice is an informed one. */
    val loss: String,
    /** Whether this one is recommended for almost everyone. */
    val recommended: Boolean,
    val features: Set<Feature>,
) {
    REMIND(
        "רוצים שהאפליקציה תזכיר מה עומד להיגמר?",
        "האפליקציה לומדת כל כמה זמן אתם קונים כל מוצר, ומציעה אותו לפני שהוא נגמר. זה הלב של האפליקציה.",
        "תצטרכו לזכור לבד מתי נגמר החלב.",
        recommended = true,
        features = setOf(Feature.RUNNING_LOW, Feature.FREQUENT),
    ),
    SAVE(
        "חשוב לכם לחסוך ולהשוות מחירים?",
        "המחיר של כל פריט בסניף שלכם, הסכום המשוער של הסל, מבצעים, ואיפה הכי זול לקנות את כל הרשימה.",
        "לא תראו כמה הסל עולה ואיפה זול יותר.",
        recommended = false,
        features = setOf(Feature.PRICES_TAB, Feature.PRICES, Feature.PROMOS, Feature.TRIP_PLAN, Feature.BRANCH_COMPARE),
    ),
    MEALS(
        "מתכננים ארוחות או מבשלים לפי מתכונים?",
        "תפריט שבועי לבני המשפחה, ומתכונים שהמרכיבים שלהם נכנסים לרשימה בכמות הנכונה.",
        "את המרכיבים של כל מתכון תוסיפו לרשימה ביד.",
        recommended = false,
        features = setOf(Feature.MENU_TAB, Feature.RECIPES_ON_LIST),
    ),
    SHARED(
        "קונים יחד עם עוד בני בית?",
        "הרשימה מתעדכנת אצל כולם בזמן אמת, ואפשר להתייעץ על מוצר מתוך החנות. נציע להזמין אותם מיד.",
        "לא תוכלו להתייעץ על מוצר מתוך הרשימה (הרשימה עדיין משותפת).",
        recommended = false,
        features = setOf(Feature.CHAT),
    ),
}

/** The answers to the first-run questions: the features they turn on. */
data class FirstRunAnswers(val yes: Set<FirstRunQuestion>) {
    fun features(): Features = Features(yes.flatMap { it.features }.toSet())

    companion object {
        /** Where the questions start: the recommended ones answered "yes". */
        val DEFAULT = FirstRunAnswers(FirstRunQuestion.entries.filter { it.recommended }.toSet())
    }
}
