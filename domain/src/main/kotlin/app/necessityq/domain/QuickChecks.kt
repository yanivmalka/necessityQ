package app.necessityq.domain

import kotlin.math.abs

/**
 * "בדיקות מהירות" (in the account page, for the test accounts of [FirstRunDemo] only): shortcuts that make the
 * situations of the manual checks of 0.5 (docs/MANUAL_TEST.md, section 10) at once, instead of waiting for days
 * of real use: decided suggestions for the accuracy card, "יש לי בבית" twice, a suggestion turned down three times,
 * and a look over every proposed amount.
 */
object QuickChecks {
    /** Suggestions the accuracy check puts on the list; [BOUGHT] of them are bought, the rest deleted. */
    const val ACCURACY_PICKS = 6
    const val BOUGHT = 4

    fun allowed(email: String?): Boolean = FirstRunDemo.allowed(email)

    /** Suggestions to use for a check: each item once, none already open on the list. */
    fun candidates(snapshot: ListSnapshot): List<Suggestion> {
        val open = snapshot.lines.filter { it.status == LineStatus.NEEDED }.map { it.itemId }.toSet()
        return (snapshot.runningLow + snapshot.tripList + snapshot.frequent).distinctBy { it.itemId }.filter { it.itemId !in open }
    }

    /** One proposed amount and what is wrong with it; [problem] is null when it is fine. */
    data class AmountCheck(val name: String, val amount: String, val problem: String?)

    /**
     * Every proposed amount ([suggestions]: cards, the week's list, the list from past purchases), checked against
     * what a shop sells: no half piece or half pack ("1.5 בקבוקים"), packs of pieces in whole packs ("30 יח'" of
     * eggs is one tray, "6 חב'" of pittas is six bags), and no amount that is far beyond a home's purchase.
     */
    fun amounts(suggestions: List<Suggestion>, packs: Map<String, Pack> = emptyMap()): List<AmountCheck> = suggestions.distinctBy { it.itemId }.mapNotNull { s ->
        val q = s.quantity ?: return@mapNotNull null
        val name = s.identity.name
        val pack = packs[s.itemId]
        val shown = describeAmount(name, s.identity.variant, q, pack)
        val whole = abs(q.amount - Math.round(q.amount)) < 1e-9
        val packs = pack?.packsIn(q)
        val problem = when {
            q.dimension == Dimension.COUNT && !whole -> "חצי יחידה או חצי אריזה"
            packs != null && abs(packs - Math.round(packs)) > 1e-6 -> "לא מארזים שלמים של ${pack.label()}"
            QuantityNorms.check(s.identity, q)?.level == QuantityLevel.ABSURD -> "כמות חריגה מאוד"
            pack != null -> null
            q.unit == QuantityUnit.PIECE && PackSizes.piecesPerPack(name, s.identity.variant, q.amount) != null &&
                PackSizes.toBuy(name, q, s.identity.variant) != q -> "לא מארזים שלמים"
            q.dimension != Dimension.COUNT && PackSizes.sizesFor(name, s.identity.variant).any { it.dimension == q.dimension } &&
                PackSizes.toBuy(name, q, s.identity.variant) != q -> "לא אריזות שלמות"
            QuantityNorms.check(s.identity, q)?.level == QuantityLevel.ABSURD -> "כמות חריגה מאוד"
            else -> null
        }
        AmountCheck(s.identity.displayName, shown, problem)
    }
}
