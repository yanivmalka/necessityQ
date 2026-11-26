package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun `milk and bread follow the size of the household`() {
        assertEquals(Quantity(3.0, QuantityUnit.LITER), SmartQuantities.forItem("חלב", 7.0, 2.0))
        assertEquals(Quantity(6.0, QuantityUnit.LITER), SmartQuantities.forItem("חלב", 7.0, 4.0))
        assertEquals(Quantity.pieces(3), SmartQuantities.forItem("לחם", 7.0, 2.0))
    }

    @Test fun `a longer cadence needs a bigger purchase and the amount is rounded up to a step`() {
        assertEquals(Quantity.pieces(16), SmartQuantities.forItem("ביצים", 14.0, 2.0))
        assertEquals(Quantity(1.0, QuantityUnit.KILOGRAM), SmartQuantities.forItem("עגבניות", 7.0, 1.0)) // 0.7 → 1.0
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
        assertEquals(Quantity(6.0, QuantityUnit.LITER), SmartQuantities.fill(none, null, 4.0).quantity)
    }
}
