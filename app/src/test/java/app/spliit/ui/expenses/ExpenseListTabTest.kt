package app.spliit.ui.expenses

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import app.spliit.data.local.RecentGroup
import app.spliit.domain.CalendarDay
import app.spliit.domain.DateHeading
import app.spliit.ui.FakeSpliitServer
import app.spliit.ui.group.GroupScreen
import app.spliit.ui.seedGroups
import app.spliit.ui.theme.SpliitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The expenses tab: the days the list is divided into, and the second page that only arrives
 * when the footer asks for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ExpenseListTabTest {

    @get:Rule
    val compose = createComposeRule()

    private val today: LocalDate = LocalDate.now()

    @Test
    fun theListIsBucketedAndTheNextPageArrivesAtTheEnd() {
        val server = FakeSpliitServer()
        answerGroup(server)
        server.answer("groups.balances.list", """{"balances":{},"reimbursements":[]}""")
        server.answer("categories.list", """{"categories":[]}""")

        // The first page says there is more; the cursor is what asks for the second.
        server.answerMatching("groups.expenses.list") { request ->
            val cursor = request.url.queryParameter("input").orEmpty()
            if (cursor.contains("\"cursor\":0")) {
                """{"expenses":[${expense("e1", "Dinner", 3000, today)}],"hasMore":true,"nextCursor":20}"""
            } else {
                """{"expenses":[${expense("e2", "Rent", 90000, today.minusMonths(3))}],"hasMore":false,"nextCursor":40}"""
            }
        }

        val container = server.container()
        seedGroups(
            container.recentGroups,
            RecentGroup(groupId = "g1", groupName = "Flat", instanceURL = "https://spliit.app"),
        )

        compose.setContent {
            SpliitTheme { GroupScreen(container = container, groupId = "g1", onBack = {}) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Dinner").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(DateHeading.of(today)).assertIsDisplayed()

        // The paging footer composes once the list is scrolled to its end; Robolectric lays the
        // whole list out, so the second page follows on its own.
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Rent").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(DateHeading.of(today.minusMonths(3))).assertIsDisplayed()
    }

    private fun answerGroup(server: FakeSpliitServer) {
        server.answer(
            "groups.get",
            """{"group":{"id":"g1","name":"Flat","currency":"€","currencyCode":"EUR",
               "createdAt":"2026-01-05T10:00:00Z",
               "participants":[{"id":"p1","name":"Ana"},{"id":"p2","name":"Bruno"}]}}""",
        )
    }

    private fun expense(id: String, title: String, amount: Int, day: LocalDate): String = """
        {"id":"$id","title":"$title","amount":$amount,
         "createdAt":"${CalendarDay.wire(day)}",
         "expenseDate":"${CalendarDay.wire(day)}",
         "isReimbursement":false,"splitMode":"EVENLY",
         "paidBy":{"id":"p1","name":"Ana"},
         "paidFor":[{"participant":{"id":"p1","name":"Ana"},"shares":100}]}
    """.trimIndent()

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
