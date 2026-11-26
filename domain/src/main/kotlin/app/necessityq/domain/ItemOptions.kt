package app.necessityq.domain

/**
 * The kinds of an item a household chooses between when a recipe only says "קמח" or "חלב": the first time a recipe
 * goes on the list the app asks which one, and remembers it for that recipe ([ListMemory.variants]).
 * A chosen kind becomes the item's variant, so the list says "קמח תופח", "אורז בסמטי", "חלב 3%".
 */
object ItemOptions {
    /** Keyed by the catalogue's name. */
    private val kinds: Map<String, List<String>> = listOf(
        "קמח" to listOf("לבן", "תופח", "מלא", "כוסמין"),
        "סוכר" to listOf("לבן", "חום"),
        "אורז" to listOf("לבן", "בסמטי", "יסמין", "מלא"),
        "פסטה" to listOf("ספגטי", "פנה", "פוסילי", "מלאה"),
        "חלב" to listOf("3%", "1%", "ללא לקטוז"),
        "קוטג'" to listOf("5%", "3%", "9%"),
        "יוגורט" to listOf("טבעי", "3%", "1.5%"),
        "גבינה לבנה" to listOf("5%", "9%", "3%", "0%"),
        "שמנת מתוקה" to listOf("38%", "32%"),
        "שמנת חמוצה" to listOf("15%", "9%"),
        "גבינה צהובה" to listOf("פרוסות", "מגורדת", "חתיכה"),
        "ביצים" to listOf("L", "M", "חופש"),
        "שמן" to listOf("קנולה", "חמניות", "תירס"),
        "שמן זית" to listOf("כתית מעולה", "רגיל"),
        "טחינה" to listOf("גולמית", "מלאה"),
        "עדשים" to listOf("ירוקות", "שחורות", "כתומות", "חומות"),
        "קוסקוס" to listOf("רגיל", "מלא"),
        "שיבולת שועל" to listOf("רגילה", "מהירה"),
        "בצל" to listOf("יבש", "אדום", "ירוק"),
        "עגבניות" to listOf("רגילות", "שרי"),
        "תפוחי אדמה" to listOf("לבנים", "אדומים"),
        "פלפל" to listOf("אדום", "צהוב", "ירוק", "חריף"),
        "בשר טחון" to listOf("בקר", "עוף", "הודו"),
        "שניצל" to listOf("עוף", "הודו"),
        "טונה" to listOf("בשמן", "במים"),
    ).associate { (name, list) -> HebrewText.matchKey(name) to list }

    /** The kinds to choose from for an ingredient, or none when the recipe line already says (a variant) or there is no choice. */
    fun kindsFor(item: ItemIdentity): List<String> =
        if (item.variant != null) emptyList() else kinds[HebrewText.matchKey(item.name)].orEmpty()

    /** Catalogue names that have kinds. */
    internal val names: Set<String> get() = kinds.keys
}
