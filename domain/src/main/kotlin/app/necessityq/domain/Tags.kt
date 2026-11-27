package app.necessityq.domain

/** Kinds of badge a product tile can carry, so items are easy to tell apart at a glance. */
enum class TagKind { ROUTINE, OCCASIONAL, SPECIAL, FAVORITE, MISSING, RECIPE, AUTO, LARGE_AMOUNT }

data class ItemTag(val kind: TagKind, val label: String)

object ItemTags {
    /**
     *  - קבוע: a routine item (regular cadence in the history)
     *  - לפעמים: bought now and then
     *  - מיוחד: one-off or never bought before (occasions, new products)
     *  - מועדף / חסר: user flags
     *  - מתכון / תפריט: the line was derived from a recipe or the menu. With [compact] (the list) only the kind
     *    is shown, with a count when there are several ("מתכון | 2"); otherwise (the item page) each one is named.
     *  - אוטומטי: generated from past purchases (not added by a person)
     *  - כמות חריגה: more than this item is usually bought in one trip, and nobody confirmed it ([QuantityNorms])
     */
    fun of(line: ListLine, stats: ItemStats?, favorite: Boolean, compact: Boolean = false): List<ItemTag> = buildList {
        if (line.missing) add(ItemTag(TagKind.MISSING, "חסר"))
        if (line.status == LineStatus.NEEDED && QuantityNorms.warningFor(line, stats?.typicalQuantity) != null) {
            add(ItemTag(TagKind.LARGE_AMOUNT, "כמות חריגה"))
        }
        val parts = line.recipeParts.ifEmpty {
            listOfNotNull(line.recipe?.let { RecipePart(it, null, fromMenu = line.isFromMenu) })
        }
        if (compact) {
            parts.distinctBy { it.fromMenu to it.dish }.groupBy { it.fromMenu }.toSortedMap().forEach { (fromMenu, group) ->
                val kind = if (fromMenu) "תפריט" else "מתכון"
                add(ItemTag(TagKind.RECIPE, if (group.size > 1) "$kind | ${group.size}" else kind))
            }
        } else {
            parts.forEach { p ->
                val label = (if (p.fromMenu) "תפריט: " else "מתכון: ") + p.dish + (p.quantity?.let { " · ${it.format()}" } ?: "")
                add(ItemTag(TagKind.RECIPE, label))
            }
        }
        when (stats?.classification) {
            Classification.ROUTINE -> add(ItemTag(TagKind.ROUTINE, stats.cadenceDays?.let(Rhythm::text) ?: "בקצב קבוע"))
            Classification.OCCASIONAL -> add(ItemTag(TagKind.OCCASIONAL, "לפעמים"))
            Classification.ONE_OFF, null -> add(ItemTag(TagKind.SPECIAL, "מיוחד"))
        }
        if (favorite) add(ItemTag(TagKind.FAVORITE, "מועדף"))
        if (line.isAuto) add(ItemTag(TagKind.AUTO, "אוטומטי"))
    }
}
