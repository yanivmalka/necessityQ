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
    /** Households created before roles existed have no owner; the first member counts as owner. */
    val ownerId: String? = null,
) {
    val effectiveOwnerId: String? get() = ownerId?.takeIf { it in members } ?: members.firstOrNull()
    fun isOwner(userId: String): Boolean = effectiveOwnerId == userId
}

enum class MemberRole { OWNER, MEMBER }

/** A household participant as shown in the members list. */
data class Member(
    val userId: String,
    val displayName: String,
    val email: String?,
    val role: MemberRole,
)

/** The signed-in identity. Anonymous accounts work fully but are lost with the app data. */
data class Account(
    val displayName: String?,
    val email: String?,
    val isAnonymous: Boolean,
)

/** Merges the household's member ids with the profile documents members wrote about themselves. */
object Members {
    fun resolve(household: Household, profiles: Map<String, Pair<String?, String?>>): List<Member> {
        val owner = household.effectiveOwnerId
        return household.members.mapIndexed { index, id ->
            val (name, email) = profiles[id] ?: (null to null)
            Member(
                userId = id,
                displayName = name?.takeIf { it.isNotBlank() } ?: "משתתף ${index + 1}",
                email = email,
                role = if (id == owner) MemberRole.OWNER else MemberRole.MEMBER,
            )
        }.sortedWith(compareBy({ it.role != MemberRole.OWNER }, { it.displayName }))
    }
}
