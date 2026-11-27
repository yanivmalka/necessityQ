package app.necessityq.data

import app.necessityq.domain.BaselineAmount
import app.necessityq.domain.HouseholdBaseline
import app.necessityq.domain.HouseholdMakeup
import app.necessityq.domain.MealData
import app.necessityq.domain.QuantityUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The first-list survey's answers are kept with the family data, and a member on an older version keeps them. */
class MealJsonBaselineTest {
    private val baseline = HouseholdBaseline(
        filledAtMillis = 42L,
        amounts = mapOf(
            "חלב" to BaselineAmount(3.0, QuantityUnit.LITER, 7),
            "קפה" to BaselineAmount(0.0, QuantityUnit.PACKAGE, 30),
        ),
        makeup = HouseholdMakeup(listOf(30, 30, 9)),
    )

    @Test fun `the answers come back as they were saved`() {
        assertEquals(baseline, MealJson.decode(MealJson.encode(MealData(baseline = baseline))).baseline)
        val skipped = HouseholdBaseline(7L, skipped = true)
        assertEquals(skipped, MealJson.decode(MealJson.encode(MealData(baseline = skipped))).baseline)
    }

    @Test fun `data from before the survey has none`() {
        assertNull(MealJson.decode(MealJson.encode(MealData())).baseline)
        assertNull(MealJson.decode("""{"members":[]}""").baseline)
    }

    @Test fun `a broken answer is dropped, not the whole survey`() {
        val json = """{"baseline":{"filledAt":1,"amounts":{"חלב":{"amount":2,"unit":"LITER","days":7},"x":{"amount":1,"unit":"SPOON","days":7},"y":{"amount":-1,"unit":"PIECE","days":7}}}}"""
        assertEquals(mapOf("חלב" to BaselineAmount(2.0, QuantityUnit.LITER, 7)), MealJson.decode(json).baseline?.amounts)
    }
}
