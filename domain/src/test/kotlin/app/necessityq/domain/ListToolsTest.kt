package app.necessityq.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ListToolsTest {
    private val zone = ZoneOffset.UTC
    private val start = LocalDate.of(2026, 1, 1)

    private fun line(
        id: String, name: String, category: Category, added: Long = 1, source: LineSource = LineSource.MANUAL,
        status: LineStatus = LineStatus.NEEDED, quantity: Quantity? = null,
    ) = ListLine(
        id = id, itemId = id, name = name, variant = null, category = category, quantity = quantity, note = null,
        status = status, source = source, addedBy = "u", addedAtMillis = added, updatedAtMillis = added,
    )

    private fun purchase(item: String, day: LocalDate, hour: Int = 10): Purchase {
        val identity = ItemIdentity.fromText(item)
        return Purchase(
            id = "p_${identity.id}_${day}_$hour", itemId = identity.id, name = identity.name, variant = identity.variant,
            category = identity.category, quantity = Quantity.pieces(1),
            boughtAtMillis = day.atTime(hour, 0).toInstant(zone).toEpochMilli(), boughtBy = "u", lineId = null,
        )
    }

    @Test
    fun `in the shop what was bought stays in its place`() {
        val lines = listOf(
            line("1", "חלב", Category.DAIRY, status = LineStatus.BOUGHT),
            line("2", "לחם", Category.BAKERY),
        )
        assertEquals(listOf("2"), ListOrdering.arrange(lines, ListSort.ALPHA)[0].lines.map { it.id })
        assertEquals(listOf("1", "2"), ListOrdering.arrange(lines, ListSort.ALPHA, includeBought = true)[0].lines.map { it.id }.sorted())
    }

    @Test fun `cart load puts heavy first, fresh after dry, chilled and frozen last`() {
        val lines = listOf(
            line("1", "גלידה", Category.FROZEN),
            line("2", "חלב", Category.DAIRY),
            line("3", "עגבניות", Category.PRODUCE),
            line("4", "פסטה", Category.PANTRY),
            line("5", "מים מינרליים", Category.DRINKS),
            line("6", "קמח", Category.PANTRY),
        )
        val sections = ListOrdering.arrange(lines, ListSort.CART_LOAD)
        assertEquals(
            listOf(CartLoad.Stage.HEAVY, CartLoad.Stage.DRY, CartLoad.Stage.FRESH, CartLoad.Stage.CHILLED, CartLoad.Stage.FROZEN)
                .map { "load_" + it.name },
            sections.map { it.key },
        )
        assertEquals(listOf("קמח", "מים מינרליים"), sections[0].lines.map { it.name }.sortedDescending())
    }

    @Test fun `chilled last and descending order`() {
        val lines = listOf(
            line("a", "אורז", Category.PANTRY), line("b", "חלב", Category.DAIRY), line("c", "גזר", Category.PRODUCE),
        )
        val groups = ListOrdering.arrange(lines, ListSort.GROUPS, chilledLast = true)
        assertEquals(listOf(Category.PRODUCE, Category.PANTRY, Category.DAIRY), groups.map { it.category })

        val alpha = ListOrdering.arrange(lines, ListSort.ALPHA, descending = true, chilledLast = true)
        assertEquals(listOf("all", ListOrdering.CHILLED_KEY), alpha.map { it.key })
        assertEquals(listOf("גזר", "אורז"), alpha[0].lines.map { it.name })
    }

    @Test fun `only name and time orders can be turned around`() {
        val lines = listOf(
            line("a", "אורז", Category.PANTRY), line("b", "חלב", Category.DAIRY), line("c", "גזר", Category.PRODUCE),
        )
        assertEquals(
            ListOrdering.arrange(lines, ListSort.GROUPS).map { it.category },
            ListOrdering.arrange(lines, ListSort.GROUPS, descending = true).map { it.category },
        )
        assertEquals(listOf(ListSort.ALPHA, ListSort.ADDED), ListSort.entries.filter { it.hasDirection })
    }

    @Test fun `generate from due items skips what is already on the list`() {
        val milk = (0 until 5).map { purchase("חלב 3%", start.plusDays(it * 7L)) }
        val bread = (0 until 5).map { purchase("לחם", start.plusDays(it * 7L)) }
        val today = start.plusDays(34)
        val stats = ConsumptionStatsEngine(zone).compute(milk + bread, today)
        val onList = listOf(line(ItemIdentity.fromText("לחם").id, "לחם", Category.BAKERY))
        val proposal = ListGenerator.propose(GenerateMode.DUE, stats.values, milk + bread, onList, today, zone = zone)
        assertEquals(listOf(ItemIdentity.fromText("חלב 3%").id), proposal.map { it.identity.id })
    }

    @Test fun `generate like the last trip`() {
        val purchases = listOf(
            purchase("חלב", start), purchase("קפה", start.plusDays(3), 9), purchase("סוכר", start.plusDays(3), 11),
        )
        val proposal = ListGenerator.propose(GenerateMode.LAST_TRIP, emptyList(), purchases, emptyList(), start.plusDays(5), zone = zone)
        assertEquals(setOf("קפה", "סוכר"), proposal.map { it.identity.name }.toSet())
    }

    @Test fun `generated lines are auto, reset by scope, undo removes them`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        var n = 0
        val service = ShoppingService(
            repo,
            object : Clock { override fun nowMillis() = 1000L },
            object : IdGenerator { override fun newId() = "id${++n}" },
            zone,
        )
        service.addFromText("h", "u", "קוטג'", emptyList())
        val items = listOf(
            GeneratedItem(ItemIdentity.fromText("חלב"), null, "x"),
            GeneratedItem(ItemIdentity.fromText("קוטג'"), null, "already there"),
        )
        val added = service.addGenerated("h", "u", items, service.snapshot("h").first().lines)
        assertEquals(1, added.size)
        val lines = service.snapshot("h").first().lines
        assertEquals(1, lines.count { it.isAuto })
        assertTrue(ItemTags.of(lines.first { it.isAuto }, null, false).any { it.kind == TagKind.AUTO })

        val removed = service.resetList("h", ResetScope.AUTO_ONLY, lines)
        assertEquals(listOf("חלב"), removed.map { it.name })
        val after = service.snapshot("h").first()
        assertEquals(listOf("קוטג'"), after.lines.filter { it.status == LineStatus.NEEDED }.map { it.name })
        assertEquals(RemoveReason.RESET, after.removed.single().removedReason)

        service.undoGenerated("h", added)
        assertFalse(service.snapshot("h").first().lines.any { it.id in added })
    }

    @Test fun `rejected proposal items are remembered and come back unselected`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = ShoppingService(
            repo,
            object : Clock { override fun nowMillis() = 1_000L },
            object : IdGenerator { override fun newId() = "id" },
            zone,
        )
        val milk = GeneratedItem(ItemIdentity.fromText("חלב"), null, "נקנה בקנייה הקודמת")
        assertTrue(service.snapshot("h").first().suppressed.isEmpty())

        service.rejectGenerated("h", listOf(milk), emptyMap())

        val suppressed = service.snapshot("h").first().suppressed
        assertEquals(setOf(milk.identity.id), suppressed)
        val proposed = ListGenerator.propose(
            GenerateMode.LAST_TRIP, emptyList(), listOf(purchase("חלב", start)), emptyList(), start.plusDays(1),
            zone = zone, suppressed = suppressed,
        ).single()
        assertFalse(proposed.preselected)
        assertTrue(proposed.reason.contains("דחיתם לאחרונה"))
    }

    @Test fun `filter and price estimate`() {
        val lines = listOf(
            line("a", "חלב", Category.DAIRY, source = LineSource.AUTO, quantity = Quantity.pieces(2)),
            line("b", "עגבניות", Category.PRODUCE, quantity = Quantity(500.0, QuantityUnit.GRAM)),
            line("c", "לחם", Category.BAKERY),
        )
        assertEquals(listOf("a"), ListFilter(source = SourceFilter.AUTO).apply(lines).map { it.id })
        assertEquals(listOf("b", "c"), ListFilter(source = SourceFilter.MANUAL).apply(lines).map { it.id })
        val fromMeals = lines + line("d", "ביצים", Category.DAIRY, source = LineSource.RECIPE) +
            line("e", "פסטה", Category.PANTRY, source = LineSource.MENU)
        assertEquals(listOf("d"), ListFilter(source = SourceFilter.RECIPE).apply(fromMeals).filter { it.status == LineStatus.NEEDED }.map { it.id })
        assertEquals(listOf("e"), ListFilter(source = SourceFilter.MENU).apply(fromMeals).filter { it.status == LineStatus.NEEDED }.map { it.id })
        assertEquals(listOf("b", "c"), ListFilter(source = SourceFilter.MANUAL).apply(fromMeals).map { it.id })
        assertEquals(listOf("b"), ListFilter(text = "עגב").apply(lines).map { it.id })

        val estimate = PriceEstimate.of(lines, mapOf("a" to 6.5, "b" to 8.0))
        assertEquals(6.5 * 2 + 8.0 * 0.5, estimate.total, 1e-9)
        assertEquals(2, estimate.pricedLines)
        assertEquals(3, estimate.openLines)
    }

    @Test fun `filter can keep only products with a conversation and describes itself`() {
        val lines = listOf(line("a", "חלב", Category.DAIRY), line("b", "לחם", Category.BAKERY), line("c", "קמח", Category.PANTRY, status = LineStatus.BOUGHT))
        val f = ListFilter(withChatOnly = true)
        assertEquals(listOf("a", "c"), f.apply(lines, setOf("a")).map { it.id })
        assertEquals(listOf("c"), f.apply(lines, emptySet()).map { it.id })
        assertEquals(null, ListFilter().summary())
        assertEquals("עם שיחה", f.summary())
        assertEquals("חיפוש \"חל\" · חסרים · עם שיחה", ListFilter(text = "חל", missingOnly = true, withChatOnly = true).summary())
        assertEquals(false, ListFilter().isActive)
    }
}
