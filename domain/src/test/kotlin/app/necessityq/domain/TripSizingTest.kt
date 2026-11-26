package app.necessityq.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Amounts of proposed lines: what was really bought, the size of the household, a week's need, what a shop sells. */
class TripSizingTest {
    private val zone = ZoneOffset.UTC
    private val engine = ConsumptionStatsEngine(zone)
    private val start = LocalDate.of(2026, 1, 1)

    private fun purchase(item: String, day: LocalDate, qty: Quantity? = Quantity.pieces(1), people: Double? = null): Purchase {
        val identity = ItemIdentity.fromText(item)
        return Purchase(
            id = "p_${identity.id}_$day", itemId = identity.id, name = identity.name, variant = identity.variant,
            category = identity.category, quantity = qty,
            boughtAtMillis = day.atTime(10, 0).toInstant(zone).toEpochMilli(), boughtBy = "u", lineId = null,
            householdPeople = people,
        )
    }

    private fun every(item: String, days: Int, times: Int, qty: (Int) -> Quantity?, people: Double? = null) =
        (0 until times).map { purchase(item, start.plusDays((it * days).toLong()), qty(it), people) }

    private fun stats(purchases: List<Purchase>, today: LocalDate) = engine.compute(purchases, today).values.single()

    // ---------- reported: "1.5 ק"ג סוכר", "1.5 בקבוקי מוסקטו" ----------

    @Test fun `the usual amount is one the household really bought, never half a bottle`() {
        val wine = stats(every("מוסקטו", 14, 4, { if (it % 2 == 0) Quantity.pieces(1) else Quantity.pieces(2) }), start.plusDays(42))
        assertEquals(Quantity.pieces(1), wine.typicalQuantity)
        val sugar = stats(every("סוכר", 30, 2, { Quantity((it + 1).toDouble(), QuantityUnit.KILOGRAM) }), start.plusDays(30))
        assertEquals(Quantity(1.0, QuantityUnit.KILOGRAM), sugar.typicalQuantity)
    }

    @Test fun `a computed amount is rounded to what a shop sells`() {
        assertEquals(Quantity(1.0, QuantityUnit.KILOGRAM), TripSizer.purchasable("סוכר", null, Quantity(1.2, QuantityUnit.KILOGRAM)))
        assertEquals(Quantity(2.0, QuantityUnit.KILOGRAM), TripSizer.purchasable("סוכר", null, Quantity(1.6, QuantityUnit.KILOGRAM)))
        assertEquals(Quantity.pieces(2), TripSizer.purchasable("מוסקטו", null, Quantity.pieces(1.5)))
        assertEquals(Quantity.pieces(1), TripSizer.purchasable("מוסקטו", null, Quantity.pieces(0.4)))
        assertEquals(Quantity.pieces(12), TripSizer.purchasable("ביצים", null, Quantity.pieces(10)))
        assertEquals(Quantity(1.25, QuantityUnit.KILOGRAM), TripSizer.purchasable("עגבניות", null, Quantity(1.3, QuantityUnit.KILOGRAM)))
    }

    // ---------- the size of the household ----------

    @Test fun `the usual amount follows a household that grew since`() {
        // bought 1 L a week when the household was 2; it is 4 now
        val milk = stats(every("חלב", 7, 5, { Quantity(1.0, QuantityUnit.LITER) }, people = 2.0), start.plusDays(28))
        assertEquals(2.0, milk.typicalPeople!!, 0.0)
        val sized = TripSizer(listOf(milk), 4.0).size(milk.identity, milk)
        assertEquals(Quantity(2.0, QuantityUnit.LITER), sized.quantity)
        assertEquals("מותאם ל־4 נפשות", sized.note)
        // the same size, or not known then: the household's own amount as it is
        assertEquals(Quantity(1.0, QuantityUnit.LITER), TripSizer(listOf(milk), 2.2).size(milk.identity, milk).quantity)
        val unknown = stats(every("חלב", 7, 5, { Quantity(1.0, QuantityUnit.LITER) }), start.plusDays(28))
        assertNull(unknown.typicalPeople)
        assertEquals(Quantity(1.0, QuantityUnit.LITER), TripSizer(listOf(unknown), 4.0).size(unknown.identity, unknown).quantity)
    }

    @Test fun `with no amount on record the table gives one for the household`() {
        val eggs = stats(every("ביצים", 7, 5, { null }), start.plusDays(28))
        val two = TripSizer(listOf(eggs), 2.0).size(eggs.identity, eggs)
        val five = TripSizer(listOf(eggs), 5.0).size(eggs.identity, eggs)
        assertEquals(Quantity.pieces(12), two.quantity)
        assertEquals(Quantity.pieces(24), five.quantity)
        assertEquals("לפי 5 נפשות", five.note)
        val bread = stats(every("לחם", 7, 5, { null }), start.plusDays(28))
        assertEquals("הערכה לפי 3 נפשות", TripSizer(listOf(bread), 3.0).size(bread.identity, bread).note)
    }

    @Test fun `a week's list buys an item as many times as it runs out in a week`() {
        val bread = stats(every("לחם", 3, 8, { Quantity.pieces(1) }), start.plusDays(21))
        val sized = TripSizer(listOf(bread), 0.0).size(bread.identity, bread, horizonDays = 7)
        assertEquals(Quantity.pieces(2), sized.quantity)
        assertEquals("לשבוע: פעמיים", sized.note)
        // a single purchase (a card) stays one
        assertEquals(Quantity.pieces(1), TripSizer(listOf(bread), 0.0).size(bread.identity, bread).quantity)
        assertEquals(1, TripSizer.timesIn(7, 10.0))
        assertEquals(4, TripSizer.timesIn(7, 1.0))
    }

    @Test fun `a new household gets a first list from its size`() {
        val list = TripSizer(emptyList(), 4.0).starter(emptySet())
        assertTrue(list.size >= 5)
        val milk = list.first { it.identity.name == "חלב" }
        assertEquals(Quantity(4.0, QuantityUnit.LITER), milk.quantity)
        assertEquals(TripSizer.STARTER_REASON, milk.reason)
        assertTrue(TripSizer(emptyList(), 0.0).starter(emptySet()).isEmpty()) // size unknown: nothing to go by
    }

    // ---------- wired through the service ----------

    private fun service(repo: InMemoryShoppingRepository, now: () -> Long): ShoppingService {
        var n = 0
        return ShoppingService(
            repo, object : Clock { override fun nowMillis() = now() }, object : IdGenerator { override fun newId() = "id${++n}" }, zone,
        )
    }

    @Test fun `suggestions on the list screen carry amounts for the household, and purchases record its size`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val today = start.plusDays(30) // the last of the weekly purchases was two days ago
        val now = today.atTime(12, 0).toInstant(zone).toEpochMilli()
        every("חלב", 7, 5, { Quantity(1.0, QuantityUnit.LITER) }, people = 2.0).forEach { repo.addPurchase("h", it) }
        val service = service(repo) { now }

        // the family profiles are not read yet: the household's own amount
        assertEquals(Quantity(1.0, QuantityUnit.LITER), service.snapshot("h").first().tripList.single().quantity)
        service.setHouseholdPeople("h", 4.0)
        val snap = service.snapshot("h").first()
        assertEquals(4.0, snap.people, 0.0)
        assertEquals(Quantity(2.0, QuantityUnit.LITER), snap.tripList.single().quantity)
        assertEquals(Quantity(2.0, QuantityUnit.LITER), snap.frequent.single().quantity) // the quick-add chip too

        service.acceptSuggestion("h", "u", snap.tripList.single(), snap.lines)
        val line = service.snapshot("h").first().lines.single()
        assertEquals(Quantity(2.0, QuantityUnit.LITER), line.quantity)
        service.checkOff("h", "u", line)
        assertEquals(4.0, repo.purchases("h").first().maxBy { it.boughtAtMillis }.householdPeople!!, 0.0)
    }

    @Test fun `a household with no history sees a first list`() = runBlocking {
        val service = service(InMemoryShoppingRepository()) { 1000L }
        assertTrue(service.snapshot("h").first().tripList.isEmpty())
        service.setHouseholdPeople("h", 3.0)
        assertTrue(service.snapshot("h").first().tripList.all { it.reason == TripSizer.STARTER_REASON })
        assertTrue(service.snapshot("h").first().tripList.size >= 5)
    }

    // ---------- the week's card and the list from past purchases are the same list ----------

    @Test fun `the list from past purchases proposes the week's list, with the same amounts`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val today = start.plusDays(28)
        val now = today.atTime(12, 0).toInstant(zone).toEpochMilli()
        (every("חלב", 7, 5, { Quantity(1.0, QuantityUnit.LITER) }, people = 2.0) + every("לחם", 3, 10, { Quantity.pieces(1) }))
            .forEach { repo.addPurchase("h", it) }
        val service = service(repo) { now }
        service.setHouseholdPeople("h", 4.0)
        val snap = service.snapshot("h").first()
        val proposal = ListGenerator.propose(
            GenerateMode.DUE, snap.stats.values, repo.purchases("h").first(), snap.lines, today, zone = zone, people = 4.0,
        )
        val preselected = proposal.filter { it.preselected }
        assertEquals(snap.tripList.map { it.itemId }.toSet(), preselected.map { it.identity.id }.toSet())
        assertEquals(
            snap.tripList.associate { it.itemId to it.quantity },
            preselected.associate { it.identity.id to it.quantity },
        )
        assertEquals(Quantity.pieces(2), preselected.first { it.identity.name == "לחם" }.quantity)
    }

    @Test fun `what is at home is not labelled as turned down`() {
        val milk = stats(every("חלב", 7, 5, { Quantity(1.0, QuantityUnit.LITER) }), start.plusDays(28))
        val atHome = ListGenerator.propose(
            GenerateMode.ROUTINE, listOf(milk), emptyList(), emptyList(), start.plusDays(28), zone = zone,
            suppressed = setOf(milk.itemId), atHome = setOf(milk.itemId),
        ).single()
        assertFalse(atHome.preselected)
        assertTrue(atHome.reason.endsWith("יש בבית"))
        val dismissed = ListGenerator.propose(
            GenerateMode.ROUTINE, listOf(milk), emptyList(), emptyList(), start.plusDays(28), zone = zone, suppressed = setOf(milk.itemId),
        ).single()
        assertTrue(dismissed.reason.endsWith("דחיתם לאחרונה"))
    }

    @Test fun `having it at home is stored as such`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = service(repo) { 1000L }
        val s = Suggestion("x", ItemIdentity.fromText("חלב"), SuggestionKind.RUNNING_LOW, null, 1.0, "")
        service.haveAtHome("h", s, null)
        assertTrue(repo.suppressions("h").first().single().atHome)
        assertEquals(setOf("x"), service.snapshot("h").first().atHome)
    }

    // ---------- learning from what is taken off ----------

    @Test fun `taking an item off the generated list adds up with earlier rejections`() = runBlocking {
        val repo = InMemoryShoppingRepository()
        val service = service(repo) { 1000L }
        val item = GeneratedItem(ItemIdentity.fromText("חלב"), null, "")
        service.rejectGenerated("h", listOf(item), emptyMap())
        assertEquals(1, repo.suppressions("h").first().single().dismissals)
        service.rejectGenerated("h", listOf(item), emptyMap(), service.snapshot("h").first().rejections)
        assertEquals(2, repo.suppressions("h").first().single().dismissals)
        service.rejectGenerated("h", listOf(item), emptyMap(), service.snapshot("h").first().rejections)
        assertEquals(3, service.snapshot("h").first().rejections[item.identity.id])
    }

    @Test fun `accuracy counts the list from past purchases, and clearing the list is no rejection`() {
        fun line(id: String, source: LineSource, status: LineStatus, removedFrom: LineStatus? = null, reason: RemoveReason? = null) = ListLine(
            id = id, itemId = id, name = id, variant = null, category = Category.PRODUCE, quantity = null, note = null,
            status = status, source = source, addedBy = "u", addedAtMillis = 0, updatedAtMillis = 0,
            removedFrom = removedFrom, removedReason = reason, removedAtMillis = 10,
        )
        val lines = listOf(
            line("a", LineSource.AUTO, LineStatus.BOUGHT),
            line("b", LineSource.AUTO, LineStatus.REMOVED, LineStatus.NEEDED, RemoveReason.DELETED),
            line("c", LineSource.SUGGESTED, LineStatus.REMOVED, LineStatus.NEEDED, RemoveReason.RESET),
        )
        assertEquals(SuggestionAccuracy(bought = 1, rejected = 1, open = 0), SuggestionAccuracy.of(lines))
        assertEquals(mapOf("b" to 1), SuggestionAccuracy.rejectionsByItem(lines, 0))
    }
}
