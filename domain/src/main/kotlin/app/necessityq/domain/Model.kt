package app.necessityq.domain

enum class LineStatus { NEEDED, BOUGHT }

enum class LineSource { MANUAL, SUGGESTED, QUICK_ADD }

/** One entry on the household's shared shopping list. */
data class ListLine(
    val id: String,
    val itemId: String,
    val name: String,
    val variant: String?,
    val category: Category,
    val quantity: Quantity?,
    val note: String?,
    val status: LineStatus,
    val source: LineSource,
    val addedBy: String,
    val addedAtMillis: Long,
    val updatedAtMillis: Long,
    val boughtAtMillis: Long? = null,
) {
    val identity: ItemIdentity get() = ItemIdentity(name, variant, category)
    val displayName: String get() = identity.displayName
}

/** A recorded purchase: the raw material of all statistics. Written once when a line is checked off. */
data class Purchase(
    val id: String,
    val itemId: String,
    val name: String,
    val variant: String?,
    val category: Category,
    val quantity: Quantity?,
    val boughtAtMillis: Long,
    val boughtBy: String,
    val lineId: String?,
) {
    val identity: ItemIdentity get() = ItemIdentity(name, variant, category)
}

/** User feedback on a suggestion: hidden until [untilEpochDay]. */
data class SuggestionSuppression(
    val itemId: String,
    val untilEpochDay: Long,
)

data class Household(
    val id: String,
    val name: String,
    val joinCode: String,
    val members: List<String>,
)
