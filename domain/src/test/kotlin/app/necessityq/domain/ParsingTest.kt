package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsingTest {

    private fun parse(text: String) = QuickEntryParser.parse(text)!!

    @Test fun `brand is dropped and percent becomes the variant`() {
        val a = ItemIdentity.fromText("קוטג' תנובה 5%")
        val b = ItemIdentity.fromText("קוטג 5%")
        assertEquals("קוטג'", a.name)
        assertEquals("5%", a.variant)
        assertEquals(a.id, b.id)
        assertEquals(Category.DAIRY, a.category)
    }

    @Test fun `different fat percent is a different item`() {
        assertNotEquals(ItemIdentity.fromText("קוטג 5%").id, ItemIdentity.fromText("קוטג 9%").id)
    }

    @Test fun `geresh variants are equivalent`() {
        assertEquals(ItemIdentity.fromText("קוטג׳").id, ItemIdentity.fromText("קוטג'").id)
    }

    @Test fun `alias resolves to canonical name`() {
        assertEquals("קורנפלקס", ItemIdentity.fromText("קורן פלקס").name)
        assertEquals("עגבניות", ItemIdentity.fromText("עגבניה").name)
    }

    @Test fun `unknown item keeps the user's wording`() {
        val item = ItemIdentity.fromText("רוטב פסטו")
        assertEquals("רוטב פסטו", item.name)
    }

    @Test fun `leading count`() {
        val p = parse("2 קוטג' 5%")
        assertEquals("קוטג'", p.item.name)
        assertEquals("5%", p.item.variant)
        assertEquals(Quantity(2.0, QuantityUnit.PIECE), p.quantity)
    }

    @Test fun `trailing multiplier`() {
        val p = parse("קוטג 9% x3")
        assertEquals("9%", p.item.variant)
        assertEquals(Quantity(3.0, QuantityUnit.PIECE), p.quantity)
    }

    @Test fun `weight with gershayim unit`() {
        val p = parse("1.5 ק\"ג עגבניות")
        assertEquals("עגבניות", p.item.name)
        assertEquals(Quantity(1.5, QuantityUnit.KILOGRAM), p.quantity)
    }

    @Test fun `half kilo in words`() {
        val p = parse("חצי קילו גבינה צהובה")
        assertEquals("גבינה צהובה", p.item.name)
        assertEquals(Quantity(0.5, QuantityUnit.KILOGRAM), p.quantity)
    }

    @Test fun `word number with package unit`() {
        val p = parse("שתי חבילות פסטה")
        assertEquals("פסטה", p.item.name)
        assertEquals(Quantity(2.0, QuantityUnit.PACKAGE), p.quantity)
    }

    @Test fun `trailing volume`() {
        val p = parse("חלב 3% 2 ליטר")
        assertEquals("חלב", p.item.name)
        assertEquals("3%", p.item.variant)
        assertEquals(Quantity(2.0, QuantityUnit.LITER), p.quantity)
    }

    @Test fun `no quantity`() {
        val p = parse("גבינה צהובה 28%")
        assertEquals("גבינה צהובה", p.item.name)
        assertEquals("28%", p.item.variant)
        assertNull(p.quantity)
    }

    @Test fun `percent is not taken as quantity`() {
        assertNull(parse("חלב 1%").quantity)
    }

    @Test fun `empty input`() {
        assertNull(QuickEntryParser.parse("   "))
    }

    @Test fun `quantity addition across units`() {
        val sum = Quantity(500.0, QuantityUnit.GRAM) + Quantity(1.0, QuantityUnit.KILOGRAM)
        assertEquals(Quantity(1.5, QuantityUnit.KILOGRAM), sum)
        assertNull(Quantity(1.0, QuantityUnit.LITER) + Quantity(1.0, QuantityUnit.KILOGRAM))
        assertEquals("1.5 ק\"ג", sum!!.format())
    }
}
