package app.necessityq.domain

/**
 * Reads what is typed in the quick-add box as the item meant, and ranks names (the household's items, the
 * catalogue, a branch's products) by how well they fit it. Words are compared as words, never as any part of a
 * word:
 *
 *  - the same word, or the same word in another form: "פלפלים" is "פלפל", "שוקיים" is "שוקי עוף",
 *    "תפוחי אדמה" is "תפוח אדמה";
 *  - a longer word that begins with what was typed ("לחם" → "לחמית", "לחמניות") is a match while the word is
 *    still being typed, and only *related* once the typed word is a whole word: offered, but after the close ones;
 *  - a word plus ה or ת is another word, not another form: "חלב" is not "חלבה" (related at most);
 *  - words that only describe ("בכל מיני צבעים", "כל מיני") are read as the kind asked for, not searched for.
 *
 * A name where the first word typed is its first word ranks first: in Hebrew the first word is what the thing is
 * ("פירורי לחם" is crumbs). When no name has every word, the names with most of them are offered as related.
 * [QuickAddCompletions] then splits what was found by supermarket section, the section meant first.
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
        // final letters as regular ones: "לחם" begins "לחמית" and "לחמניות"
        prefixOk && plain(word).startsWith(plain(typed)) -> Fit.PREFIX
        else -> null
    }

    /** How a name fits: its [score] (higher is better), and whether it is [close] (every word, as typed or in another form). */
    data class Match(val score: Int, val close: Boolean)

    /**
     * How well [name] fits [q], or null when it does not: every typed word must be a word of the name ([relaxed]:
     * at least one, fully typed). The last word may be a beginning ([prefixOk]); when it is a whole word already
     * ([lastIsWord]) such a match is only related. [extra] words must be in the name as they are ("5%").
     */
    private fun match(
        q: Query, name: List<String>, prefixOk: Boolean, lastIsWord: Boolean, relaxed: Boolean, extra: List<String> = emptyList(),
    ): Match? {
        if (name.isEmpty() || !extra.all { it in name }) return null
        var total = 0
        var matched = 0
        var beginningOfWord = false
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
                if (b == Fit.PREFIX) beginningOfWord = true else matched++
                total += b.points * 10
                if (i == 0 && at == 0) total += 15 // what the thing is: the first word typed is its first word
            }
        }
        if (relaxed && matched == 0) return null
        if (name.size == q.words.size) total += 5 // nothing more than what was typed
        if (q.colors && name.any { it in colorWords }) total += 8
        val close = !relaxed && !(beginningOfWord && lastIsWord)
        return Match(total - name.size, close)
    }

    /** A candidate with how it fits. */
    data class Ranked<T>(val value: T, val match: Match)

    /**
     * [candidates] that fit [q], close ones first, then by score (ties keep their order). Each candidate may have
     * several names (a catalogue item and its aliases); the best one counts. Longer words that begin with a whole
     * word typed come as related; when no candidate has every word, those with most of them, as related.
     */
    fun <T> rank(
        q: Query,
        candidates: List<T>,
        vocabulary: Vocabulary,
        withVariant: Boolean = false,
        names: (T) -> List<String>,
    ): List<Ranked<T>> {
        val lastIsWord = vocabulary.isWord(q.words.last())
        val extra = if (withVariant) q.variantWords else emptyList()
        fun pass(relaxed: Boolean) = candidates.mapIndexedNotNull { index, c ->
            names(c).mapNotNull { match(q, words(it), prefixOk = true, lastIsWord, relaxed, extra) }
                .maxWithOrNull(compareBy<Match> { it.close }.thenBy { it.score })
                ?.let { Triple(c, it, index) }
        }.sortedWith(compareByDescending<Triple<T, Match, Int>> { it.second.close }.thenByDescending { it.second.score }.thenBy { it.third })
            .map { Ranked(it.first, it.second) }
        val strict = pass(relaxed = false)
        return if (strict.none { it.match.close } && q.words.size > 1) strict + pass(relaxed = true).filter { r -> strict.none { it.value == r.value } } else strict
    }

    /** Some of [names] has every word of [q], fully typed: the text names a known item and needs no reading. */
    fun fitsFully(q: Query, names: Iterable<String>): Boolean =
        names.any { match(q, words(it), prefixOk = false, lastIsWord = true, relaxed = false) != null }
}

/** One suggestion in the quick-add box, and where it comes from. */
data class Completion(val identity: ItemIdentity, val source: Source) {
    enum class Source { MINE, CATALOG, STORE }
}

/** Suggestions of one supermarket section; [main] = the section of what was typed ("לחם" → לחם ומאפים). */
data class CompletionGroup(val category: Category, val main: Boolean, val items: List<Completion>)

/**
 * Quick-add suggestions split by supermarket section, so related products are offered but never mixed with what was
 * meant: "לחם" gives לחם, לחם מלא, לחמניות under "לחם ומאפים", and לחמית (crackers) under "דגני בוקר וחטיפים",
 * פירורי לחם under "מזווה ובישול". The section meant comes first; within a section the close matches come first,
 * the household's own items first on a tie, then the catalogue, then the branch's products.
 *
 * A kind typed ("קוטג 5%", "פלפלים בכל מיני צבעים") is kept: catalogue items are offered in it, the household's items
 * only when they are of it, and products only when they have it.
 */
object QuickAddCompletions {
    const val MAX_GROUPS = 4
    const val PER_GROUP = 6

    fun build(text: String, stats: Collection<ItemStats>, products: List<BranchProduct>? = null): List<CompletionGroup> {
        val q = ItemSearch.query(text) ?: return emptyList()
        val variantKey = q.variant?.let(HebrewText::matchKey)
        val mine = stats
            .filter { variantKey == null || HebrewText.matchKey(it.identity.variant.orEmpty()) == variantKey }
            .sortedByDescending { it.frequencyScore }
            .map { it.identity }
        val vocabulary = ItemSearch.vocabulary(mine.map { it.name } + products.orEmpty().map { it.name })

        class Found(val completion: Completion, val match: ItemSearch.Match)
        val local = mine.map { Completion(it, Completion.Source.MINE) to listOf(it.name) } +
            Catalog.items.map { Completion(ItemIdentity(it.name, q.variant, it.category), Completion.Source.CATALOG) to listOf(it.name) + it.aliases }
        val found = ItemSearch.rank(q, local, vocabulary) { it.second }.map { Found(it.value.first, it.match) }.toMutableList()
        if (products != null && q.words.joinToString(" ").length >= 2) {
            // generic names of the best products only: reading every name is the slow part
            found += ItemSearch.rank(q, products, vocabulary, withVariant = true) { listOf(it.name) }
                .asSequence().take(MAX_GROUPS * PER_GROUP * 4)
                .map { Found(Completion(Scan.genericIdentity(it.value), Completion.Source.STORE), it.match) }
                .filter { it.completion.identity.name.isNotBlank() }
                .toList()
        }
        // one entry per item: the household's own, else the catalogue's, else the product's
        val best = found
            .sortedWith(
                compareByDescending<Found> { it.match.close }.thenByDescending { it.match.score }.thenBy { it.completion.source.ordinal },
            )
            .distinctBy { it.completion.identity.id }

        // the section meant: the catalogue item named exactly, else the best close match
        val parts = ItemIdentity.parts(text)
        val meant = Catalog.lookup(Catalog.stripBrands(parts.text))?.category
            ?: best.firstOrNull { it.match.close && it.completion.identity.category != Category.OTHER }?.completion?.identity?.category

        return best.groupBy { it.completion.identity.category }
            .map { (category, items) -> CompletionGroup(category, category == meant, items.take(PER_GROUP).map { it.completion }) to items.first() }
            .sortedWith(
                compareByDescending<Pair<CompletionGroup, Found>> { it.first.main }
                    .thenByDescending { it.second.match.close }
                    .thenByDescending { it.second.match.score },
            )
            .take(MAX_GROUPS)
            .map { it.first }
    }
}
