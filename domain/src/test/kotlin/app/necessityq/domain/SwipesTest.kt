package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipesTest {
    @Test
    fun `making the list removes or talks, the shop marks and talks - never the other way round`() {
        assertEquals(listOf(SwipeChoice.NONE, SwipeChoice.REMOVE, SwipeChoice.CHAT), SwipeChoice.of(ListMode.PLAN))
        assertEquals(listOf(SwipeChoice.NONE, SwipeChoice.BOUGHT, SwipeChoice.MISSING, SwipeChoice.CHAT), SwipeChoice.of(ListMode.SHOP))
        // an old setting that removes in the shop does nothing now
        assertEquals(
            SwipeActions(SwipeChoice.BOUGHT, SwipeChoice.NONE),
            SwipeActions(SwipeChoice.BOUGHT, SwipeChoice.REMOVE).allowedIn(ListMode.SHOP),
        )
        assertEquals(
            SwipeActions(SwipeChoice.NONE, SwipeChoice.REMOVE),
            SwipeActions(SwipeChoice.BOUGHT, SwipeChoice.REMOVE).allowedIn(ListMode.PLAN),
        )
        assertEquals(SwipeActions(SwipeChoice.CHAT, SwipeChoice.CHAT), SwipeActions(SwipeChoice.CHAT, SwipeChoice.CHAT).allowedIn(ListMode.SHOP))
    }

    @Test
    fun `defaults are as before and the hint says what each side does`() {
        assertEquals("משכו שמאלה: נקנה · ימינה: חסר", SwipeActions.defaultFor(ListMode.SHOP).hint)
        assertEquals("משכו ימינה: הסרה", SwipeActions.defaultFor(ListMode.PLAN).hint)
        assertNull(SwipeActions(SwipeChoice.NONE, SwipeChoice.NONE).hint)
        assertEquals(SwipeChoice.MISSING, SwipeChoice.decode("SOMETHING", SwipeChoice.MISSING))
    }
}
