package app.necessityq.domain

enum class Category(val label: String, val order: Int) {
    PRODUCE("ירקות ופירות", 0),
    DAIRY("מוצרי חלב וביצים", 1),
    BAKERY("לחם ומאפים", 2),
    MEAT_FISH("בשר, עוף ודגים", 3),
    PANTRY("מזווה ובישול", 4),
    BREAKFAST_SNACKS("דגני בוקר וחטיפים", 5),
    DRINKS("משקאות", 6),
    FROZEN("קפואים", 7),
    CLEANING("ניקיון ובית", 8),
    TOILETRIES("טואלטיקה", 9),
    BABY("תינוקות", 10),
    OTHER("אחר", 11),
    ;

    /** Needs the fridge or freezer — bought last so it stays cold. */
    val isChilled: Boolean get() = this == DAIRY || this == MEAT_FISH || this == FROZEN
}

/** A brand-agnostic product the household names on its list: "קוטג'", "גבינה צהובה", "קורנפלקס". */
data class GenericItemDef(
    val name: String,
    val category: Category,
    val aliases: List<String> = emptyList(),
)

/**
 * Seed catalogue of generic items with Hebrew aliases and the brand words to ignore.
 * Unknown names are still accepted (category [Category.OTHER]) — the catalogue only
 * improves grouping and spelling consolidation.
 */
object Catalog {
    val items: List<GenericItemDef> = listOf(
        // ירקות ופירות
        GenericItemDef("עגבניות", Category.PRODUCE, listOf("עגבניה", "עגבנייה", "עגבניות שרי", "שרי")),
        GenericItemDef("מלפפונים", Category.PRODUCE, listOf("מלפפון")),
        GenericItemDef("בצל", Category.PRODUCE, listOf("בצלים", "בצל יבש")),
        GenericItemDef("שום", Category.PRODUCE),
        GenericItemDef("תפוחי אדמה", Category.PRODUCE, listOf("תפוח אדמה", "תפו\"א", "תפוא")),
        GenericItemDef("גזר", Category.PRODUCE),
        GenericItemDef("פלפל", Category.PRODUCE, listOf("פלפלים", "גמבה")),
        GenericItemDef("חסה", Category.PRODUCE),
        GenericItemDef("פטרוזיליה", Category.PRODUCE),
        GenericItemDef("כוסברה", Category.PRODUCE),
        GenericItemDef("לימון", Category.PRODUCE, listOf("לימונים")),
        GenericItemDef("בננות", Category.PRODUCE, listOf("בננה")),
        GenericItemDef("תפוחים", Category.PRODUCE, listOf("תפוח", "תפוחי עץ")),
        GenericItemDef("תפוזים", Category.PRODUCE, listOf("תפוז")),
        GenericItemDef("אבוקדו", Category.PRODUCE),
        GenericItemDef("קישואים", Category.PRODUCE, listOf("קישוא")),
        GenericItemDef("בטטה", Category.PRODUCE, listOf("בטטות")),
        // מוצרי חלב וביצים
        GenericItemDef("חלב", Category.DAIRY),
        GenericItemDef("קוטג'", Category.DAIRY, listOf("קוטג", "גבינת קוטג")),
        GenericItemDef("גבינה צהובה", Category.DAIRY, listOf("צהובה", "גבינה צהובה פרוסה")),
        GenericItemDef("גבינה לבנה", Category.DAIRY, listOf("לבנה")),
        GenericItemDef("גבינת שמנת", Category.DAIRY),
        GenericItemDef("גבינה בולגרית", Category.DAIRY, listOf("בולגרית")),
        GenericItemDef("מוצרלה", Category.DAIRY),
        GenericItemDef("יוגורט", Category.DAIRY, listOf("יוגורטים")),
        GenericItemDef("לבן", Category.DAIRY),
        GenericItemDef("שמנת מתוקה", Category.DAIRY),
        GenericItemDef("שמנת חמוצה", Category.DAIRY),
        GenericItemDef("חמאה", Category.DAIRY),
        GenericItemDef("ביצים", Category.DAIRY, listOf("ביצה", "תבנית ביצים")),
        GenericItemDef("מעדנים", Category.DAIRY, listOf("מעדן", "פודינג")),
        // לחם ומאפים
        GenericItemDef("לחם", Category.BAKERY, listOf("כיכר לחם")),
        GenericItemDef("לחם מלא", Category.BAKERY),
        GenericItemDef("פיתות", Category.BAKERY, listOf("פיתה")),
        GenericItemDef("לחמניות", Category.BAKERY, listOf("לחמניה", "לחמנייה")),
        GenericItemDef("חלות", Category.BAKERY, listOf("חלה")),
        GenericItemDef("טורטיות", Category.BAKERY, listOf("טורטיה", "טורטייה")),
        // בשר, עוף ודגים
        GenericItemDef("חזה עוף", Category.MEAT_FISH, listOf("חזה")),
        GenericItemDef("שניצל", Category.MEAT_FISH, listOf("שניצלים")),
        GenericItemDef("כרעיים", Category.MEAT_FISH, listOf("כרעי עוף")),
        GenericItemDef("בשר טחון", Category.MEAT_FISH, listOf("טחון")),
        GenericItemDef("נקניקיות", Category.MEAT_FISH, listOf("נקניקייה", "נקניקיה")),
        GenericItemDef("פסטרמה", Category.MEAT_FISH),
        GenericItemDef("סלמון", Category.MEAT_FISH),
        GenericItemDef("טונה", Category.MEAT_FISH, listOf("טונה בשימורים")),
        // מזווה ובישול
        GenericItemDef("אורז", Category.PANTRY),
        GenericItemDef("פסטה", Category.PANTRY, listOf("ספגטי", "פתיתים")),
        GenericItemDef("קמח", Category.PANTRY),
        GenericItemDef("סוכר", Category.PANTRY),
        GenericItemDef("מלח", Category.PANTRY),
        GenericItemDef("שמן", Category.PANTRY, listOf("שמן קנולה", "שמן חמניות")),
        GenericItemDef("שמן זית", Category.PANTRY),
        GenericItemDef("רסק עגבניות", Category.PANTRY, listOf("רסק")),
        GenericItemDef("טחינה", Category.PANTRY),
        GenericItemDef("חומוס", Category.PANTRY),
        GenericItemDef("קטשופ", Category.PANTRY),
        GenericItemDef("מיונז", Category.PANTRY),
        GenericItemDef("תבלינים", Category.PANTRY),
        GenericItemDef("קפה", Category.PANTRY, listOf("קפה נמס", "קפה שחור")),
        GenericItemDef("תה", Category.PANTRY),
        GenericItemDef("דבש", Category.PANTRY),
        GenericItemDef("ממרח שוקולד", Category.PANTRY, listOf("ממרח")),
        GenericItemDef("עדשים", Category.PANTRY),
        GenericItemDef("קוסקוס", Category.PANTRY),
        // דגני בוקר וחטיפים
        GenericItemDef("קורנפלקס", Category.BREAKFAST_SNACKS, listOf("קורן פלקס", "קורנפלקס דבש")),
        GenericItemDef("דגני בוקר", Category.BREAKFAST_SNACKS, listOf("דגנים")),
        GenericItemDef("גרנולה", Category.BREAKFAST_SNACKS),
        GenericItemDef("שיבולת שועל", Category.BREAKFAST_SNACKS, listOf("קוואקר")),
        GenericItemDef("עוגיות", Category.BREAKFAST_SNACKS, listOf("עוגיה")),
        GenericItemDef("ביסלי", Category.BREAKFAST_SNACKS),
        GenericItemDef("במבה", Category.BREAKFAST_SNACKS),
        GenericItemDef("קרקרים", Category.BREAKFAST_SNACKS, listOf("קרקר")),
        GenericItemDef("שוקולד", Category.BREAKFAST_SNACKS),
        GenericItemDef("חטיפי דגנים", Category.BREAKFAST_SNACKS, listOf("חטיף דגנים")),
        // משקאות
        GenericItemDef("מים מינרליים", Category.DRINKS, listOf("מים", "בקבוקי מים")),
        GenericItemDef("סודה", Category.DRINKS),
        GenericItemDef("מיץ", Category.DRINKS, listOf("מיץ תפוזים", "מיצים")),
        GenericItemDef("משקה קל", Category.DRINKS, listOf("קולה", "שתייה קלה")),
        GenericItemDef("מיץ ענבים", Category.DRINKS, listOf("יין", "תירוש")),
        // קפואים
        GenericItemDef("ירקות קפואים", Category.FROZEN, listOf("אפונה", "שעועית ירוקה")),
        GenericItemDef("גלידה", Category.FROZEN),
        GenericItemDef("בורקס", Category.FROZEN),
        // ניקיון ובית
        GenericItemDef("נייר טואלט", Category.CLEANING, listOf("טואלט")),
        GenericItemDef("מגבות נייר", Category.CLEANING, listOf("נייר מגבת")),
        GenericItemDef("סבון כלים", Category.CLEANING, listOf("נוזל כלים")),
        GenericItemDef("אבקת כביסה", Category.CLEANING, listOf("ג'ל כביסה", "נוזל כביסה")),
        GenericItemDef("מרכך כביסה", Category.CLEANING),
        GenericItemDef("שקיות אשפה", Category.CLEANING, listOf("שקיות זבל")),
        GenericItemDef("נייר כסף", Category.CLEANING, listOf("נייר אלומיניום")),
        GenericItemDef("חומר ניקוי", Category.CLEANING, listOf("אקונומיקה")),
        // טואלטיקה
        GenericItemDef("סבון ידיים", Category.TOILETRIES, listOf("סבון")),
        GenericItemDef("שמפו", Category.TOILETRIES),
        GenericItemDef("משחת שיניים", Category.TOILETRIES),
        GenericItemDef("מברשות שיניים", Category.TOILETRIES, listOf("מברשת שיניים")),
        GenericItemDef("דאודורנט", Category.TOILETRIES),
        // תינוקות
        GenericItemDef("חיתולים", Category.BABY),
        GenericItemDef("מגבונים", Category.BABY, listOf("מגבונים לחים")),
        GenericItemDef("תמ\"ל", Category.BABY, listOf("תמל", "תחליף חלב")),
    )

    /** Brand / manufacturer words dropped from names: the list tracks the product, not the company. */
    val brandWords: Set<String> = setOf(
        "תנובה", "שטראוס", "טרה", "יטבתה", "השחר", "עלית", "אסם", "נסטלה", "תלמה",
        "יוניליוור", "סוגת", "וילי", "זוגלובק", "אנג'ל", "ברמן", "דנונה", "יופלה", "מולר",
        "נביעות", "פריגת", "תפוגן", "סנו", "פינוק", "קלגייט", "פמפרס", "האגיס", "טיטול",
        "שופרסל", "ויקטורי", "יוחננוף", "כרמית", "נטורפוד", "קלוגס", "טעמן", "צבר",
        "שקדיה", "מילוטל", "סוויטנגו", "תפוגן", "מעיין", "נובה", "היימן",
    )

    /** Brand words that are also part of real generic names; kept when they form the whole name. */
    private val protectedNames: Set<String> = items.map { HebrewText.matchKey(it.name) }.toSet()

    private val byKey: Map<String, GenericItemDef> = buildMap {
        for (item in items) {
            put(HebrewText.matchKey(item.name), item)
            for (alias in item.aliases) putIfAbsent(HebrewText.matchKey(alias), item)
        }
    }

    private val brandKeys: Set<String> = brandWords.map { HebrewText.matchKey(it) }.toSet()

    fun lookup(name: String): GenericItemDef? = byKey[HebrewText.matchKey(name)]

    /**
     * Removes brand words, unless removing them would destroy a known generic name
     * (for example "עוף" in "חזה עוף" or "קולה" when the user typed only "קולה").
     */
    fun stripBrands(name: String): String {
        val cleaned = HebrewText.clean(name)
        if (lookup(cleaned) != null) return cleaned
        val tokens = cleaned.split(' ').filter { it.isNotBlank() }
        val kept = tokens.filterIndexed { index, token ->
            val key = HebrewText.matchKey(token)
            if (key !in brandKeys) return@filterIndexed true
            // keep the word when it belongs to a catalogue name together with a neighbour
            val prev = tokens.getOrNull(index - 1)
            val next = tokens.getOrNull(index + 1)
            (prev != null && HebrewText.matchKey("$prev $token") in protectedNames) ||
                (next != null && HebrewText.matchKey("$token $next") in protectedNames)
        }
        val result = kept.joinToString(" ")
        return result.ifBlank { cleaned }
    }

    /** Finds the longest catalogue name contained in [name] (e.g. "גבינה צהובה פרוסה דקה" → "גבינה צהובה"). */
    fun bestContainedMatch(name: String): GenericItemDef? {
        val tokens = HebrewText.matchKey(name).split(' ').filter { it.isNotBlank() }
        for (size in tokens.size downTo 1) {
            for (start in 0..tokens.size - size) {
                val candidate = tokens.subList(start, start + size).joinToString(" ")
                byKey[candidate]?.let { return it }
            }
        }
        return null
    }

    fun search(query: String, limit: Int = 8): List<GenericItemDef> {
        val q = HebrewText.matchKey(query)
        if (q.isBlank()) return emptyList()
        val starts = items.filter { item ->
            HebrewText.matchKey(item.name).startsWith(q) || item.aliases.any { HebrewText.matchKey(it).startsWith(q) }
        }
        val contains = items.filter { item ->
            item !in starts && (HebrewText.matchKey(item.name).contains(q) || item.aliases.any { HebrewText.matchKey(it).contains(q) })
        }
        return (starts + contains).take(limit)
    }
}
