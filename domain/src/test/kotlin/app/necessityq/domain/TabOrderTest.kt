package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TabOrderTest {
    @Test
    fun `every tab once, whatever was stored`() {
        assertEquals(TabOrder.DEFAULT, TabOrder.decode(null))
        val custom = listOf(AppTab.LIST, AppTab.HOME, AppTab.PRICES, AppTab.MENU)
        assertEquals(custom, TabOrder.decode(TabOrder.encode(custom)))
        assertEquals(listOf(AppTab.LIST, AppTab.HOME, AppTab.MENU, AppTab.PRICES), TabOrder.decode("LIST,LIST,OLD,HOME"))
    }

    @Test
    fun `a tab is dragged into place`() {
        assertEquals(
            listOf(AppTab.LIST, AppTab.HOME, AppTab.MENU, AppTab.PRICES),
            TabOrder.move(TabOrder.DEFAULT, from = 3, to = 0),
        )
        assertEquals(TabOrder.DEFAULT, TabOrder.move(TabOrder.DEFAULT, from = 5, to = 0))
    }
}
