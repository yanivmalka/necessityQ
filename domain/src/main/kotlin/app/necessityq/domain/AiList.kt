package app.necessityq.domain

/** One line of the list the AI built: a catalogue product, how much for the span, and why (in Hebrew). */
data class AiListLine(val name: String, val amount: Double, val unit: QuantityUnit, val reason: String)

/** What the AI answered, and whether it came from the shared cache (the same makeup asked before). */
data class AiListAnswer(val lines: List<AiListLine>, val cached: Boolean, val model: String?)

/**
 * Port: the AI builds a household's list itself — which products and how much — from the ages at home and how
 * many [days] it has to last, choosing only from [catalog]. Throws [EstimateException] when it could not ask.
 */
interface ListSuggester {
    suspend fun suggest(householdId: String, ages: List<Int>, days: Int, catalog: List<GenericItemDef>, exclude: List<String>): AiListAnswer
}

object AiList {
    const val REASON_PREFIX = "AI: "

    /**
     * The AI's lines as a proposal for review: each a catalogue item (its own name and section), skipping what is
     * already on the list ([skipIds]) and anything the catalogue does not know.
     */
    fun toProposal(lines: List<AiListLine>, skipIds: Set<String>): List<GeneratedItem> {
        val seen = mutableSetOf<String>()
        return lines.mapNotNull { line ->
            val def = Catalog.lookup(line.name) ?: return@mapNotNull null
            val identity = ItemIdentity(def.name, null, def.category)
            if (identity.id in skipIds || !seen.add(identity.id)) return@mapNotNull null
            GeneratedItem(
                identity = identity,
                quantity = line.amount.takeIf { it > 0.0 && it.isFinite() }?.let { Quantity(it, line.unit) },
                reason = REASON_PREFIX + line.reason.ifBlank { "לפי הרכב הבית" },
            )
        }
    }
}
