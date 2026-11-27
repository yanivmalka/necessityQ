package app.necessityq.domain

/**
 * Reads what is typed in the quick-add box as the item meant, and ranks names (the household's items, the
 * catalogue, a branch's products) by how well they fit it. Words are compared as words, never as any part of a
 * word:
 *
 *  - the same word, or the same word in another form: "פלפלים" is "פלפל", "שוקיים" is "שוקי עוף",
 *    "תפוחי אדמה" is "תפוח אדמה";
 *  - the beginning of a word only while that word is still being typed: "לח" may become "לחם" or "לחמית",
 *    but "לחם" is a whole word, so it is bread and not "לחמית" (crackers); "שוקיים" is never "שוקו";
 *  - a word plus ה or ת is another word, not another form: "חלב" is not "חלבה";
 *  - words that only describe ("בכל מיני צבעים", "כל מיני") are read as the kind asked for, not searched for.
 *
 * A name where the first word typed is its first word ranks first: in Hebrew the first word is what the thing is
 * ("פירורי לחם" is crumbs). When no name has every word, the names with most of them are offered.
 */
object ItemSearch {
    /** Colour words of products: with "בכל מיני צבעים" the coloured kinds come first. */
    private val colorWords = setOf("אדום", "אדומה", "אדומים", "צהוב", "צהובה", "צהובים", "ירוק", "ירוקה", "ירוקים", "כתום", "כתומה", "כתומים", "סגול", "סגולה")

    /** Variant words that do not narrow which products fit ("צבעוני" is a mix of the coloured ones). */
    internal val looseVariantWords: Set<String> = setOf(HebrewText.matchKey(ItemIdentity.COLORFUL))

    /** What was typed: the naming [words] (the last may be unfinished) and the kind asked for. */
    data class Query(val words: List<String>, val variant: String?, val colors: Boolean) {
        /** Variant words a product must also have ("5%", "ללא לקטוז"); a mix of colours narrows nothing. */
        val variantWords: List<String> = variant?.let { words(it) }.orEmpty().filter { it !in looseVariantWords }
    }

    /** The words of [text] (quantity already removed), or null when nothing names an item. */
    fun query(text: String): Query? {
        val parts = ItemIdentity.parts(text)
        val words = words(Catalog.stripBrands(parts.text))
        if (words.isEmpty()) return null
        return Query(words, parts.variant, parts.colors)
    }

    /** Words of a name, compared form: no quotes or hyphens; commas, slashes and parentheses split words. */
    fun words(name: String): List<String> =
        HebrewText.matchKey(name).split(' ', ',', '(', ')', '/').filter { it.isNotBlank() }

    /** Every word of the names that can be offered: tells a whole word typed ("לחם") from a beginning ("לח"). */
    class Vocabulary(names: Iterable<String>) {
        private val words = HashSet<String>()
        private val stems = HashSet<String>()

        init {
            for (name in names) for (w in words(name)) if (words.add(w)) stems += forms(w)
        }

        /** [word] is a whole word of some name, as it is or in another form ("שוקיים" ← "שוקי עוף"). */
        fun isWord(word: String): Boolean = word in words || (word.length >= 4 && forms(word).any { it in stems })
    }

    /** The vocabulary of the catalogue (names and aliases) and [names]. */
    fun vocabulary(names: Iterable<String>): Vocabulary =
        Vocabulary(Catalog.items.flatMap { listOf(it.name) + it.aliases } + names)

    private enum class Fit(val points: Int) { EXACT(3), FORM(2), PREFIX(1) }

    /** Stems of a word: its own, and that of its construct form ("שוקי" → "שוק", "תפוחי" → "תפוח"). */
    private fun forms(word: String): List<String> = listOfNotNull(
        HebrewStem.stem(word),
        word.takeIf { it.length > 3 && it.endsWith("י") }?.dropLast(1)?.let(HebrewStem::stem),
    )

    private fun plain(word: String) =
        word.replace("יי", "י").replace('ך', 'כ').replace('ם', 'מ').replace('ן', 'נ').replace('ף', 'פ').replace('ץ', 'צ')

    /** One word plus ה or ת is a different word ("חלב" / "חלבה", "לבן" / "לבנה"), not another form of it. */
    private fun oneLetterApart(a: String, b: String): Boolean {
        val pa = plain(a)
        val pb = plain(b)
        return (pb.length == pa.length + 1 && pb.startsWith(pa) && pb.last() in "הת") ||
            (pa.length == pb.length + 1 && pa.startsWith(pb) && pa.last() in "הת")
    }

    /** The same word in another form: plural, feminine, construct ("עגבניות" / "עגבנייה", "שוקיים" / "שוקי"). */
    internal fun sameForm(a: String, b: String): Boolean {
        // forms share their beginning: a different first letter is never the same word (and is cheap to check)
        if (a.length < 3 || b.length < 3 || a[0] != b[0] || oneLetterApart(a, b)) return false
        val fa = forms(a)
        return forms(b).any { it in fa }
    }

    private fun fit(typed: String, word: String, prefixOk: Boolean): Fit? = when {
        word == typed -> Fit.EXACT
        sameForm(typed, word) -> Fit.FORM
        prefixOk && word.startsWith(typed) -> Fit.PREFIX
        else -> null
    }

    /**
     * How well [name] fits [q] (higher is better), or null when it does not: every typed word must be a word of
     * the name ([relaxed]: at least one, fully typed). [extra] words must be in the name as they are ("5%").
     */
    private fun score(q: Query, name: List<String>, prefixOk: Boolean, relaxed: Boolean, extra: List<String> = emptyList()): Int? {
        if (name.isEmpty() || !extra.all { it in name }) return null
        var total = 0
        var matched = 0
        q.words.forEachIndexed { i, typed ->
            val last = i == q.words.lastIndex
            var best: Fit? = null
            var at = -1
            name.forEachIndexed { j, word ->
                val f = fit(typed, word, prefixOk && last)
                if (f != null && (best == null || f.points > best!!.points)) { best = f; at = j }
            }
            val b = best
            if (b == null) {
                if (!relaxed) return null
            } else {
                if (b != Fit.PREFIX) matched++
                total += b.points * 10
                if (i == 0 && at == 0) total += 15 // what the thing is: the first word typed is its first word
            }
        }
        if (relaxed && matched == 0) return null
        if (name.size == q.words.size) total += 5 // nothing more than what was typed
        if (q.colors && name.any { it in colorWords }) total += 8
        return total - name.size
    }

    /**
     * [candidates] that fit [q], best first (ties keep their order). Each candidate may have several names (a
     * catalogue item and its aliases); the best one counts. When none has every word, those with most of them.
     */
    fun <T> rank(
        q: Query,
        candidates: List<T>,
        vocabulary: Vocabulary,
        withVariant: Boolean = false,
        names: (T) -> List<String>,
    ): List<T> {
        // the last word may be a beginning only while it is not a whole word yet
        val prefixOk = !vocabulary.isWord(q.words.last())
        val extra = if (withVariant) q.variantWords else emptyList()
        fun pass(relaxed: Boolean) = candidates.mapIndexedNotNull { index, c ->
            names(c).mapNotNull { score(q, words(it), prefixOk, relaxed, extra) }.maxOrNull()?.let { Triple(c, it, index) }
        }.sortedWith(compareByDescending<Triple<T, Int, Int>> { it.second }.thenBy { it.third }).map { it.first }
        val strict = pass(relaxed = false)
        return if (strict.isEmpty() && q.words.size > 1) pass(relaxed = true) else strict
    }

    /**
     * Generic items from a branch's [products] for [text] ("לחם" → "לחם אחיד", not "לחמית"), leaving out the
     * item ids in [exclude] (offered already).
     */
    fun storeItems(products: List<BranchProduct>, text: String, exclude: Set<String> = emptySet(), limit: Int = 8): List<ItemIdentity> {
        val q = query(text) ?: return emptyList()
        if (q.words.joinToString(" ").length < 2) return emptyList()
        val vocabulary = vocabulary(products.map { it.name })
        return rank(q, products, vocabulary, withVariant = true) { listOf(it.name) }
            .asSequence()
            .take(limit * 8)
            .map { Scan.genericIdentity(it) }
            .filter { it.id !in exclude && it.name.isNotBlank() }
            .distinctBy { it.id }
            .take(limit)
            .toList()
    }
}
