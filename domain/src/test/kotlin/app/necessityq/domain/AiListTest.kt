package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiListTest {
    private fun line(name: String, amount: Double = 2.0, unit: QuantityUnit = QuantityUnit.PIECE) = AiListLine(name, amount, unit, "סיבה")

    @Test fun `AI lines become catalogue items with their amount and an AI reason`() {
        val milk = Catalog.items.first()
        val out = AiList.toProposal(listOf(line(milk.name, 3.0, QuantityUnit.LITER)), emptySet())
        assertEquals(1, out.size)
        assertEquals(milk.name, out[0].identity.name)
        assertEquals(milk.category, out[0].identity.category)
        assertEquals(Quantity(3.0, QuantityUnit.LITER), out[0].quantity)
        assertTrue(out[0].reason.startsWith(AiList.REASON_PREFIX))
    }

    @Test fun `unknown products, duplicates and what is on the list are left out`() {
        val a = Catalog.items[0]
        val b = Catalog.items[1]
        val onList = ItemIdentity(b.name, null, b.category).id
        val out = AiList.toProposal(listOf(line(a.name), line(a.name), line("מוצר שלא קיים בקטלוג"), line(b.name)), setOf(onList))
        assertEquals(listOf(a.name), out.map { it.identity.name })
    }
}
