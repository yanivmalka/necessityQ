package app.necessityq.domain

/** Kinds of badge a product tile can carry, so items are easy to tell apart at a glance. */
enum class TagKind { ROUTINE, OCCASIONAL, SPECIAL, FAVORITE, MISSING, RECIPE, AUTO }

data class ItemTag(val kind: TagKind, val label: String)

object ItemTags {
    /**
     *  - קבוע: a routine item (regular cadence in the history)
     *  - לפעמים: bought now and then
     *  - מיוחד: one-off or never bought before (occasions, new products)
     *  - מועדף / חסר: user flags
     *  - מתכון: the line was derived from a menu or recipe
     *  - אוטומטי: generated from past purchases (not added by a person)
     */
    fun of(line: ListLine, stats: ItemStats?, favorite: Boolean): List<ItemTag> = buildList {
        if (line.missing) add(ItemTag(TagKind.MISSING, "חסר"))
        if (line.recipeParts.isNotEmpty()) {
            line.recipeParts.forEach { p ->
                val label = (if (p.fromMenu) "תפריט: " else "מתכון: ") + p.dish + (p.quantity?.let { " · ${it.format()}" } ?: "")
                add(ItemTag(TagKind.RECIPE, label))
            }
        } else {
            line.recipe?.let { add(ItemTag(TagKind.RECIPE, if (line.isFromMenu) "תפריט: $it" else "מתכון: $it")) }
        }
        when (stats?.classification) {
            Classification.ROUTINE -> add(ItemTag(TagKind.ROUTINE, "קבוע"))
            Classification.OCCASIONAL -> add(ItemTag(TagKind.OCCASIONAL, "לפעמים"))
            Classification.ONE_OFF, null -> add(ItemTag(TagKind.SPECIAL, "מיוחד"))
        }
        if (favorite) add(ItemTag(TagKind.FAVORITE, "מועדף"))
        if (line.isAuto) add(ItemTag(TagKind.AUTO, "אוטומטי"))
    }
}
