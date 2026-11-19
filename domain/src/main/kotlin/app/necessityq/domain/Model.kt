package app.necessityq.domain

enum class LineStatus { NEEDED, BOUGHT, REMOVED }

/** Why a line went to the recycle bin. */
enum class RemoveReason(val label: String) { DELETED("נמחק"), CLEARED("נוקה אחרי קנייה"), RESET("איפוס רשימה") }

/**
 * Where a line came from. [AUTO] lines were generated from past purchases ("רשימה מקניות עבר");
 * everything else was put there by a person (typed, or one tap on a suggestion / quick-add chip / menu).
 */
enum class LineSource(val label: String) {
    MANUAL("נוסף ידנית"),
    SUGGESTED("נוסף מהמלצה"),
    QUICK_ADD("נוסף מ'קונים בדרך כלל'"),
    AUTO("נוצר אוטומטית מקניות עבר"),
    RECIPE("נוסף ממתכון"),
    MENU("נוסף מתפריט"),
}

/** How much of a line is for one dish ("שקשוקה: 2 יח'"); [fromMenu] = added from the menu rather than the recipe screen. */
data class RecipePart(val dish: String, val quantity: Quantity?, val fromMenu: Boolean = false)

object RecipeParts {
    /** Adds [part] to the parts: the same dish (and origin) sums its quantity. */
    fun merge(parts: List<RecipePart>, part: RecipePart): List<RecipePart> {
        val same = parts.firstOrNull { it.dish == part.dish && it.fromMenu == part.fromMenu } ?: return parts + part
        val q = when {
            same.quantity == null -> part.quantity
            part.quantity == null -> same.quantity
            else -> (same.quantity + part.quantity) ?: part.quantity
        }
        return parts.map { if (it === same) it.copy(quantity = q) else it }
    }

    /** Sets the dish's part to [part] (used when a recipe's servings change). */
    fun replace(parts: List<RecipePart>, part: RecipePart): List<RecipePart> =
        parts.filterNot { it.dish == part.dish && it.fromMenu == part.fromMenu } + part
}

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
    /** The dish name when the line came from a menu or a recipe. */
    val recipe: String? = null,
    /** The dishes this line is (also) for, with the amount each needs. The line's [quantity] is the total. */
    val recipeParts: List<RecipePart> = emptyList(),
    /** Recycle bin: set while [status] is REMOVED. */
    val removedAtMillis: Long? = null,
    val removedReason: RemoveReason? = null,
    /** Status before removal, so a cleared bought line can be put back as bought. */
    val removedFrom: LineStatus? = null,
    /**
     * An unusually large amount someone confirmed ("כן, זו הכמות"): up to it the list does not flag the
     * line (see [QuantityNorms.warningFor]).
     */
    val approvedQuantity: Quantity? = null,
) {
    val identity: ItemIdentity get() = ItemIdentity(name, variant, category)
    val displayName: String get() = identity.displayName

    /** Generated from past purchases rather than added by a person. */
    val isAuto: Boolean get() = source == LineSource.AUTO

    /** Added from a recipe screen. */
    val isFromRecipe: Boolean get() = source == LineSource.RECIPE || recipeParts.any { !it.fromMenu }

    /** Added from the menu (older versions tagged menu lines as suggestions with a recipe name). */
    val isFromMenu: Boolean get() =
        source == LineSource.MENU || recipeParts.any { it.fromMenu } || (source == LineSource.SUGGESTED && recipe != null)

    /** Put there by a person (typed or a tap), not generated and not from a recipe or the menu. */
    val isManual: Boolean get() =
        source != LineSource.AUTO && source != LineSource.RECIPE && source != LineSource.MENU && !(source == LineSource.SUGGESTED && recipe != null)

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

    /** Imported from the Microsoft To Do history rather than checked off in the app (see [TodoCoverage]). */
    val isFromTodo: Boolean get() = id.startsWith(TODO_PURCHASE_PREFIX)

    companion object {
        const val TODO_PURCHASE_PREFIX = "p_todo_"
    }
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
    /** Profile picture: a small JPEG, Base64-encoded (the member's own or their Google picture). */
    val photo: String? = null,
)

/** The signed-in identity. Anonymous accounts work fully but are lost with the app data. */
data class Account(
    val displayName: String?,
    val email: String?,
    val isAnonymous: Boolean,
    /** Profile picture (small Base64 JPEG): the one the user chose, otherwise the Google account's. */
    val photo: String? = null,
    /** The name on the Google account, to go back to after renaming. */
    val googleName: String? = null,
    /** True when [photo] is one the user picked rather than the Google account's. */
    val customPhoto: Boolean = false,
)

/** Merges the household's member ids with the profile documents members wrote about themselves. */
object Members {
    fun resolve(
        household: Household,
        profiles: Map<String, Pair<String?, String?>>,
        photos: Map<String, String?> = emptyMap(),
    ): List<Member> {
        val owner = household.effectiveOwnerId
        return household.members.mapIndexed { index, id ->
            val (name, email) = profiles[id] ?: (null to null)
            Member(
                userId = id,
                displayName = name?.takeIf { it.isNotBlank() } ?: "משתתף ${index + 1}",
                email = email,
                role = if (id == owner) MemberRole.OWNER else MemberRole.MEMBER,
                photo = photos[id]?.takeIf { it.isNotBlank() },
            )
        }.sortedWith(compareBy({ it.role != MemberRole.OWNER }, { it.displayName }))
    }
}
