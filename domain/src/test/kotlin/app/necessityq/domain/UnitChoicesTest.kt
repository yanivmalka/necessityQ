package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitChoicesTest {
    private fun choices(text: String, current: QuantityUnit? = null): List<QuantityUnit> {
        val item = ItemIdentity.fromText(text)
        return QuantitySteps.choicesFor(item.name, item.category, current)
    }

    @Test fun `produce is bought by the piece or by weight, never in litres`() {
        assertEquals(listOf(QuantityUnit.PIECE, QuantityUnit.PACKAGE, QuantityUnit.KILOGRAM, QuantityUnit.GRAM), choices("עגבניות"))
        assertFalse(QuantityUnit.LITER in choices("בצל"))
    }

    @Test fun `drinks by the bottle or the litre, not by weight`() {
        val water = choices("מים מינרליים")
        assertTrue(QuantityUnit.LITER in water)
        assertFalse(QuantityUnit.KILOGRAM in water)
        assertTrue(QuantityUnit.LITER in choices("חלב"))
        assertFalse(QuantityUnit.LITER in choices("גבינה צהובה"))
        assertTrue(QuantityUnit.GRAM in choices("גבינה צהובה"))
    }

    @Test fun `pantry - oil in litres, flour by weight, eggs and bread by count`() {
        assertTrue(QuantityUnit.LITER in choices("שמן זית"))
        assertFalse(QuantityUnit.KILOGRAM in choices("שמן זית"))
        assertTrue(QuantityUnit.KILOGRAM in choices("קמח"))
        assertFalse(QuantityUnit.LITER in choices("קמח"))
        assertEquals(listOf(QuantityUnit.PIECE, QuantityUnit.PACKAGE), choices("ביצים"))
    }

    @Test fun `the unit in use is always offered`() {
        assertTrue(QuantityUnit.LITER in choices("עגבניות", current = QuantityUnit.LITER))
        assertTrue(QuantityUnit.MILLILITER in choices("חלב", current = QuantityUnit.MILLILITER))
        assertFalse(QuantityUnit.MILLILITER in choices("חלב"))
    }
}
