package app.spliit.data.remote

import app.spliit.data.model.BalancesResponse
import app.spliit.data.model.ExpenseFormValuesTestSupport
import app.spliit.data.model.GroupsListResponse
import app.spliit.data.model.GroupResponse
import app.spliit.data.model.SplitMode
import app.spliit.domain.CalendarDay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Decodes responses recorded from a real instance, and pins the envelope shape the server
 * expects. Fixtures are the iOS test suite's own recordings, so both apps are checked against
 * the same bytes.
 */
class SuperJsonTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name.json")) {
            "Missing fixture $name"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `a group carries its participants, symbol and ISO code`() {
        val response = SuperJson.decode(GroupResponse.serializer(), fixture("groups-get"))
        val group = checkNotNull(response.group)

        assertEquals("Weekend in Lisbon", group.name)
        assertEquals("€", group.currency)
        assertEquals("EUR", group.currencyCode)
        assertEquals(listOf("Ana", "Bruno", "Chloé"), group.participants.map { it.name })
        assertTrue(group.information?.startsWith("Shared costs") == true)
    }

    @Test
    fun `a group summary decodes an unannotated date and the participant count`() {
        val response = SuperJson.decode(GroupsListResponse.serializer(), fixture("groups-list"))

        assertEquals(3, response.groups.size)
        val lisbon = checkNotNull(response.groups.firstOrNull { it.name == "Weekend in Lisbon" })
        assertEquals(3, lisbon.participantCount)
        assertEquals("€", lisbon.currency)
        assertTrue(lisbon.createdAt.epochSecond > 0)
    }

    @Test
    fun `an expense list decodes amounts, split modes and payers`() {
        val response = SuperJson.decode(
            app.spliit.data.model.ExpensesListResponse.serializer(),
            fixture("expenses-list"),
        )

        assertEquals(4, response.expenses.size)
        assertEquals(false, response.hasMore)

        val taxi = checkNotNull(response.expenses.firstOrNull { it.title == "Airport taxi" })
        assertEquals(4250, taxi.amount)
        assertEquals(SplitMode.EVENLY, taxi.splitMode)
        assertEquals("Ana", taxi.paidBy.name)
        assertEquals(3, taxi.paidFor.size)
        assertEquals("General", taxi.category?.name)
        assertEquals(0, taxi.documentCount)
        assertEquals(false, taxi.isReimbursement)

        val apartment = checkNotNull(response.expenses.firstOrNull { it.title == "Apartment" })
        assertEquals(SplitMode.BY_SHARES, apartment.splitMode)
        assertEquals(200, apartment.paidFor.first { it.participant.name == "Chloé" }.shares)

        val tram = checkNotNull(response.expenses.firstOrNull { it.title == "Tram tickets" })
        assertEquals(SplitMode.BY_AMOUNT, tram.splitMode)
        assertEquals(tram.amount, tram.paidFor.sumOf { it.shares })
    }

    @Test
    fun `expense details decode the editable fields`() {
        val response = SuperJson.decode(
            app.spliit.data.model.ExpenseResponse.serializer(),
            fixture("expenses-get"),
        )
        val expense = response.expense

        assertEquals("Airport taxi", expense.title)
        assertEquals(4250, expense.amount)
        assertEquals(0, expense.categoryId)
        assertEquals("Ana", expense.paidBy.name)
        assertEquals(3, expense.paidFor.size)
        assertEquals(SplitMode.EVENLY, expense.splitMode)
        assertNull(expense.notes)
        assertTrue(expense.documents.isEmpty())
        assertNull(expense.originalAmount)
        assertNull(expense.conversionRate)
    }

    @Test
    fun `balances and reimbursements decode, and balance totals cancel out`() {
        val response = SuperJson.decode(BalancesResponse.serializer(), fixture("balances-list"))

        assertEquals(3, response.balances.size)
        assertEquals(0, response.balances.values.sumOf { it.total })

        assertEquals(2, response.reimbursements.size)
        val reimbursed = response.reimbursements.sumOf { it.amount }
        val owed = response.balances.values.filter { it.total > 0 }.sumOf { it.total }
        assertEquals(owed, reimbursed)
    }

    @Test
    fun `stats answer for a participant with what they paid and what they owe`() {
        val response = SuperJson.decode(
            app.spliit.data.model.GroupStatsResponse.serializer(),
            fixture("groups-stats-get"),
        )

        assertEquals(4250 + 9630 + 48000 + 1800, response.totalGroupSpendings)
        assertEquals(4250 + 1800, response.totalParticipantSpendings)
        assertEquals(17627.0, checkNotNull(response.totalParticipantShare), 0.0)
    }

    @Test
    fun `an activity type the client does not know does not fail the log`() {
        val response = SuperJson.decode(
            app.spliit.data.model.ActivitiesListResponse.serializer(),
            fixture("activities-list"),
        )

        assertTrue(response.activities.isNotEmpty())
        assertTrue(response.activities.all { it.activityType.isRecognised })
        val renamed = response.activities.first { it.expenseId != null }
        assertEquals("Airport taxi", renamed.title)
        assertTrue(renamed.expenseStillExists)
    }

    @Test
    fun `categories decode with their grouping`() {
        val response = SuperJson.decode(
            app.spliit.data.model.CategoriesResponse.serializer(),
            fixture("categories-list"),
        )

        assertTrue(response.categories.isNotEmpty())
        val general = response.categories.first { it.name == "General" }
        assertEquals("Uncategorized", general.grouping)
        assertEquals(0, general.id)
    }

    // MARK: - The request envelope

    @Test
    fun `the expense form's date is annotated as a Date, at its full path`() {
        val values = ExpenseFormValuesTestSupport.simple(
            expenseDate = CalendarDay.wire(LocalDate.of(2026, 8, 28)),
        )
        val procedure = SpliitApi.createExpense(groupId = "g1", values = values)
        val envelope = checkNotNull(procedure.input).jsonObject

        val annotated = checkNotNull(envelope["meta"]?.jsonObject?.get("values")?.jsonObject)
        assertTrue(
            "the date's path is annotated, found $annotated",
            annotated.containsKey("expenseFormValues.expenseDate"),
        )
        assertEquals("[\"Date\"]", annotated["expenseFormValues.expenseDate"].toString())
        val date = envelope["json"]?.jsonObject?.get("expenseFormValues")?.jsonObject
            ?.get("expenseDate")?.jsonPrimitive?.content
        assertEquals("2026-08-28T00:00:00Z", date)
    }

    @Test
    fun `originalCurrency is written even when the expense is in the group's currency`() {
        val procedure = SpliitApi.createExpense(
            groupId = "g1",
            values = ExpenseFormValuesTestSupport.simple(
                expenseDate = CalendarDay.wire(LocalDate.of(2026, 8, 28)),
            ),
        )
        val form = checkNotNull(procedure.input).jsonObject["json"]?.jsonObject
            ?.get("expenseFormValues")?.jsonObject

        assertNotNull(form)
        assertTrue("originalCurrency must be present as null", form!!.containsKey("originalCurrency"))
        assertEquals("null", form["originalCurrency"].toString())
        // The other two are omitted rather than nulled: the server rejects null for them.
        assertTrue(!form.containsKey("originalAmount"))
        assertTrue(!form.containsKey("conversionRate"))
    }

    @Test
    fun `a query carries its input as a superjson envelope with no annotations`() {
        val procedure = SpliitApi.expenses(groupId = "g1", cursor = 20, limit = 20)
        val envelope = checkNotNull(procedure.input) as JsonObject

        assertEquals("g1", envelope["json"]?.jsonObject?.get("groupId")?.jsonPrimitive?.content)
        assertEquals("20", envelope["json"]?.jsonObject?.get("cursor")?.jsonPrimitive?.content)
        assertNull(envelope["meta"])
    }
}
