package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipePlanningTest {
    private fun id(text: String) = ItemIdentity.fromText(text).id
    private val shakshuka = Dish("a", "שקשוקה", listOf("8 ביצים", "1 ק\"ג עגבניות", "בצל"), servings = 4)

    private fun line(id: String, text: String, q: Quantity?, source: LineSource, parts: List<RecipePart> = emptyList(), recipe: String? = null): ListLine {
        val item = ItemIdentity.fromText(text)
        return ListLine(id, item.id, item.name, item.variant, item.category, q, null, LineStatus.NEEDED, source, "u", 0, 0, recipe = recipe, recipeParts = parts)
    }

    private fun shownAt(dish: Dish, servings: Int, text: String) = Recipes.scaled(dish, servings).first { it.item.id == id(text) }.quantity

    @Test fun `plus and minus move the amount shown for the chosen servings by one step`() {
        val more = RecipeEdits.adjust(shakshuka, 0, servings = 6, direction = 1)
        assertEquals(13.0, shownAt(more, 6, "ביצים")!!.amount, 1e-9)
        val less = RecipeEdits.adjust(shakshuka, 0, servings = 6, direction = -1)
        assertEquals(11.0, shownAt(less, 6, "ביצים")!!.amount, 1e-9)
        // still the same item after rewriting the text
        assertEquals(id("ביצים"), Recipes.identity(more.ingredients[0])?.id)

        val tomatoes = RecipeEdits.adjust(shakshuka, 1, servings = 6, direction = 1)
        assertEquals(1600.0, shownAt(tomatoes, 6, "עגבניות")!!.baseAmount, 1e-9)

        // an ingredient without an amount gets one unit on +, and nothing on −
        val onion = RecipeEdits.adjust(shakshuka, 2, servings = 6, direction = 1)
        assertEquals(1.0, shownAt(onion, 6, "בצל")!!.amount, 1e-9)
        assertEquals(shakshuka, RecipeEdits.adjust(shakshuka, 2, servings = 6, direction = -1))

        // never below one step
        val one = Dish("b", "ביצה", listOf("1 ביצים"), servings = 1)
        assertEquals(one, RecipeEdits.adjust(one, 0, servings = 1, direction = -1))
    }

    @Test fun `added ingredient is typed for the servings on screen and stored in the recipe yield`() {
        val dish = RecipeEdits.add(shakshuka, "300 גרם פטריות", servings = 6)
        assertEquals(4, dish.ingredients.size)
        assertEquals(200.0, Recipes.scaled(dish, 4).first { it.item.id == id("פטריות") }.quantity!!.baseAmount, 1e-9)
        assertEquals(300.0, shownAt(dish, 6, "פטריות")!!.baseAmount, 1e-9)
        assertEquals(shakshuka.ingredients, RecipeEdits.remove(dish, 3).ingredients)
    }

    @Test fun `what is at home stays off the list`() {
        val skip = setOf(id("בצל"))
        assertTrue(Recipes.scaled(shakshuka, 4, skip).none { it.item.id == id("בצל") })
        val data = MealData(dishes = listOf(shakshuka), plan = MealPlan(meals = listOf(PlannedMeal(0, MealSlot.DINNER, "a"))), pantry = skip)
        val week = MealPlanner.ingredientsFor(data, MealPlan.DEFAULT_ANCHOR, 7)
        assertEquals(listOf(id("ביצים"), id("עגבניות")), week.map { it.first.item.id })
        assertEquals(setOf<String>(), data.withPantry(id("בצל"), false).pantry)
    }

    @Test fun `preview splits ingredients into new, added to a line and at home`() {
        val lines = listOf(line("t", "עגבניות", Quantity(1.5, QuantityUnit.KILOGRAM), LineSource.MANUAL))
        val rows = RecipeListPreview.of(lines, shakshuka, 4, pantry = setOf(id("בצל"))).associateBy { it.item.id }
        assertEquals(RecipeListPreview.Kind.NEW, rows.getValue(id("ביצים")).kind)
        val tomatoes = rows.getValue(id("עגבניות"))
        assertEquals(RecipeListPreview.Kind.MERGE, tomatoes.kind)
        assertEquals(2500.0, tomatoes.after!!.baseAmount, 1e-9)
        assertEquals(RecipeListPreview.Kind.AT_HOME, rows.getValue(id("בצל")).kind)
    }

    @Test fun `taking a recipe off the list deletes its own lines and shrinks shared ones`() {
        val lines = listOf(
            line("e", "ביצים", Quantity.pieces(8), LineSource.RECIPE, listOf(RecipePart("שקשוקה", Quantity.pieces(8))), recipe = "שקשוקה"),
            line("t", "עגבניות", Quantity(2.5, QuantityUnit.KILOGRAM), LineSource.MANUAL, listOf(RecipePart("שקשוקה", Quantity(1.0, QuantityUnit.KILOGRAM))), recipe = "שקשוקה"),
        )
        val changes = RecipeListSync.removal(lines, shakshuka).associateBy {
            when (it) { is RecipeListSync.Removal.Delete -> it.lineId; is RecipeListSync.Removal.Shrink -> it.lineId }
        }
        assertEquals(RecipeListSync.Removal.Delete("e"), changes["e"])
        val shrink = changes["t"] as RecipeListSync.Removal.Shrink
        assertEquals(1500.0, shrink.quantity!!.baseAmount, 1e-9)
        assertTrue(shrink.parts.isEmpty())
    }

    @Test fun `method splits into steps without the typed numbering`() {
        assertEquals(listOf("מחממים שמן.", "מוסיפים בצל."), Recipes.steps("1. מחממים שמן.\n2) מוסיפים בצל."))
        assertEquals(listOf("מחממים שמן.", "מוסיפים בצל."), Recipes.steps("מחממים שמן. מוסיפים בצל."))
        assertEquals(listOf("1.5 כוסות מים"), Recipes.steps("1.5 כוסות מים"))
        assertEquals(emptyList<String>(), Recipes.steps("  \n "))
    }
}
