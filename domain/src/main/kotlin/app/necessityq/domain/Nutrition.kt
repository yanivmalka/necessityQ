package app.necessityq.domain

/*
 * Tzameret, Israel's national nutrition database (Ministry of Health, via data.gov.il).
 * The ingestion function stores one snapshot; here it is searched and used for three things:
 *  - a starting set of dishes: a composite food's composition becomes a dish's ingredient list,
 *  - converting household amounts to weight ("2 עגבניות" ≈ 250 g) through its household measures,
 *  - nutrition per 100 g for a dish or an ingredient.
 * Tzameret names generic foods ("עגבנייה, טרייה"), not products, so it never replaces the catalogue.
 */

/** A household measure of a food: "כוס" = 180 g. */
data class FoodMeasure(val name: String, val grams: Double)

/** One ingredient of a composite food, as a share of the dish's weight. */
data class FoodPart(val code: Int, val name: String, val percent: Double)

/** A Tzameret food; nutrient values are per 100 g and may be missing. */
data class NutritionFood(
    val code: Int,
    val name: String,
    val kcal: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val carbs: Double? = null,
    val fiber: Double? = null,
    val sodiumMg: Double? = null,
    val measures: List<FoodMeasure> = emptyList(),
    val parts: List<FoodPart> = emptyList(),
) {
    /** The name before the first comma: "עגבנייה, טרייה" → "עגבנייה". */
    val baseName: String get() = name.substringBefore(',').trim()

    val isComposite: Boolean get() = parts.isNotEmpty()

    /** Weight of one piece, from a measure named "יחידה" (medium preferred). */
    val gramsPerPiece: Double?
        get() = measures.filter { it.name.contains("יחידה") }.let { pieces ->
            (pieces.firstOrNull { it.name.contains("בינונ") } ?: pieces.firstOrNull())?.grams
        }

    fun summary(): String = listOfNotNull(
        kcal?.let { "${Quantity.formatNumber(it)} קק\"ל" },
        protein?.let { "חלבון ${Quantity.formatNumber(it)} גרם" },
        carbs?.let { "פחמימות ${Quantity.formatNumber(it)} גרם" },
        fat?.let { "שומן ${Quantity.formatNumber(it)} גרם" },
    ).joinToString(" · ").let { if (it.isEmpty()) it else "$it ל-100 גרם" }
}

class NutritionIndex(val foods: List<NutritionFood>, val updatedAtMillis: Long = 0) {
    private val byCode = foods.associateBy { it.code }

    fun food(code: Int): NutritionFood? = byCode[code]

    /**
     * Foods whose name contains every word of [query] (singular/plural tolerant).
     * Order: base name equal to the query, then names starting with it, then shorter names.
     */
    fun search(query: String, limit: Int = 30, compositeOnly: Boolean = false): List<NutritionFood> {
        val words = HebrewStem.words(query)
        if (words.isEmpty()) return emptyList()
        val target = words.joinToString(" ")
        return foods.asSequence()
            .filter { !compositeOnly || it.isComposite }
            .mapNotNull { f ->
                val nameWords = HebrewStem.words(f.name)
                if (!words.all { w -> nameWords.any { it.startsWith(w) } }) return@mapNotNull null
                val base = HebrewStem.words(f.baseName).joinToString(" ")
                val rank = when {
                    base == target -> 0
                    base.startsWith(target) -> 1
                    nameWords.firstOrNull()?.startsWith(words.first()) == true -> 2
                    else -> 3
                }
                Triple(f, rank, f.name.length)
            }
            .sortedWith(compareBy({ it.second }, { it.third }))
            .take(limit)
            .map { it.first }
            .toList()
    }

    /**
     * The Tzameret food that best stands for a generic shopping item ("עגבניות" → "עגבנייה, טרייה"):
     * same base name, preferring fresh/raw foods and ones that have household measures.
     */
    fun match(itemName: String): NutritionFood? {
        val target = HebrewStem.words(ItemIdentity.fromText(itemName).name).joinToString(" ")
        if (target.isEmpty()) return null
        return foods.filter { HebrewStem.words(it.baseName).joinToString(" ") == target }
            .minWithOrNull(
                compareBy<NutritionFood>(
                    { if (rawHints.any { h -> it.name.contains(h) }) 0 else 1 },
                    { if (it.measures.isNotEmpty()) 0 else 1 },
                    { it.name.length },
                ),
            )
    }

    /** Weight in grams of [quantity] of an item, or null when it cannot be estimated. */
    fun grams(itemName: String, quantity: Quantity): Double? = when (quantity.dimension) {
        Dimension.WEIGHT -> quantity.baseAmount
        Dimension.VOLUME -> {
            val cup = match(itemName)?.measures?.firstOrNull { it.name.trim() == "כוס" }?.grams
            quantity.baseAmount * (cup?.div(CUP_ML) ?: 1.0)
        }
        Dimension.COUNT -> if (quantity.unit == QuantityUnit.PIECE) match(itemName)?.gramsPerPiece?.times(quantity.amount) else null
    }

    /**
     * A dish built from a composite food: its ingredients by share of weight, water and traces
     * dropped, each named by its base name ("שמן זית, כתית" → "שמן זית").
     */
    fun dishFrom(food: NutritionFood, id: String): Dish = Dish(
        id = id,
        name = food.baseName.ifBlank { food.name },
        ingredients = food.parts
            .filter { it.percent >= MIN_PART_PERCENT }
            .map { (byCode[it.code]?.baseName ?: it.name.substringBefore(',')).trim() }
            .filter { it.isNotEmpty() && HebrewText.matchKey(it) !in ignoredParts }
            .distinctBy { HebrewText.matchKey(it) }
            .ifEmpty { listOf(food.baseName) },
        nutritionCode = food.code,
        // Tzameret describes a food per 100 g: it says nothing about how many servings a dish makes
        servingsKnown = false,
    )

    companion object {
        const val CUP_ML = 240.0
        const val MIN_PART_PERCENT = 1.0
        private val rawHints = listOf("טרי", "גולמי", "נא", "חי")
        private val ignoredParts = setOf("מים", "מי ברז", "מים מינרליים")
    }
}

/** Very light Hebrew normalisation so singular and plural forms compare equal. */
object HebrewStem {
    /** "עגבנייה" and "עגבניות" → "עגבני"; "ביצה"/"ביצים" → "ביצ". */
    fun stem(word: String): String {
        var w = HebrewText.matchKey(word).replace("יי", "י").replace("וו", "ו")
        if (w.length <= 3) return finals(w)
        for (suffix in listOf("ות", "ים", "ה", "ת")) {
            if (w.length - suffix.length >= 2 && w.endsWith(suffix)) {
                w = w.dropLast(suffix.length)
                break
            }
        }
        return finals(w)
    }

    /** Final letters as regular ones (after the suffix is gone), so "מלפפון" and "מלפפונים" share a stem. */
    private fun finals(w: String) = w.replace('ך', 'כ').replace('ם', 'מ').replace('ן', 'נ').replace('ף', 'פ').replace('ץ', 'צ')

    /** A stem this short (up to four letters) is a whole word: as a prefix it would also begin other words ("שוק" ← "שוקו", "שוקולד"). */
    private const val SHORT_STEM = 4

    /**
     * True when [stem] (of a query word) starts one of [words]; with [exact] (an item of the list, a specific kind of
     * product — not a search) a short stem must be the whole word, so "שוקיים" is not "שוקו"; a plural construct ending in י also matches
     * its singular ("תפוחי אדמה" ↔ "תפוח אדמה").
     */
    fun matchesAny(words: List<String>, stem: String, exact: Boolean = false): Boolean =
        words.any { if (exact && stem.length <= SHORT_STEM) it == stem || it == stem + "י" else it.startsWith(stem) } ||
            (stem.length > 3 && stem.endsWith("י") && stem.dropLast(1).let { base -> words.any { it == base || it.startsWith(base) && it.length <= base.length + 1 } })

    /** Words of a name, each stemmed; commas and parentheses split words. */
    fun words(text: String): List<String> =
        HebrewText.matchKey(text).split(' ', ',', '(', ')', '/').filter { it.isNotBlank() }.map(::stem)
}

/** Port for the Tzameret snapshot. */
interface NutritionRepository {
    /** The current snapshot (cached), or null when it is not available yet. */
    suspend fun index(): NutritionIndex?
}

class NoNutritionRepository : NutritionRepository {
    override suspend fun index(): NutritionIndex? = null
}
