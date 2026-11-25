package app.necessityq.domain

/** What a group of features is for, in the order the settings show them ("מה האפליקציה עושה"). */
enum class FeatureGroup(val label: String, val hint: String) {
    LEARNING("למידה והצעות", "האפליקציה לומדת מה הבית קונה ובאיזה קצב, ומזכירה לפני שמשהו נגמר."),
    SAVING("חיסכון ומחירים", "כמה הסל עולה, מה במבצע, ואיפה זול יותר."),
    MEALS("ארוחות ומתכונים", "תפריט שבועי ומתכונים שממלאים את הרשימה בעצמם."),
    SHARING("שיתוף עם בני הבית", "התייעצות על מוצר עם מי שבבית או בחנות."),
    ADVANCED("למתקדמים", "פרטים נוספים על כל פריט ברשימה."),
}

/** A part of the app that can be turned off ("הגדרות" ← "מה האפליקציה עושה") and on again at any time. */
enum class Feature(val label: String, val description: String, val group: FeatureGroup) {
    MENU_TAB(
        "לשונית \"תפריט\"",
        "תפריט שבועי לבני המשפחה, ומתכונים שהמרכיבים שלהם נכנסים לרשימה בנגיעה.",
        FeatureGroup.MEALS,
    ),
    PRICES_TAB(
        "לשונית \"מחירים\"",
        "בחירת הסניפים שקונים בהם, וחיפוש מחיר של כל מוצר בכל סניף.",
        FeatureGroup.SAVING,
    ),
    CHAT(
        "צ'אט פוד",
        "שיחה על מוצר מסוים עם בני הבית: \"אין קוטג' 5%, לקחת 3%?\" — עם תמונה, הקלטה ותיוג ב-@.",
        FeatureGroup.SHARING,
    ),
    RUNNING_LOW(
        "\"כנראה נגמר בקרוב\"",
        "הצעות בראש הרשימה למה שעומד להיגמר, לפי הקצב שבו הבית קונה אותו (\"בדרך כלל כל 5 ימים\").",
        FeatureGroup.LEARNING,
    ),
    FREQUENT(
        "\"קונים בדרך כלל\"",
        "כפתורי הוספה מהירה למוצרים שהבית קונה הכי הרבה, בלי להקליד.",
        FeatureGroup.LEARNING,
    ),
    TRIP_PLAN(
        "תכנון \"איפה הכי משתלם לקנות\"",
        "לפני היציאה: השוואת הרשימה כולה בין הסניפים שבמעקב (צריך לעקוב אחרי שני סניפים לפחות).",
        FeatureGroup.SAVING,
    ),
    PRICES(
        "מחירים על הפריטים וסכום משוער",
        "המחיר של כל פריט בסניף שלכם, והסכום המשוער של כל הרשימה בתחתית.",
        FeatureGroup.SAVING,
    ),
    PROMOS(
        "מבצעים",
        "מבצעים על המוצרים שברשימה: על כל פריט עצמו, ובכרטיס מסכם בראש הרשימה.",
        FeatureGroup.SAVING,
    ),
    RECIPES_ON_LIST(
        "\"מתכונים ברשימה\"",
        "כרטיס בראש הרשימה עם המתכונים שהמרכיבים שלהם ברשימה: שינוי מנות מעדכן את הכמויות.",
        FeatureGroup.MEALS,
    ),
    TILE_EXTRAS(
        "ידית גרירה וסטטיסטיקה על הפריט",
        "ידית להזזת פריט ברשימה, ומתי נקנה לאחרונה ובאיזו תדירות.",
        FeatureGroup.ADVANCED,
    ),
}

/**
 * The features that are on. The first time the app opens it asks a few questions about the household's habits
 * ([FirstRunAnswers]); the settings also offer three ready choices: [SIMPLE], [RECOMMENDED] and [ALL].
 * Stored as the features turned off, so a feature added in a later version starts on.
 *
 * Turning a feature off only hides it: every purchase is still recorded, so the suggestions are ready (with all the
 * history) the moment it is turned on again.
 */
data class Features(val on: Set<Feature>) {
    operator fun contains(feature: Feature): Boolean = feature in on

    fun with(feature: Feature, enabled: Boolean): Features = Features(if (enabled) on + feature else on - feature)

    /** What a tile may show with these features: no price, chat, drag handle or statistics when they are off. */
    fun tile(layout: TileLayout): TileLayout = TileLayout(layout.shown - hiddenElements())

    /** The parts of a tile that a feature that is off keeps off the list (shown greyed in the tile editor). */
    fun hiddenElements(): Set<TileElement> = TileElement.entries.filter { featureFor(it)?.let { f -> f !in on } == true }.toSet()

    /** The names of the features that are off, separated by commas. */
    fun encodeOff(): String = Feature.entries.filter { it !in on }.joinToString(",") { it.name }

    companion object {
        val ALL = Features(Feature.entries.toSet())

        /**
         * "רשימה פשוטה": the list and the household — and still the one thing that makes it more than a note:
         * "כנראה נגמר בקרוב".
         */
        val SIMPLE = Features(setOf(Feature.RUNNING_LOW))

        /** "מומלץ": what helps almost every household — the suggestions and the prices — without the menu, chat or extras. */
        val RECOMMENDED = Features(setOf(Feature.RUNNING_LOW, Feature.FREQUENT, Feature.PRICES, Feature.PROMOS))

        /** The feature that, when off, keeps [element] off the tile (null: the element is always available). */
        fun featureFor(element: TileElement): Feature? = when (element) {
            TileElement.PRICE -> Feature.PRICES
            TileElement.CHAT -> Feature.CHAT
            TileElement.DRAG, TileElement.LAST_BOUGHT, TileElement.FREQUENCY -> Feature.TILE_EXTRAS
            else -> null
        }

        /** From [encodeOff]; unknown names (from a newer version) are ignored. */
        fun decodeOff(text: String?): Features {
            val off = text.orEmpty().split(',').mapNotNull { n -> Feature.entries.firstOrNull { it.name == n.trim() } }.toSet()
            return Features(Feature.entries.toSet() - off)
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
        features = setOf(Feature.PRICES_TAB, Feature.PRICES, Feature.PROMOS, Feature.TRIP_PLAN),
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
