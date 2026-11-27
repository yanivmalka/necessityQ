package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** The first-list survey: who lives at home, the proposed amounts (history, model, table), and how the answers are used. */
class FirstListSurveyTest {
    private val today = LocalDate.of(2026, 11, 27)
    private val family = HouseholdMakeup(listOf(40, 38, 9, 4))
    private fun item(name: String) = FirstListSurvey.item(name)!!

    // ---------- who lives at home ----------

    @Test fun `every survey item is a catalogue item`() {
        FirstListSurvey.items.forEach { assertEquals(it.name, Catalog.lookup(it.name)?.name) }
        assertEquals(FirstListSurvey.items.size, FirstListSurvey.items.map { it.name }.toSet().size)
    }

    @Test fun `the makeup comes from the profiles, ages only, unknown age is an adult`() {
        val members = listOf(
            FamilyMember("a", "אבא", birthYear = 1986),
            FamilyMember("b", "בלי גיל"),
            FamilyMember("c", "קטן", birthYear = 2025, birthMonth = 3),
        )
        val makeup = HouseholdMakeup.of(members, today)
        assertEquals(listOf(40, HouseholdMakeup.ADULT_AGE, 1), makeup.ages)
        assertEquals(1, makeup.babies)
        assertEquals(HouseholdSize.people(members, today), makeup.people, 1e-9)
    }

    @Test fun `without profiles the survey asks by age group`() {
        val makeup = HouseholdMakeup.ofGroups(mapOf(AgeGroup.ADULTS to 2, AgeGroup.KIDS to 1, AgeGroup.BABIES to 1))
        assertEquals(listOf(30, 30, 9, 1), makeup.ages)
        assertEquals(2.0 + 0.75 + 0.25, makeup.people, 1e-9)
        assertEquals("2 מבוגרים, 2 ילדים בגילים 9 ו1", makeup.describe())
        assertEquals("מבוגר אחד", HouseholdMakeup(listOf(30)).describe())
    }

    @Test fun `nappies are asked only where there is a small child`() {
        assertFalse(FirstListSurvey.itemsFor(family).any { it.name == "חיתולים" })
        assertTrue(FirstListSurvey.itemsFor(HouseholdMakeup(listOf(30, 1))).any { it.name == "חיתולים" })
    }

    // ---------- proposed amounts ----------

    @Test fun `the table gives a whole step, more for a bigger home`() {
        val milk = item("חלב")
        val one = FirstListSurvey.tableEstimate(milk, HouseholdMakeup(listOf(30)))
        val four = FirstListSurvey.tableEstimate(milk, family)
        assertEquals(1.0, one, 0.0)
        assertTrue(four > one)
        assertEquals(0.0, four % milk.step, 1e-9)
        assertEquals(0.0, FirstListSurvey.tableEstimate(milk, HouseholdMakeup(emptyList())), 0.0)
    }

    @Test fun `the model's amount is kept only within reason`() {
        val milk = item("חלב")
        assertEquals(4.0, FirstListSurvey.acceptModel(milk, 4.2, 3.0)!!, 0.0)
        assertNull("forty litres a week", FirstListSurvey.acceptModel(milk, 40.0, 3.0))
        assertNull("no milk for a family", FirstListSurvey.acceptModel(milk, 0.0, 3.0))
        assertNull(FirstListSurvey.acceptModel(milk, -1.0, 3.0))
        assertNull(FirstListSurvey.acceptModel(milk, Double.NaN, 3.0))
        assertNull(FirstListSurvey.acceptModel(milk, null, 3.0))
    }

    @Test fun `history first, then the model, then the table`() {
        val bought = ConsumptionStatsEngine(ZoneOffset.UTC).compute(
            (0 until 4).map { i ->
                val day = today.minusDays(7L * (4 - i))
                val id = ItemIdentity.fromText("חלב")
                Purchase(
                    id = "p$i", itemId = id.id, name = id.name, variant = null, category = id.category,
                    quantity = Quantity(2.0, QuantityUnit.LITER), boughtAtMillis = day.atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
                    boughtBy = "u", lineId = null,
                )
            },
            today,
        ).values
        val estimates = FirstListSurvey.estimates(family, mapOf("חלב" to 9.0, "לחם" to 5.0, "ביצים" to 500.0), bought)
        assertEquals(SurveyEstimate(2.0, SurveyEstimate.Source.HISTORY), estimates["חלב"])
        assertEquals(SurveyEstimate(5.0, SurveyEstimate.Source.MODEL), estimates["לחם"])
        assertEquals(SurveyEstimate.Source.TABLE, estimates["ביצים"]!!.source)
        assertEquals(SurveyEstimate.Source.TABLE, FirstListSurvey.estimates(family, null)["לחם"]!!.source)
    }

    @Test fun `an answer far from the usual is pointed out`() {
        val milk = item("חלב")
        assertNotNull(FirstListSurvey.check(milk, 20.0, 3.0))
        assertNotNull(FirstListSurvey.check(milk, 1.0, 6.0))
        assertNull(FirstListSurvey.check(milk, 4.0, 3.0))
        assertNull("not buying is an answer", FirstListSurvey.check(milk, 0.0, 3.0))
    }

    @Test fun `steps never go below not buying`() {
        val milk = item("חלב")
        assertEquals(0.0, FirstListSurvey.step(milk, 1.0, -1), 0.0)
        assertEquals(0.0, FirstListSurvey.step(milk, 0.0, -1), 0.0)
        assertEquals(2.0, FirstListSurvey.step(milk, 1.0, +1), 0.0)
        assertEquals("לא קונים", FirstListSurvey.format(milk, 0.0))
    }

    // ---------- the answers ----------

    private val answers = FirstListSurvey.baseline(mapOf("חלב" to 3.0, "אורז" to 2.0, "קפה" to 0.0, "לא בשאלון" to 4.0), null, 1L)

    @Test fun `answers keep their unit and period, unknown names are dropped`() {
        assertEquals(BaselineAmount(3.0, QuantityUnit.LITER, 7), answers.amountFor("חלב"))
        assertEquals(BaselineAmount(2.0, QuantityUnit.KILOGRAM, 30), answers.amountFor("אורז"))
        assertNull(answers.amountFor("לא בשאלון"))
        assertNull(answers.amountFor("קפה")!!.quantity)
    }

    @Test fun `the first list is what the household said it buys`() {
        val starter = TripSizer(emptyList(), 0.0, answers).starter(emptySet())
        assertEquals(listOf("אורז", "חלב").sorted(), starter.map { it.identity.name }.sorted())
        val milk = starter.first { it.identity.name == "חלב" }
        assertEquals(Quantity(3.0, QuantityUnit.LITER), milk.quantity)
        assertEquals(TripSizer.SURVEY_STARTER_REASON, milk.reason)
        assertTrue(TripSizer.isStarter(milk.reason))
        val rice = starter.first { it.identity.name == "אורז" }
        assertEquals("לפי השאלון · לחודש", rice.quantityNote)
    }

    @Test fun `the answer sizes an item the household has not bought, for as long as a purchase lasts`() {
        val sizer = TripSizer(emptyList(), 4.0, answers)
        val milk = ItemIdentity.fromText("חלב")
        assertEquals(Quantity(3.0, QuantityUnit.LITER), sizer.size(milk, null).quantity)
        assertEquals("לפי השאלון", sizer.size(milk, null).note)
        // an item the household said it does not buy gets no amount from the table either
        assertNull(sizer.size(ItemIdentity.fromText("קפה"), null).quantity)
    }

    @Test fun `what the household really bought wins over the answer`() {
        val bought = ConsumptionStatsEngine(ZoneOffset.UTC).compute(
            (0 until 3).map { i ->
                val id = ItemIdentity.fromText("חלב")
                Purchase(
                    id = "p$i", itemId = id.id, name = id.name, variant = null, category = id.category,
                    quantity = Quantity(1.0, QuantityUnit.LITER),
                    boughtAtMillis = today.minusDays(7L * (3 - i)).atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
                    boughtBy = "u", lineId = null,
                )
            },
            today,
        ).values.single()
        assertEquals(Quantity(1.0, QuantityUnit.LITER), TripSizer(listOf(bought), 4.0, answers).size(bought.identity, bought).quantity)
    }

    @Test fun `the household's size comes from the profiles, or from the survey without them`() {
        val told = answers.copy(makeup = HouseholdMakeup(listOf(30, 30)))
        assertEquals(2.0, FirstListSurvey.people(MealData(baseline = told), today), 1e-9)
        assertEquals(1.0, FirstListSurvey.people(MealData(members = listOf(FamilyMember("a", "א")), baseline = told), today), 1e-9)
        assertTrue(FirstListSurvey.shouldAsk(MealData()))
        assertFalse(FirstListSurvey.shouldAsk(MealData(baseline = HouseholdBaseline(1L, skipped = true))))
    }
}
