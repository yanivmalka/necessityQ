package app.necessityq.domain

enum class LineStatus { NEEDED, BOUGHT, REMOVED }

/** Why a line went to the recycle bin. */
enum class RemoveReason(val label: String) { DELETED("נמחק"), CLEARED("נוקה אחרי קנייה") }

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
    /** Manual order within the list; 0 = never moved, [addedAtMillis] then decides (add order is kept). */
    val position: Long = 0L,
    /** Flagged as missing/urgent; chosen household members are notified. */
    val missing: Boolean = false,
    val missingBy: String? = null,
    /** Set when the line came from a planned menu / recipe (future meal planning). */
    val recipe: String? = null,
    /** Recycle bin: set while [status] is REMOVED. */
    val removedAtMillis: Long? = null,
    val removedReason: RemoveReason? = null,
    /** Status before removal, so a cleared bought line can be put back as bought. */
    val removedFrom: LineStatus? = null,
) {
    val identity: ItemIdentity get() = ItemIdentity(name, variant, category)
    val displayName: String get() = identity.displayName

    /** Sort key honouring manual moves while preserving the order of adding for untouched lines. */
    val orderKey: Long get() = if (position != 0L) position else addedAtMillis
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
    /** Where it was bought (see [Store]); null when unknown. */
    val storeId: String? = null,
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
