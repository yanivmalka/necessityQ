package app.necessityq.domain

/**
 * What nearly every kitchen already has: a recipe that needs salt or a spoon of oil should not put them on the
 * shopping list. They are assumed to be at home ("יש בבית"); the household can say it needs one anyway
 * ([MealData.buy]), and marking one "יש בבית" never needs repeating.
 *
 *  - Always at home: water, salt, pepper, spices, baking powder, yeast, vanilla.
 *  - At home when a recipe needs only a little: sugar, flour, oil, vinegar, soy sauce. A small share of a pack
 *    ([SMALL_SHARE]) is a spoon or a cup; more than that is a baking day and is bought.
 */
object Staples {
    /** A recipe's need up to this share of the smallest pack is taken from what is at home. */
    const val SMALL_SHARE = 0.15

    /** Names that start with one of these are always at home. */
    private val alwaysStarts: List<String> = listOf(
        "מלח", "תבלין", "פפריקה", "כמון", "כורכום", "קינמון", "אורגנו", "קארי", "זעתר", "בהרט", "תימין", "אבקת אפיה", "אבקת אפייה",
        "סודה לשתיה", "סודה לשתייה", "שמרים", "וניל", "סוכר וניל", "אבקת שום", "אבקת בצל", "פלפל שחור", "פלפל לבן", "פלפל חריף יבש",
    ).map(HebrewText::matchKey)

    /** "פלפל" alone is the vegetable in the catalogue, so only these whole names are the spice or water. */
    private val alwaysExact: Set<String> = setOf("מים", "מלח ופלפל", "מלח ופלפל שחור").map(HebrewText::matchKey).toSet()

    /** Staples a recipe uses a little of, with the pack they come in when [PackSizes] has none. */
    private val littleOf: Map<String, Double> = mapOf(
        "סוכר" to 1000.0, "קמח" to 1000.0, "שמן" to 1000.0, "שמן זית" to 500.0, "חומץ" to 500.0, "רוטב סויה" to 250.0,
    ).mapKeys { HebrewText.matchKey(it.key) }

    fun assumedAtHome(item: ItemIdentity, quantity: Quantity?): Boolean {
        val key = HebrewText.matchKey(item.name)
        if (key in alwaysExact) return true
        if (alwaysStarts.any { key == it || key.startsWith("$it ") }) return true
        val pack = littleOf.entries.firstOrNull { (name, _) -> key == name || key.startsWith("$name ") } ?: return false
        quantity ?: return true // "שמן לטיגון": no amount means a little
        val packBase = PackSizes.smallestPack(item.name)?.let { if (it.dimension == quantity.dimension) it.baseAmount else null }
            ?: pack.value.takeIf { quantity.dimension != Dimension.COUNT }
            ?: return false
        return quantity.baseAmount <= packBase * SMALL_SHARE + 1e-9
    }
}
