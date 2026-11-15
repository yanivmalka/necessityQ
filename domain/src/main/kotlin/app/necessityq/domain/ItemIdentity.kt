package app.necessityq.domain

/**
 * The identity of a generic item on the list: a brand-free [name] plus an optional
 * distinguishing [variant] such as "5%" or "ללא לקטוז".
 *
 * "קוטג' תנובה 5%" and "קוטג 5%" are the same item; "קוטג' 9%" is a different one.
 */
data class ItemIdentity(
    val name: String,
    val variant: String?,
    val category: Category,
) {
    /** Stable key used as the document id for statistics and consolidation. */
    val id: String
        get() = buildString {
            append(HebrewText.matchKey(name).replace(' ', '_'))
            if (!variant.isNullOrBlank()) {
                append("__")
                append(HebrewText.matchKey(variant).replace(' ', '_').replace("%", "pct"))
            }
        }

    val displayName: String get() = if (variant.isNullOrBlank()) name else "$name $variant"

    companion object {
        private val percent = Regex("(\\d+(?:[.,]\\d+)?)\\s*%")
        private val descriptors = listOf(
            "ללא לקטוז", "דל שומן", "מופחת שומן", "ללא סוכר", "ללא גלוטן", "אורגני", "טבעוני", "דל נתרן",
        )

        /** Builds the identity from free text (no quantity). */
        fun fromText(raw: String): ItemIdentity {
            var text = HebrewText.clean(raw)
            val variantParts = mutableListOf<String>()

            percent.find(text)?.let { match ->
                variantParts += match.groupValues[1].replace(',', '.') + "%"
                text = text.removeRange(match.range)
            }
            for (descriptor in descriptors) {
                if (text.contains(descriptor)) {
                    variantParts += descriptor
                    text = text.replace(descriptor, " ")
                }
            }
            text = HebrewText.clean(text)
            val unbranded = Catalog.stripBrands(text)
            val exact = Catalog.lookup(unbranded)
            val def = exact ?: Catalog.bestContainedMatch(unbranded)
            // An exact alias collapses to the canonical spelling ("קוטג" → "קוטג'");
            // otherwise keep the user's wording, which may be more specific than the catalogue.
            val name = exact?.name ?: unbranded
            return ItemIdentity(
                name = name.ifBlank { HebrewText.clean(raw) },
                variant = variantParts.joinToString(" ").ifBlank { null },
                category = def?.category ?: Category.OTHER,
            )
        }
    }
}
