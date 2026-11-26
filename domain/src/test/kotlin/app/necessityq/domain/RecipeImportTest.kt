package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeImportTest {
    private fun page(jsonLd: String) = """
        <html><head><title>x</title>
        <script type="application/ld+json">{"@context":"https://schema.org","@type":"WebSite","name":"אתר"}</script>
        <script type='application/ld+json' class="yoast-schema-graph">$jsonLd</script>
        </head><body>…</body></html>
    """.trimIndent()

    @Test fun `reads a recipe inside a graph with HowToStep instructions`() {
        val r = RecipeImport.fromHtml(
            page(
                """{"@graph":[{"@type":"WebPage"},{"@type":"Recipe","name":"שקשוקה &amp; פטה",
                "recipeYield":["4","4 מנות"],
                "recipeIngredient":["6 ביצים","4 עגבניות","<b>1</b> בצל"],
                "recipeInstructions":[{"@type":"HowToStep","text":"מטגנים את הבצל."},{"@type":"HowToStep","text":"מוסיפים עגבניות ומבשלים&nbsp;10 דקות."}]}]}""",
            ),
            "https://example.com/r",
        )!!
        assertEquals("שקשוקה & פטה", r.name)
        assertEquals(4, r.servings)
        assertEquals(listOf("6 ביצים", "4 עגבניות", "1 בצל"), r.ingredients)
        assertEquals("מטגנים את הבצל.\nמוסיפים עגבניות ומבשלים 10 דקות.", r.instructions)
        assertEquals("https://example.com/r", r.sourceUrl)
    }

    @Test fun `sections keep their title and the type may be a list`() {
        val r = RecipeImport.fromHtml(
            page(
                """[{"@type":["Recipe","NewsArticle"],"name":"עוגה","recipeInstructions":[
                {"@type":"HowToSection","name":"הבצק","itemListElement":[{"@type":"HowToStep","text":"מערבבים"}]},
                {"@type":"HowToSection","name":"הקרם","itemListElement":[{"@type":"HowToStep","text":"מקציפים"}]}]}]""",
            ),
        )!!
        assertEquals("הבצק:\nמערבבים\nהקרם:\nמקציפים", r.instructions)
    }

    @Test fun `one block of html text becomes steps`() {
        val r = RecipeImport.fromHtml(
            page("""{"@type":"Recipe","name":"אורז","recipeInstructions":"<ol><li>1. שוטפים את האורז</li><li>2. מבשלים 20 דקות</li></ol>"}"""),
        )!!
        assertEquals(listOf("שוטפים את האורז", "מבשלים 20 דקות"), Recipes.steps(r.instructions))
    }

    @Test fun `a page without recipe data gives nothing`() {
        assertNull(RecipeImport.fromHtml("<html><script type=\"application/ld+json\">{\"@type\":\"WebSite\"}</script></html>"))
        assertNull(RecipeImport.fromHtml("<html>broken <script type=\"application/ld+json\">{not json</script></html>"))
    }

    @Test fun `finds the link in shared text`() {
        assertEquals("https://www.10dakot.co.il/recipe/x/", RecipeImport.urlIn("מתכון מעולה: https://www.10dakot.co.il/recipe/x/."))
        assertNull(RecipeImport.urlIn("אין כאן קישור"))
    }

    @Test fun `yield comes from a range, an object, or the markup when the data lacks it`() {
        assertEquals(24, RecipeImport.fromHtml(page("""{"@type":"Recipe","name":"עוגיות","recipeYield":"24-30 עוגיות"}"""))!!.servings)
        assertEquals(6, RecipeImport.fromHtml(page("""{"@type":"Recipe","name":"מרק","recipeYield":{"@type":"QuantitativeValue","value":"6"}}"""))!!.servings)
        assertEquals(8, RecipeImport.fromHtml(page("""{"@type":"Recipe","name":"פשטידה"}""").replace("</body>", "<span itemprop=\"recipeYield\">8 מנות</span></body>"))!!.servings)
        assertNull(RecipeImport.fromHtml(page("""{"@type":"Recipe","name":"סלט"}"""))!!.servings)
    }

    @Test fun `a tzameret dish does not claim a yield`() {
        assertEquals(false, Dish("d", "x", emptyList(), servingsKnown = false).servingsKnown)
    }
}
