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

    /** What free text says about an item: the words naming it, and the kind asked for ("5%", "ללא לקטוז", "צבעוני"). */
    internal data class Parts(val text: String, val variant: String?, val colors: Boolean)

    companion object {
        /** The variant of "בכל מיני צבעים", "צבעוניים": a mix of colours, not one colour. */
        const val COLORFUL = "צבעוני"

        private val percent = Regex("(\\d+(?:[.,]\\d+)?)\\s*%")
        private val descriptors = listOf(
            "ללא לקטוז", "דל שומן", "מופחת שומן", "ללא סוכר", "ללא גלוטן", "אורגני", "טבעוני", "דל נתרן",
        )
        // whole words only: Hebrew letters are not \w, so the edges are "no letter before / after"
        private const val START = "(?<!\\p{L})"
        private const val END = "(?!\\p{L})"
        /** "פלפלים בכל מיני צבעים", "בצבעים שונים", "צבעוניים": the household wants a mix of colours. */
        private val colorPhrase = Regex(
            "$START(?:ו?(?:ב|מ)?כל\\s+(?:מיני\\s+|ה)?צבעים|ו?ב?צבעים\\s+שונים|צבעוני(?:ים|ות|ת)?)$END",
        )
        /** Words that say "some of each" and name nothing: "כל מיני", "מכל הסוגים", "סוגים שונים". */
        private val fillerPhrase = Regex(
            "$START(?:ו?(?:ב|מ)?כל\\s+(?:מיני|הסוגים|סוג)|ו?סוגים\\s+שונים)$END",
        )

        /** Splits free text (no quantity) into the naming words and the variant, before any catalogue lookup. */
        internal fun parts(raw: String): Parts {
            var text = HebrewText.clean(raw)
            val variantParts = mutableListOf<String>()

            percent.find(text)?.let { match ->
                variantParts += match.groupValues[1].replace(',', '.') + "%"
                text = text.removeRange(match.range)
            }
            val colors = colorPhrase.containsMatchIn(text)
            if (colors) {
                variantParts += COLORFUL
                text = colorPhrase.replace(text, " ")
            }
            text = fillerPhrase.replace(text, " ")
            for (descriptor in descriptors) {
                if (text.contains(descriptor)) {
                    variantParts += descriptor
                    text = text.replace(descriptor, " ")
                }
            }
            text = HebrewText.clean(text)
            // only a variant was typed ("אורגני"): it is the name
            if (text.isBlank()) return Parts(HebrewText.clean(raw), null, false)
            return Parts(text, variantParts.joinToString(" ").ifBlank { null }, colors)
        }

        /** Builds the identity from free text (no quantity). */
        fun fromText(raw: String): ItemIdentity {
            val parts = parts(raw)
            val unbranded = Catalog.stripBrands(parts.text)
            val exact = Catalog.lookup(unbranded)
            val def = exact ?: Catalog.bestContainedMatch(unbranded)
            // An exact alias collapses to the canonical spelling ("קוטג" → "קוטג'");
            // otherwise keep the user's wording, which may be more specific than the catalogue.
            val name = exact?.name ?: unbranded
            return ItemIdentity(
                name = name.ifBlank { HebrewText.clean(raw) },
                variant = parts.variant,
                category = def?.category ?: Category.OTHER,
            )
        }
    }
}
