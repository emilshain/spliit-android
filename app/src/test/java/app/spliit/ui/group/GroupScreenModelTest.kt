package app.spliit.ui.group

import app.spliit.AppContainer
import app.spliit.data.local.PendingExpense
import app.spliit.ui.FakeSpliitServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The model's Phase 4 behaviour: which procedure the totals are asked for, the paging sweep behind
 * the category breakdown, the debounce in front of a search, and the pull that reloads rather than
 * pages.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroupScreenModelTest {

    private fun server(): FakeSpliitServer = FakeSpliitServer().apply {
        answer(
            "groups.get",
            """{"group":{"id":"g1","name":"Flat","currency":"€","currencyCode":"EUR",
               "createdAt":"2026-01-05T10:00:00Z",
               "participants":[{"id":"p1","name":"Ana"},{"id":"p2","name":"Bruno"}]}}""",
        )
        answer("groups.balances.list", """{"balances":{},"reimbursements":[]}""")
        answer("categories.list", """{"categories":[]}""")
        answer("groups.expenses.list", """{"expenses":[],"hasMore":false,"nextCursor":0}""")
    }

    // MARK: - Totals

    /**
     * The instance most people are on has dropped `groups.stats.get`, so the overview is asked
     * first; a self-hosted image answers `NOT_FOUND` and the old name is the one that works.
     */
    @Test
    fun `the totals fall back to the old procedure name`() = runBlocking {
        val server = server()
        server.answer(
            "groups.stats.get",
            """{"totalGroupSpendings":63680,"totalParticipantSpendings":42100,
               "totalParticipantShare":17627}""",
        )

        val model = startedModel(server)
        model.loadStats("p1")

        assertEquals(63680, model.stats?.totalGroupSpendings)
        assertEquals(17627.0, model.stats?.totalParticipantShare ?: 0.0, 0.0)
        assertEquals(
            listOf("groups.stats.overview", "groups.stats.get"),
            server.calls().filter { it.contains("stats") },
        )
        assertFalse(model.statsUnavailable)
        assertNull(model.statsFailure)
    }

    @Test
    fun `an instance with the overview is asked for it, and nothing else`() = runBlocking {
        val server = server()
        server.answer("groups.stats.overview", """{"totalGroupSpendings":5000}""")

        val model = startedModel(server)
        model.loadStats(null)

        assertEquals(5000, model.stats?.totalGroupSpendings)
        assertEquals(
            listOf("groups.stats.overview"),
            server.calls().filter { it.contains("stats") },
        )
    }

    /** Neither name means no totals — which is not a failure, and is not retried. */
    @Test
    fun `an instance with either name missing reports no totals rather than an error`() =
        runBlocking {
            val server = server()

            val model = startedModel(server)
            model.loadStats("p1")

            assertTrue(model.statsUnavailable)
            assertNull(model.stats)
            assertNull(model.statsFailure)
            assertFalse(model.didFailToLoadStats)
        }

    /**
     * A server having a bad day is not a server without totals, so an ordinary failure must not
     * fall through to the other name and come back saying the feature is missing.
     */
    @Test
    fun `a failure that is not a missing procedure is not retried under the other name`() =
        runBlocking {
            val server = server()
            server.answer("groups.stats.get", """{"totalGroupSpendings":5000}""")
            server.fail("groups.stats.overview", 500)

            val model = startedModel(server)
            model.loadStats("p1")

            assertFalse(model.statsUnavailable)
            assertTrue(model.didFailToLoadStats)
            assertEquals(
                listOf("groups.stats.overview"),
                server.calls().filter { it.contains("stats") },
            )
        }

    // MARK: - The category sweep

    /** The sweep walks every page and folds it, so the breakdown can reconcile with the total. */
    @Test
    fun `the category sweep folds every page`() = runBlocking {
        val server = server()
        server.answer("groups.stats.overview", """{"totalGroupSpendings":3500}""")
        server.answerMatching("groups.expenses.list") { request ->
            val cursor = request.url.queryParameter("input").orEmpty()
            if (cursor.contains("\"cursor\":0")) {
                """{"expenses":[${expense("e1", "Market", 2000, 5, "Groceries")},
                   ${expense("e2", "Airport", 1000, 12, "Taxi")},
                   ${expense("e3", "Settling up", 900, 12, "Taxi", reimbursement = true)}],
                   "hasMore":true,"nextCursor":100}"""
            } else {
                """{"expenses":[${expense("e4", "Corner shop", 500, 5, "Groceries")}],
                   "hasMore":false,"nextCursor":200}"""
            }
        }

        val model = startedModel(server)
        model.loadStats(null)
        model.refreshCategorySpending()

        assertEquals(listOf(5, 12), model.categorySpending.map { it.categoryId })
        assertEquals(listOf(2500, 1000), model.categorySpending.map { it.total })
        assertTrue(model.categorySpendingReconciles)
    }

    // MARK: - Search

    /**
     * A keystroke cancels the search the keystroke before it started, and a cancelled search must
     * never reach the server — otherwise every character typed is a request.
     */
    @Test
    fun `a keystroke cancels the search before it is sent`() = runBlocking {
        val server = server()
        server.answerMatching("groups.expenses.list") { request ->
            val input = request.url.queryParameter("input").orEmpty()
            if (input.contains("\"filter\":\"Dinner\"")) {
                """{"expenses":[${expense("e1", "Dinner", 3000, null, null)}],
                   "hasMore":false,"nextCursor":20}"""
            } else {
                """{"expenses":[],"hasMore":false,"nextCursor":0}"""
            }
        }

        val model = startedModel(server)
        val before = server.callsTo("groups.expenses.list")

        val typing = launch { model.search("Din") }
        delay(10)
        typing.cancel()
        model.search("Dinner")

        assertEquals("Dinner", model.filter)
        assertEquals(before + 1, server.callsTo("groups.expenses.list"))
        assertEquals(
            listOf("Dinner"),
            model.searchResults.map { it.title },
        )
    }

    // MARK: - Pull to refresh

    /**
     * A pull is not a page: it asks for the first page again and replaces the list, rather than
     * appending the next one.
     */
    @Test
    fun `pulling reloads the first page instead of paging`() = runBlocking {
        val server = server()
        server.answerMatching("groups.expenses.list") { request ->
            val input = request.url.queryParameter("input").orEmpty()
            if (input.contains("\"cursor\":0")) {
                """{"expenses":[${expense("e1", "Dinner", 3000, null, null)}],
                   "hasMore":true,"nextCursor":20}"""
            } else {
                """{"expenses":[${expense("e2", "Rent", 90000, null, null)}],
                   "hasMore":false,"nextCursor":40}"""
            }
        }

        val model = startedModel(server)
        await { model.expenses.size == 1 }

        model.loadNextPage()
        await { model.expenses.size == 2 }

        model.refresh()
        await { !model.isRefreshing && server.callsTo("groups.expenses.list") == 3 }

        assertEquals(listOf("Dinner"), model.expenses.map { it.title })
        assertTrue(model.hasMoreExpenses)
    }

    // MARK: - Deleting while offline

    /**
     * A delete of a row that only exists in the outbox is this phone forgetting it. Sending a
     * delete would ask the server to remove an expense it has never had, so the request would
     * fail and the waiting create would stay where it was.
     */
    @Test
    fun `deleting a waiting expense forgets it instead of deleting it on the server`() = runBlocking {
        val server = server()
        // Nothing can be sent, so the create stays in the outbox and the row is drawn from it.
        server.offline("groups.expenses.create")
        val container = server.container()
        container.outbox.put(pendingCreate("w1", "Taxi"))

        val model = startedModel(server, container)
        await { model.sections.flatMap { it.expenses }.any { it.id == "w1" } }

        model.requestDelete(model.sections.flatMap { it.expenses }.first { it.id == "w1" })
        model.commitPendingDeletion()

        withTimeout(5_000) {
            while (container.outbox.forGroup("g1").isNotEmpty()) delay(10)
        }
        assertEquals(0, server.callsTo("groups.expenses.delete"))
    }

    /**
     * A search can find an expense the list has not loaded, and undo has to put it back where it
     * came from: the results, not a list that never held it.
     */
    @Test
    fun `undo after deleting a search result leaves the expense list alone`() = runBlocking {
        val server = server()
        server.answerMatching("groups.expenses.list") { request ->
            val input = request.url.queryParameter("input").orEmpty()
            if (input.contains("\"filter\":\"Dinner\"")) {
                """{"expenses":[${expense("e-far", "Dinner", 3000, null, null)}],
                   "hasMore":false,"nextCursor":0}"""
            } else {
                """{"expenses":[${expense("e-rent", "Rent", 90000, null, null)}],
                   "hasMore":false,"nextCursor":0}"""
            }
        }

        val model = startedModel(server)
        await { model.expenses.size == 1 }
        model.search("Dinner")
        await { model.searchResults.size == 1 }

        model.requestDelete(model.searchResults.single())
        model.undoDelete()

        assertEquals(listOf("Rent"), model.expenses.map { it.title })
        assertEquals(listOf("Dinner"), model.searchResults.map { it.title })
    }

    // MARK: - Helpers

    private suspend fun CoroutineScope.startedModel(
        server: FakeSpliitServer,
        container: AppContainer = server.container(),
    ): GroupScreenModel {
        val model = GroupScreenModel(
            container = container,
            groupId = "g1",
            scope = this,
            searchDebounceMillis = 40,
        )
        model.start()
        await { model.group != null }
        return model
    }

    /**
     * A change that could not be sent, as the outbox holds it.
     *
     * It carries the request it would send, as a real one does: without it the model would drop
     * the row as unusable rather than try to send it, which is a different path.
     */
    private fun pendingCreate(id: String, title: String) = PendingExpense(
        key = id,
        groupId = "g1",
        kind = PendingExpense.Kind.CREATE,
        valuesJson = """{"title":"$title","amount":1000}""",
        title = title,
        amount = 1000,
        paidById = "p1",
        paidFor = listOf(PendingExpense.PaidFor("p1", 10000)),
        queuedAtEpochMillis = System.currentTimeMillis(),
    )

    private suspend fun await(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) delay(10)
        }
    }

    private fun expense(
        id: String,
        title: String,
        amount: Int,
        categoryId: Int?,
        categoryName: String?,
        reimbursement: Boolean = false,
    ): String {
        val category = if (categoryId != null && categoryName != null) {
            """{"id":$categoryId,"grouping":"Uncategorised","name":"$categoryName"}"""
        } else {
            "null"
        }
        return """
            {"id":"$id","title":"$title","amount":$amount,
             "createdAt":"2026-08-13T09:00:00Z","expenseDate":"2026-08-13T00:00:00Z",
             "isReimbursement":$reimbursement,"splitMode":"EVENLY",
             "category":$category,
             "paidBy":{"id":"p1","name":"Ana"},
             "paidFor":[{"participant":{"id":"p1","name":"Ana"},"shares":100}]}
        """.trimIndent()
    }
}
