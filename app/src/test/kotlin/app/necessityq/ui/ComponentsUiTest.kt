package app.necessityq.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.necessityq.domain.ListMode
import app.necessityq.domain.Quantity
import app.necessityq.domain.QuantityUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Basic UI tests: the list's building blocks, rendered with the app's own theme and driven by taps, on the JVM.
 * (The whole app screen needs a signed-in household, permissions and location; its paths are covered by
 * [AppViewModelTest] underneath.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComponentsUiTest {

    @get:Rule val compose = createComposeRule()

    @Test fun `the mode switch shows both stages and marks the current one`() {
        compose.setContent { NecessityTheme(bright = true) { ModeSwitch(shopping = false, onSelect = {}) } }

        compose.onNodeWithText(ListMode.PLAN.label).assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText(ListMode.SHOP.label).assertIsDisplayed().assertIsNotSelected()
    }

    @Test fun `tapping the other stage reports it`() {
        var picked: ListMode? = null
        compose.setContent { NecessityTheme(bright = true) { ModeSwitch(shopping = false, onSelect = { picked = it }) } }

        compose.onNodeWithText(ListMode.SHOP.label).performClick()

        assertEquals(ListMode.SHOP, picked)
    }

    @Test fun `the mode switch follows the state it is given`() {
        var shopping by mutableStateOf(false)
        compose.setContent { NecessityTheme(bright = true) { ModeSwitch(shopping = shopping, onSelect = { shopping = it == ListMode.SHOP }) } }

        compose.onNodeWithText(ListMode.SHOP.label).performClick()

        compose.onNodeWithText(ListMode.SHOP.label).assertIsSelected()
        compose.onNodeWithText(ListMode.PLAN.label).assertIsNotSelected()
    }

    @Test fun `the quantity stepper shows the amount and reports plus and minus`() {
        var plus = 0
        var minus = 0
        compose.setContent {
            NecessityTheme(bright = true) {
                QuantityStepper(Quantity(2.0, QuantityUnit.PIECE), onMinus = { minus++ }, onPlus = { plus++ })
            }
        }

        compose.onNodeWithText(Quantity(2.0, QuantityUnit.PIECE).format()).assertIsDisplayed()
        compose.onNodeWithContentDescription("יותר").performClick()
        compose.onNodeWithContentDescription("יותר").performClick()
        compose.onNodeWithContentDescription("פחות").performClick()

        assertEquals(2, plus)
        assertEquals(1, minus)
    }

    @Test fun `the quantity stepper shows a dash when there is no amount`() {
        compose.setContent { NecessityTheme(bright = true) { QuantityStepper(null, onMinus = {}, onPlus = {}) } }

        compose.onNodeWithText("—").assertIsDisplayed()
    }

    @Test fun `the stepper follows the amount it is given`() {
        var amount by mutableStateOf(Quantity(1.0, QuantityUnit.PIECE))
        compose.setContent {
            NecessityTheme(bright = true) {
                QuantityStepper(
                    amount,
                    onMinus = { amount = Quantity(amount.amount - 1, amount.unit) },
                    onPlus = { amount = Quantity(amount.amount + 1, amount.unit) },
                )
            }
        }

        compose.onNodeWithContentDescription("יותר").performClick()

        compose.onNodeWithText(Quantity(2.0, QuantityUnit.PIECE).format()).assertIsDisplayed()
    }

    @Test fun `a promotion strip shows its text and nothing without one`() {
        var promo by mutableStateOf<String?>("1+1 על גבינות")
        compose.setContent { NecessityTheme(bright = true) { PromoStrip(promo) } }

        compose.onNodeWithText("🏷 1+1 על גבינות").assertIsDisplayed()

        promo = null
        compose.waitForIdle()
        compose.onAllNodesWithText("1+1", substring = true).assertCountEquals(0)
    }
}
