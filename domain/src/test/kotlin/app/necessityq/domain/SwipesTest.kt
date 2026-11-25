package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipesTest {
    @Test
    fun `while making the list a swipe only removes or does nothing`() {
        assertEquals(listOf(SwipeChoice.NONE, SwipeChoice.REMOVE), SwipeChoice.of(ListMode.PLAN))
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
