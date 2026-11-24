package app.necessityq.domain

/** The bottom-bar tabs. */
enum class AppTab(val label: String) { HOME("הבית"), MENU("תפריט"), PRICES("מחירים"), LIST("רשימה") }

/**
 * The order of the tabs, chosen in the settings (dragged into place; the first is on the right), and the tab the
 * app opens on. Stored as the tab names separated by commas.
 */
object TabOrder {
    val DEFAULT = listOf(AppTab.HOME, AppTab.MENU, AppTab.PRICES, AppTab.LIST)

    fun encode(order: List<AppTab>): String = order.joinToString(",") { it.name }

    /** Every tab exactly once: unknown names are dropped, and a missing tab is added in its default place at the end. */
    fun decode(text: String?): List<AppTab> {
        val read = text.orEmpty().split(',').mapNotNull { n -> AppTab.entries.firstOrNull { it.name == n.trim() } }.distinct()
        return read + DEFAULT.filter { it !in read }
    }

    /** [order] with the tab at [from] moved to [to]. */
    fun move(order: List<AppTab>, from: Int, to: Int): List<AppTab> {
        if (from !in order.indices || to !in order.indices) return order
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }
}
