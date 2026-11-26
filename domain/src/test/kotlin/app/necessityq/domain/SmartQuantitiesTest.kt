package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SmartQuantitiesTest {
    private val today = LocalDate.of(2026, 6, 1)
    private fun member(id: String, birthYear: Int?) = FamilyMember(id, id, birthYear = birthYear, birthMonth = 6)

    @Test fun `children count for less than adults and unknown ages count as adults`() {
        val family = listOf(member("a", 1985), member("b", 1987), member("c", 2020), member("d", 2025), member("e", null))
        // 1 + 1 + 0.5 (6 years: 72 months is not below 72, so 0.75) + 0.25 + 1
        assertEquals(1.0 + 1.0 + 0.75 + 0.25 + 1.0, HouseholdSize.people(family, today), 0.0001)
        assertEquals(0.0, HouseholdSize.people(emptyList(), today), 0.0)
    }

    @Test fun `milk and eggs follow the size of the household`() {
        assertEquals(Quantity(2.0, QuantityUnit.LITER), SmartQuantities.forItem("חלב", 7.0, 2.0)) // 1.8 L → 2
        assertEquals(Quantity(3.0, QuantityUnit.LITER), SmartQuantities.forItem("חלב", 7.0, 4.0)) // 4 people share: 2.9 L → 3
        assertEquals(Quantity.pieces(10), SmartQuantities.forItem("ביצים", 7.0, 2.0)) // 9.6 → 10
    }

    @Test fun `a longer cadence needs a bigger purchase and the amount is rounded up to a step`() {
        assertEquals(Quantity.pieces(18), SmartQuantities.forItem("ביצים", 14.0, 2.0)) // 2 weeks, 17.3 → 18
        assertEquals(Quantity(0.5, QuantityUnit.KILOGRAM), SmartQuantities.forItem("תפוחים", 7.0, 1.0)) // 0.27 → one step
    }

    @Test fun `items without a published figure get a rough estimate, and unknown items get nothing`() {
        assertEquals(Quantity.pieces(4), SmartQuantities.forItem("לחם", 7.0, 3.0)) // 3.8 → 4
        assertEquals(Quantity(2.0, QuantityUnit.KILOGRAM), SmartQuantities.forItem("עגבניות", 7.0, 3.0)) // 1.8 → 2
        assertTrue(SmartQuantities.isEstimate("לחם"))
        assertFalse(SmartQuantities.isEstimate("חלב"))
        assertNull(SmartQuantities.forItem("פלפל חריף מיוחד", 7.0, 3.0))
    }

    @Test fun `no profiles or an unknown item gives no amount`() {
        assertNull(SmartQuantities.forItem("חלב", 7.0, 0.0))
        assertNull(SmartQuantities.forItem("פלפל חריף מיוחד", 7.0, 3.0))
    }

    @Test fun `a suggestion keeps its own usual amount`() {
        val identity = ItemIdentity.fromText("חלב")
        val own = Suggestion("x", identity, SuggestionKind.FREQUENT, Quantity.pieces(2), 1.0, "")
        assertEquals(Quantity.pieces(2), SmartQuantities.fill(own, null, 4.0).quantity)
        val none = own.copy(quantity = null)
        assertEquals(Quantity(3.0, QuantityUnit.LITER), SmartQuantities.fill(none, null, 4.0).quantity)
    }

    // ---- learning from the household's own purchases ----

    private val zone = java.time.ZoneOffset.UTC
    private val start = LocalDate.of(2026, 1, 1)

    private fun stats(item: String, amount: Quantity, times: Int = 4): ItemStats {
        val identity = ItemIdentity.fromText(item)
        val purchases = (0 until times).map { i ->
            val day = start.plusDays(i * 7L)
            Purchase("p_${identity.id}_$i", identity.id, identity.name, identity.variant, identity.category, amount,
                day.atTime(10, 0).toInstant(zone).toEpochMilli(), "u1", null)
        }
        return ConsumptionStatsEngine(zone).compute(purchases, start.plusDays(21)).values.single()
    }

    @Test fun `a household that buys twice the table teaches the estimates to double`() {
        val people = 2.0
        // the table says 2 L of milk and 10 eggs for two people a week; this family buys 4 L and 20
        val learned = listOf(stats("חלב", Quantity(4.0, QuantityUnit.LITER)), stats("ביצים", Quantity.pieces(20)))
        val k = SmartQuantities.calibration(learned, people)
        assertEquals(2.0, k, 0.01)
        assertEquals(Quantity.pieces(3), SmartQuantities.forItem("לחם", 7.0, people)) // 2 people × 1.5
        assertEquals(Quantity.pieces(6), SmartQuantities.forItem("לחם", 7.0, people, k)) // doubled by what it learned
    }

    @Test fun `one item is too little to learn from, and the lesson is bounded`() {
        assertEquals(1.0, SmartQuantities.calibration(listOf(stats("חלב", Quantity(4.0, QuantityUnit.LITER))), 2.0), 0.0)
        val huge = listOf(stats("חלב", Quantity(40.0, QuantityUnit.LITER)), stats("ביצים", Quantity.pieces(200)))
        assertEquals(2.0, SmartQuantities.calibration(huge, 2.0), 0.0)
        val tiny = listOf(stats("חלב", Quantity(0.5, QuantityUnit.LITER)), stats("ביצים", Quantity.pieces(1)))
        assertEquals(0.5, SmartQuantities.calibration(tiny, 2.0), 0.0)
        assertEquals(1.0, SmartQuantities.calibration(huge, 0.0), 0.0) // no profiles: nothing to compare with
    }

    @Test fun `a big household buys less per person than a single one`() {
        val one = SmartQuantities.forItem("קוטג'", 7.0, 1.0)!!.amount
        val eight = SmartQuantities.forItem("קוטג'", 7.0, 8.0)!!.amount
        assertTrue(eight > one) // still more in all
        assertTrue(eight / 8.0 < one) // but fewer each
        assertEquals(1.0, HouseholdSize.shared(1.0), 0.0)
    }
}

