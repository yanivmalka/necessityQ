package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsAndSuggestionsTest {

    private val zone = ZoneOffset.UTC
    private val engine = ConsumptionStatsEngine(zone)
    private val start = LocalDate.of(2026, 1, 1)

    private fun purchase(item: String, day: LocalDate, qty: Quantity? = Quantity.pieces(1), n: Int = 0): Purchase {
        val identity = ItemIdentity.fromText(item)
        return Purchase(
            id = "p_${identity.id}_${day}_$n",
            itemId = identity.id,
            name = identity.name,
            variant = identity.variant,
            category = identity.category,
            quantity = qty,
            boughtAtMillis = day.atTime(10, 0).toInstant(zone).toEpochMilli(),
            boughtBy = "u1",
            lineId = null,
        )
    }

    private fun every(item: String, days: Int, times: Int, qty: Quantity? = Quantity.pieces(1)) =
        (0 until times).map { purchase(item, start.plusDays((it * days).toLong()), qty) }

    @Test fun `regular cadence is detected as routine`() {
        val purchases = every("חלב 3%", 5, 6)
        val today = start.plusDays(25)
        val stats = engine.compute(purchases, today).values.single()
        assertEquals(5.0, stats.cadenceDays!!, 0.01)
        assertEquals(Classification.ROUTINE, stats.classification)
        assertEquals(start.plusDays(30), stats.nextDue)
        assertTrue(stats.confidence > 0.7)
    }

    @Test fun `single purchase has no cadence`() {
        val stats = engine.compute(listOf(purchase("קמח", start)), start).values.single()
        assertNull(stats.cadenceDays)
        assertNull(stats.nextDue)
        assertEquals(Classification.ONE_OFF, stats.classification)
    }

    @Test fun `same day check-offs count as one trip`() {
        val purchases = listOf(
            purchase("לחם", start, n = 1), purchase("לחם", start, n = 2),
            purchase("לחם", start.plusDays(4)),
        )
        val stats = engine.compute(purchases, start.plusDays(4)).values.single()
        assertEquals(2, stats.purchaseDays)
        assertEquals(Quantity.pieces(1.5), stats.typicalQuantity) // median of 2 (same day) and 1
    }

    @Test fun `typical quantity ignores purchases in another dimension`() {
        // bought as 1 יח' twice, once as 750 g: 750 must never turn into "750 יח'"
        val purchases = listOf(
            purchase("גבינה לבנה", start, Quantity.pieces(1)),
            purchase("גבינה לבנה", start.plusDays(7), Quantity(750.0, QuantityUnit.GRAM)),
            purchase("גבינה לבנה", start.plusDays(14), Quantity.pieces(1)),
            purchase("גבינה לבנה", start.plusDays(21), Quantity.pieces(2)),
        )
        val stats = engine.compute(purchases, start.plusDays(21)).values.single()
        assertEquals(Quantity.pieces(1), stats.typicalQuantity) // median of 1, 1, 2 — the 750 g trip is left out
        // the latest purchase decides the dimension
        val grams = purchases.dropLast(1) + purchase("גבינה לבנה", start.plusDays(21), Quantity(500.0, QuantityUnit.GRAM))
        assertEquals(Quantity(625.0, QuantityUnit.GRAM), engine.compute(grams, start.plusDays(21)).values.single().typicalQuantity)
    }

    @Test fun `stock-up does not slow the cadence`() {
        // usually 1 every 7 days; once bought 2 and the next trip came 14 days later
        val purchases = listOf(
            purchase("קורנפלקס", start),
            purchase("קורנפלקס", start.plusDays(7)),
            purchase("קורנפלקס", start.plusDays(14), Quantity.pieces(2)),
            purchase("קורנפלקס", start.plusDays(28)),
            purchase("קורנפלקס", start.plusDays(35)),
        )
        val stats = engine.compute(purchases, start.plusDays(35)).values.single()
        assertEquals(7.0, stats.cadenceDays!!, 0.01)
    }

    @Test fun `bigger last purchase pushes the due date`() {
        val purchases = every("אורז", 10, 4) + purchase("אורז", start.plusDays(40), Quantity.pieces(2))
        val stats = engine.compute(purchases, start.plusDays(40)).values.single()
        assertEquals(start.plusDays(60), stats.nextDue)
    }

    @Test fun `weighted median favours recent intervals`() {
        val m = ConsumptionStatsEngine.weightedMedian(listOf(10.0, 10.0, 4.0, 4.0, 4.0), listOf(0.1, 0.1, 1.0, 1.0, 1.0))
        assertEquals(4.0, m, 0.0)
    }

    @Test fun `due item is suggested as running low with a reason`() {
        val stats = engine.compute(every("חלב 3%", 5, 6), start.plusDays(29))
        val result = SuggestionEngine().suggest(stats.values, emptySet(), emptyList(), start.plusDays(29))
        val s = result.runningLow.single()
        assertEquals(SuggestionKind.RUNNING_LOW, s.kind)
        assertEquals("בדרך כלל כל 5 ימים · נקנה לפני 4 ימים", s.reason)
        assertTrue(result.frequent.none { it.itemId == s.itemId })
    }

    @Test fun `item already on the list is not suggested`() {
        val stats = engine.compute(every("חלב 3%", 5, 6), start.plusDays(29))
        val id = ItemIdentity.fromText("חלב 3%").id
        val result = SuggestionEngine().suggest(stats.values, setOf(id), emptyList(), start.plusDays(29))
        assertTrue(result.runningLow.isEmpty())
        assertTrue(result.frequent.isEmpty())
    }

    @Test fun `dismissed suggestion stays quiet until the cooldown ends`() {
        val today = start.plusDays(29)
        val stats = engine.compute(every("חלב 3%", 5, 6), today)
        val id = ItemIdentity.fromText("חלב 3%").id
        val until = SuggestionEngine().dismissUntil(stats[id], today)
        assertEquals(today.plusDays(3), until) // half of a 5-day cadence, rounded up
    }

    @Test fun `frequent items ranked by recency weighted frequency`() {
        val purchases = every("לחם", 3, 10) + every("קפה", 30, 2)
        val today = start.plusDays(30)
        val result = SuggestionEngine(dueThreshold = 99.0).suggest(engine.compute(purchases, today).values, emptySet(), emptyList(), today)
        assertEquals("לחם", result.frequent.first().identity.name)
    }

    // ---- the list for the next trip (0.5) ----

    @Test fun `trip list holds what runs out before the next trip, from the real rhythm`() {
        // milk every 5 days (last bought day 25, due day 30), flour every 40 days (last day 40, due day 80)
        val purchases = every("חלב 3%", 5, 6) + every("קמח", 40, 3)
        val today = start.plusDays(27)
        val stats = engine.compute(purchases, today).values
        val trip = SuggestionEngine().forNextTrip(stats, emptySet(), emptyList(), today)
        assertEquals(listOf("חלב 3%"), trip.map { it.identity.displayName })
        assertEquals(SuggestionKind.RUNNING_LOW, trip.single().kind)
    }

    @Test fun `a longer horizon brings in slower items`() {
        val purchases = every("חלב 3%", 5, 6) + every("קמח", 10, 4)
        val today = start.plusDays(30)
        val stats = engine.compute(purchases, today).values
        val week = SuggestionEngine().forNextTrip(stats, emptySet(), emptyList(), today, horizonDays = 3)
        val fortnight = SuggestionEngine().forNextTrip(stats, emptySet(), emptyList(), today, horizonDays = 14)
        assertTrue(fortnight.size >= week.size)
        assertEquals(setOf("חלב 3%", "קמח"), fortnight.map { it.identity.displayName }.toSet())
    }

    @Test fun `trip list skips items on the list, dismissed items, one-offs and guesses`() {
        val purchases = every("חלב 3%", 5, 6) + every("לחם", 3, 8) + purchase("דבש", start.plusDays(20)) +
            every("חמאה", 9, 2) // one interval: a guess
        val today = start.plusDays(29)
        val stats = engine.compute(purchases, today).values
        val milk = ItemIdentity.fromText("חלב 3%").id
        val bread = ItemIdentity.fromText("לחם").id
        val all = SuggestionEngine().forNextTrip(stats, emptySet(), emptyList(), today).map { it.itemId }
        assertTrue(milk in all && bread in all)
        assertTrue(ItemIdentity.fromText("דבש").id !in all)
        assertTrue(ItemIdentity.fromText("חמאה").id !in all)
        val onList = SuggestionEngine().forNextTrip(stats, setOf(milk), emptyList(), today).map { it.itemId }
        assertTrue(milk !in onList && bread in onList)
        val dismissed = SuggestionEngine().forNextTrip(stats, emptySet(), listOf(SuggestionSuppression(bread, today.toEpochDay() + 2)), today)
        assertTrue(dismissed.none { it.itemId == bread } && dismissed.any { it.itemId == milk })
    }

    @Test fun `the most overdue item comes first and the quantity is the typical purchase`() {
        val purchases = every("חלב 3%", 5, 6, Quantity.pieces(2)) + every("לחם", 3, 8)
        val today = start.plusDays(40) // milk due day 30, bread due day 24+3=27... both overdue; bread by more cadences
        val stats = engine.compute(purchases, today).values
        val trip = SuggestionEngine().forNextTrip(stats, emptySet(), emptyList(), today)
        assertEquals("לחם", trip.first().identity.displayName)
        assertEquals(Quantity.pieces(2), trip.first { it.identity.displayName == "חלב 3%" }.quantity)
    }

    private fun line(id: String, source: LineSource, status: LineStatus, removedFrom: LineStatus? = null, recipe: String? = null) = ListLine(
        id = id, itemId = id, name = id, variant = null, category = Category.PRODUCE, quantity = null, note = null,
        status = status, source = source, addedBy = "u1", addedAtMillis = 0, updatedAtMillis = 0,
        recipe = recipe, removedFrom = removedFrom,
    )

    @Test fun `accuracy counts bought, thrown away and waiting suggestions`() {
        val lines = listOf(
            line("a", LineSource.SUGGESTED, LineStatus.BOUGHT),
            line("b", LineSource.QUICK_ADD, LineStatus.REMOVED, removedFrom = LineStatus.BOUGHT), // cleared after buying
            line("c", LineSource.SUGGESTED, LineStatus.REMOVED, removedFrom = LineStatus.NEEDED), // deleted
            line("d", LineSource.SUGGESTED, LineStatus.NEEDED),
            line("e", LineSource.MANUAL, LineStatus.BOUGHT), // typed by a person: not a suggestion
            line("f", LineSource.SUGGESTED, LineStatus.BOUGHT, recipe = "שקשוקה"), // old menu line
        )
        val acc = SuggestionAccuracy.of(lines)
        assertEquals(SuggestionAccuracy(bought = 2, rejected = 1, open = 1), acc)
        assertNull(acc.rate()) // too few decided to say
        assertEquals(2.0 / 3.0, acc.rate(minDecided = 3)!!, 0.001)
    }

    @Test fun `adding an item already on the list merges the quantity`() {
        val line = ListLine(
            id = "l1", itemId = ItemIdentity.fromText("עגבניות").id, name = "עגבניות", variant = null,
            category = Category.PRODUCE, quantity = Quantity(1.0, QuantityUnit.KILOGRAM), note = null,
            status = LineStatus.NEEDED, source = LineSource.MANUAL, addedBy = "u1", addedAtMillis = 0, updatedAtMillis = 0,
        )
        val decision = ListRules.decideAdd(listOf(line), line.itemId, Quantity(500.0, QuantityUnit.GRAM))
        assertEquals(ListRules.AddDecision.Merge("l1", Quantity(1.5, QuantityUnit.KILOGRAM)), decision)
        val bought = line.copy(status = LineStatus.BOUGHT)
        assertTrue(ListRules.decideAdd(listOf(bought), line.itemId, null) is ListRules.AddDecision.New)
    }
}
