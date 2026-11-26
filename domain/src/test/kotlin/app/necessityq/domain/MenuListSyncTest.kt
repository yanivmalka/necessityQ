package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuListSyncTest {
    private fun id(text: String) = ItemIdentity.fromText(text).id
    private val shakshuka = Dish("a", "שקשוקה", listOf("8 ביצים", "6 עגבניות", "בצל"), servings = 4)
    private val salad = Dish("b", "סלט", listOf("4 עגבניות", "בצל", "טחינה", "שמן זית"), servings = 4)
    private val soup = Dish("c", "מרק", listOf("300 גרם בצל"), servings = 4)

    private fun menu(vararg meals: Pair<Int, Dish>): List<Pair<ScaledIngredient, String>> {
        val data = MealData(
            dishes = meals.map { it.second }.distinctBy { it.id },
            plan = MealPlan(meals = meals.map { (day, dish) -> PlannedMeal(day, if (day % 2 == 0) MealSlot.DINNER else MealSlot.LUNCH, dish.id) }),
        )
        return MealPlanner.ingredientsFor(data, MealPlan.DEFAULT_ANCHOR, 7)
    }

    private fun apply(lines: List<ListLine>, changes: List<MenuListSync.Change>): List<ListLine> {
        var out = lines
        changes.forEach { c ->
            out = when (c) {
                is MenuListSync.Change.Add -> out + ListLine(
                    "n${out.size}", c.item.id, c.item.name, c.item.variant, c.item.category, c.quantity, null,
                    LineStatus.NEEDED, LineSource.MENU, "u", 0, 0, recipe = c.parts.first().dish, recipeParts = c.parts,
                )
                is MenuListSync.Change.Update -> out.map { if (it.id == c.lineId) it.copy(quantity = c.quantity, recipeParts = c.parts) else it }
            }
        }
        return out
    }

    private fun List<ListLine>.qty(text: String) = first { it.itemId == id(text) }.quantity

    @Test fun `two dishes that share an ingredient add up`() {
        val lines = apply(emptyList(), MenuListSync.plan(emptyList(), menu(0 to shakshuka, 1 to salad)))
        assertEquals(Quantity.pieces(10), lines.qty("עגבניות"))
        // "בצל" with no amount is one onion in each recipe
        assertEquals(Quantity.pieces(2), lines.qty("בצל"))
        // a jar of tahini has no amount to add up
        assertEquals(null, lines.qty("טחינה"))
        // oil is a staple: the menu does not put it on the list
        assertTrue(lines.none { it.itemId == id("שמן זית") })
        assertEquals(setOf("שקשוקה", "סלט"), lines.first { it.itemId == id("עגבניות") }.recipeParts.map { it.dish }.toSet())
    }

    @Test fun `the same dish twice counts twice, and adding the menu again changes nothing`() {
        val ingredients = menu(0 to shakshuka, 2 to shakshuka)
        val lines = apply(emptyList(), MenuListSync.plan(emptyList(), ingredients))
        assertEquals(Quantity.pieces(16), lines.qty("ביצים"))
        assertTrue(MenuListSync.plan(lines, ingredients).isEmpty())
    }

    @Test fun `an amount already on the list is kept, and the menu's part changes by the difference`() {
        val manual = ListLine("t", id("עגבניות"), "עגבניות", null, ItemIdentity.fromText("עגבניות").category, Quantity.pieces(3), null, LineStatus.NEEDED, LineSource.MANUAL, "u", 0, 0)
        val first = apply(listOf(manual), MenuListSync.plan(listOf(manual), menu(0 to shakshuka)))
        assertEquals(Quantity.pieces(9), first.qty("עגבניות"))
        // a salad joins the menu: only its four tomatoes are added
        val second = apply(first, MenuListSync.plan(first, menu(0 to shakshuka, 1 to salad)))
        assertEquals(Quantity.pieces(13), second.qty("עגבניות"))
    }

    @Test fun `pieces and a weight of the same produce add up through the weight of a piece`() {
        val lines = apply(emptyList(), MenuListSync.plan(emptyList(), menu(0 to shakshuka, 1 to soup)))
        val onion = lines.qty("בצל")!!
        val g = PieceWeights.gramsPerPiece("בצל")!!
        // one onion for the shakshuka and 300 g for the soup: enough for both, never "the newer one wins"
        val total = if (onion.dimension == Dimension.COUNT) onion.amount * g else onion.baseAmount
        assertTrue("$onion", total >= 300 + g - 1e-9)
    }

    @Test fun `adding to a line converts pieces and weight instead of replacing`() {
        val g = PieceWeights.gramsPerPiece("בצל")!!
        val merged = ListRules.combine("בצל", Quantity.pieces(2), Quantity(300.0, QuantityUnit.GRAM))!!
        assertEquals(Dimension.COUNT, merged.dimension)
        assertEquals(2.0 + kotlin.math.ceil(300 / g), merged.amount, 1e-9)
        // nothing known about the weight of a piece: the newer entry wins, as before
        assertEquals(Quantity(1.0, QuantityUnit.LITER), ListRules.combine("חלב", Quantity.pieces(2), Quantity(1.0, QuantityUnit.LITER)))
    }

    @Test fun `a dish on the list through the menu counts as on the list everywhere`() {
        val lines = apply(emptyList(), MenuListSync.plan(emptyList(), menu(0 to shakshuka)))
        // the recipe's own sync does not own these lines, but every screen must show the same state
        assertTrue(RecipeListSync.onList(lines, shakshuka).isEmpty())
        assertTrue(RecipeListSync.onListAny(lines, shakshuka))
        assertTrue(!RecipeListSync.onListAny(lines, salad))
    }

    @Test fun `kits and extra slots take part in the plan like any dish`() {
        val kit = Dish("k", "ערכת יום הולדת", listOf("2 בלונים", "1 ק\"ג סוכר"), kind = DishKind.KIT)
        assertTrue(kit.isKit)
        val data = MealData(
            dishes = listOf(kit),
            plan = MealPlan(meals = listOf(PlannedMeal(0, MealSlot.OTHER, "k"), PlannedMeal(0, MealSlot.SNACK, "k"))),
        )
        assertEquals(2, MealPlanner.mealsOn(data, MealPlan.DEFAULT_ANCHOR).size)
        assertTrue(MealPlanner.extraSlots.all { it !in MealPlanner.daySlots })
    }
}
