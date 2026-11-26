package app.necessityq.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A recipe read from a web page: what is needed to fill a [Dish]. */
data class ImportedRecipe(
    val name: String,
    val ingredients: List<String>,
    /** The method, one step per line. */
    val instructions: String,
    val servings: Int?,
    val sourceUrl: String? = null,
) {
    val hasInstructions: Boolean get() = instructions.isNotBlank()
}

/**
 * Reads a recipe from a recipe site's page. Almost every recipe site (and the WordPress recipe plugins
 * most Israeli food blogs use) describes the recipe for search engines as schema.org Recipe data in a
 * `<script type="application/ld+json">` block: name, recipeIngredient, recipeInstructions, recipeYield.
 * Tzameret has only the composition of a dish, never the method, so this is where methods come from.
 */
object RecipeImport {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scriptRe = Regex(
        "<script[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** The recipe described on the page, or null when the page has no recipe data. */
    fun fromHtml(html: String, url: String? = null): ImportedRecipe? =
        scriptRe.findAll(html).firstNotNullOfOrNull { m ->
            val element = runCatching { json.parseToJsonElement(m.groupValues[1].trim()) }.getOrNull()
            element?.let(::findRecipe)?.let { toRecipe(it, url) }
        }?.let { r ->
            // a page whose data lacks the yield often still shows it in its markup
            if (r.servings == null) r.copy(servings = servingsInMarkup(html)) else r
        }

    /** Looks for the Recipe object: on its own, in an array, or in a "@graph". */
    private fun findRecipe(e: JsonElement): JsonObject? = when (e) {
        is JsonArray -> e.firstNotNullOfOrNull(::findRecipe)
        is JsonObject -> when {
            isRecipe(e) -> e
            else -> e["@graph"]?.let(::findRecipe) ?: e["mainEntity"]?.let(::findRecipe)
        }
        else -> null
    }

    private fun isRecipe(o: JsonObject): Boolean = when (val t = o["@type"]) {
        is JsonPrimitive -> t.contentOrNull.equals("Recipe", ignoreCase = true)
        is JsonArray -> t.any { (it as? JsonPrimitive)?.contentOrNull.equals("Recipe", ignoreCase = true) }
        else -> false
    }

    private fun toRecipe(o: JsonObject, url: String?): ImportedRecipe? {
        val name = text(o["name"]).orEmpty()
        val ingredients = (o["recipeIngredient"] ?: o["ingredients"]).let(::strings).map(::clean).filter { it.isNotEmpty() }
        val steps = steps(o["recipeInstructions"])
        if (name.isBlank() && ingredients.isEmpty() && steps.isEmpty()) return null
        return ImportedRecipe(
            name = clean(name),
            ingredients = ingredients,
            instructions = steps.joinToString("\n"),
            servings = servings(o["recipeYield"]) ?: servings(o["yield"]) ?: servings(o["recipeServings"]),
            sourceUrl = url,
        )
    }

    /** recipeInstructions: one text, a list of texts, HowToStep objects, or HowToSection objects holding steps. */
    private fun steps(e: JsonElement?): List<String> = when (e) {
        null -> emptyList()
        is JsonPrimitive -> e.contentOrNull?.let { t ->
            // one block of text: its lines or <li>/<p>/<br> become steps
            Recipes.steps(clean(t.replace(Regex("(?i)<\\s*(br|/p|/li)\\s*/?>"), "\n"), keepLines = true))
        }.orEmpty()
        is JsonArray -> e.flatMap(::steps)
        is JsonObject -> {
            val nested = e["itemListElement"]
            if (nested != null) {
                val title = text(e["name"])?.let(::clean)?.takeIf { it.isNotEmpty() }
                val inner = steps(nested)
                if (title != null && inner.isNotEmpty()) listOf("$title:") + inner else inner
            } else {
                listOfNotNull((text(e["text"]) ?: text(e["name"]))?.let(::clean)?.takeIf { it.isNotEmpty() })
            }
        }
    }

    /**
     * recipeYield: "4", 4, "4 מנות", "6-8 servings", "מספר מנות: 12", an object holding the value, or a list of
     * these; the first number (the low end of a range). Null when there is none: the recipe does not say.
     */
    private fun servings(e: JsonElement?): Int? = when (e) {
        is JsonArray -> e.firstNotNullOfOrNull(::servings)
        is JsonObject -> servings(e["value"] ?: e["@value"] ?: e["name"] ?: e["text"])
        is JsonPrimitive -> e.contentOrNull?.let { servingsIn(it) }
        else -> null
    }

    /** The first number in a yield text; "בין 6 ל-8" is 6. Counts of 1..100 only. */
    fun servingsIn(text: String): Int? =
        Regex("\\d+").find(clean(text))?.value?.toIntOrNull()?.takeIf { it in 1..100 }

    private val markupYield = listOf(
        Regex("itemprop\\s*=\\s*[\"']recipeYield[\"'][^>]*>([^<]{1,40})<", RegexOption.IGNORE_CASE),
        Regex("itemprop\\s*=\\s*[\"']recipeYield[\"'][^>]*content\\s*=\\s*[\"']([^\"']{1,40})[\"']", RegexOption.IGNORE_CASE),
        Regex("wprm-recipe-servings[^>]*>\\s*([0-9]{1,3})\\s*<", RegexOption.IGNORE_CASE),
    )

    private fun servingsInMarkup(html: String): Int? =
        markupYield.firstNotNullOfOrNull { re -> re.find(html)?.groupValues?.get(1)?.let(::servingsIn) }

    private fun strings(e: JsonElement?): List<String> = when (e) {
        is JsonArray -> e.flatMap(::strings)
        is JsonPrimitive -> listOfNotNull(e.contentOrNull)
        is JsonObject -> listOfNotNull(text(e["text"]) ?: text(e["name"]))
        null -> emptyList()
    }

    private fun text(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull
        is JsonArray -> e.firstNotNullOfOrNull(::text)
        else -> null
    }

    /** Removes HTML tags and entities and tidies spaces; [keepLines] keeps line breaks. */
    fun clean(s: String, keepLines: Boolean = false): String {
        val noTags = s.replace(Regex("<[^>]+>"), " ")
        val decoded = decodeEntities(noTags)
        return if (keepLines) {
            decoded.lines().joinToString("\n") { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }.trim()
        } else {
            decoded.replace(Regex("\\s+"), " ").trim()
        }
    }

    private val named = mapOf(
        "amp" to "&", "quot" to "\"", "apos" to "'", "lt" to "<", "gt" to ">", "nbsp" to " ",
        "ndash" to "–", "mdash" to "—", "frac12" to "½", "frac14" to "¼", "frac34" to "¾", "deg" to "°", "rsquo" to "’", "lsquo" to "‘",
        "rdquo" to "”", "ldquo" to "“", "hellip" to "…",
    )

    private fun decodeEntities(s: String): String = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+\\d*);").replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> named[e]
        } ?: m.value
    }

    /** The first web address in shared or pasted text ("מתכון מעולה https://…"). */
    fun urlIn(text: String): String? = Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')
}
