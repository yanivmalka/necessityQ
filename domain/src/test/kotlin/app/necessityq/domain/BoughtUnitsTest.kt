package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Goods are counted the way they are bought: eggs in packs, milk in litres — not "30 יח'" or "1 יח' חלב". */
class BoughtUnitsTest {
    private val pack = QuantityUnit.PACKAGE

    @Test fun `first plus adds a pack of eggs, not one egg`() {
        assertEquals(Quantity(1.0, pack), QuantitySteps.step(null, +1, "ביצים"))
        assertEquals(Quantity(2.0, pack), QuantitySteps.step(Quantity(1.0, pack), +1, "ביצים"))
        assertEquals(Quantity(1.0, pack), QuantitySteps.step(null, +1, "פיתות"))
    }

    @Test fun `first plus adds a litre of milk and a kilo of flour`() {
        assertEquals(Quantity(1.0, QuantityUnit.LITER), QuantitySteps.step(null, +1, "חלב"))
        assertEquals(Quantity(1.5, QuantityUnit.LITER), QuantitySteps.step(Quantity(1.0, QuantityUnit.LITER), +1, "חלב"))
        assertEquals(Quantity(1.0, QuantityUnit.KILOGRAM), QuantitySteps.step(null, +1, "קמח"))
    }

    @Test fun `items with no pack sizes still start at one piece`() {
        assertEquals(Quantity.pieces(1), QuantitySteps.step(null, +1, "עגבניות"))
        assertEquals(Quantity.pieces(1), QuantitySteps.step(null, +1))
    }

    @Test fun `thirty eggs are shown as a tray, not as thirty pieces`() {
        assertEquals("מארז 30", describeAmount("ביצים", null, Quantity.pieces(30), null))
        assertEquals("מארז 30", describeAmount("ביצים", null, Quantity.pieces(30), Pack(30)))
        assertEquals("2 מארזים של 30", describeAmount("ביצים", null, Quantity.pieces(60), Pack(30)))
        assertEquals("מארז 30", stepperAmount("ביצים", null, Quantity.pieces(30), Pack(30)))
        assertEquals("מארז 12", stepperAmount("ביצים", null, Quantity.pieces(12), null))
        // the +/- stays short for volumes: the litres alone
        assertEquals("4 ליטר", stepperAmount("חלב", null, Quantity(4.0, QuantityUnit.LITER), Pack(1, Quantity(2.0, QuantityUnit.LITER))))
    }

    @Test fun `a slip falls back to the item's first amount`() {
        val milk = QuantityNorms.homeSized("חלב", Category.DAIRY, Quantity(500.0, QuantityUnit.LITER))
        assertEquals(Quantity(1.0, QuantityUnit.LITER), milk)
    }
}
