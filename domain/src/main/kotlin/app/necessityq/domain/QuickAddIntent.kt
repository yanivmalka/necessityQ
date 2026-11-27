package app.necessityq.domain

/**
 * One item as the server's reading returned it (functions/src/interpret.ts): a generic Hebrew [name], an optional
 * [variant], an amount when the text gave or implied one, and the supermarket section.
 */
data class InterpretedItem(
    val name: String,
    val variant: String?,
    val amount: Double?,
    /** "piece", "package", "kilogram", "gram", "liter", "milliliter", or null. */
    val unit: String?,
    /** A [Category] name; the catalogue's category wins when it knows the item. */
    val category: String,
)

/** An item proposed for the list from a reading, as the household will see it before adding. */
data class ProposedItem(val identity: ItemIdentity, val quantity: Quantity?)

/** Port: reads free text as the grocery items meant (Claude, through a cloud function). Null = could not ask. */
interface ItemInterpreter {
    suspend fun interpret(householdId: String, text: String, people: Double): List<InterpretedItem>?
}

/**
 * The quick-add box understands most typing by itself ([ItemSearch]): "חלב", "2 קוטג 5%", "פלפלים בכל מיני
 * צבעים". Only text it can't read as a known item ("משהו לכריכים לילדים", "מה שצריך לשקשוקה", "חלב סויה", a
 * misspelling) is sent to be read by the model, and the reading is turned into items here.
 */
object QuickAddIntent {
    /**
     * True when no known item (the household's own or the catalogue's) has every word typed. Amounts and kinds
     * ("2", "5%", "צבעוני") don't count: they are read locally.
     */
    fun needsReading(text: String, history: Collection<ItemIdentity>): Boolean {
        val parsed = QuickEntryParser.parse(text) ?: return false
        val q = ItemSearch.query(parsed.nameText) ?: return false
        // a number or a code alone is not something to read
        if (q.words.all { w -> w.any { it.isDigit() } }) return false
        val names = history.map { it.name } + Catalog.items.flatMap { listOf(it.name) + it.aliases }
        return !ItemSearch.fitsFully(q, names)
    }

    private val units = mapOf(
        "piece" to QuantityUnit.PIECE, "package" to QuantityUnit.PACKAGE,
        "kilogram" to QuantityUnit.KILOGRAM, "gram" to QuantityUnit.GRAM,
        "liter" to QuantityUnit.LITER, "milliliter" to QuantityUnit.MILLILITER,
    )

    /**
     * The reading as list items: names go through the same identity as typed text (so "עגבניה" is the list's
     * "עגבניות" and a brand is dropped); the model's category is used when the catalogue doesn't know the item.
     */
    fun proposals(items: List<InterpretedItem>): List<ProposedItem> = items.mapNotNull { item ->
        val text = listOfNotNull(item.name, item.variant).joinToString(" ").trim()
        if (text.isBlank()) return@mapNotNull null
        val identity = ItemIdentity.fromText(text).let { id ->
            if (id.category != Category.OTHER) id
            else id.copy(category = Category.entries.firstOrNull { it.name == item.category } ?: Category.OTHER)
        }
        val quantity = item.amount?.takeIf { it > 0 }?.let { Quantity(it, units[item.unit] ?: QuantityUnit.PIECE) }
        ProposedItem(identity, quantity)
    }.distinctBy { it.identity.id }
}
