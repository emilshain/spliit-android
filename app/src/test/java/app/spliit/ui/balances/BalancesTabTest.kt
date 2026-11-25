package app.spliit.ui.balances

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.spliit.data.local.ActiveParticipant
import org.junit.Assert.assertEquals
import app.spliit.data.local.RecentGroup
import app.spliit.ui.FakeSpliitServer
import app.spliit.ui.group.GroupScreen
import app.spliit.ui.seedGroups
import app.spliit.ui.theme.SpliitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The balances tab, read from where the person holding the phone stands: their own position
 * first, then the group, then the payments that would settle it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BalancesTabTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theTabLeadsWithYourOwnBalanceAndOffersTheSettlement() {
        val server = FakeSpliitServer()
        server.answer(
            "groups.get",
            """{"group":{"id":"g1","name":"Flat","currency":"€","currencyCode":"EUR",
               "createdAt":"2026-01-05T10:00:00Z",
               "participants":[{"id":"p1","name":"Ana"},{"id":"p2","name":"Bruno"}]}}""",
        )
        server.answer("groups.expenses.list", """{"expenses":[],"hasMore":false,"nextCursor":20}""")
        server.answer("categories.list", """{"categories":[]}""")
        server.answer(
            "groups.balances.list",
            """{"balances":{
                 "p1":{"paid":0,"paidFor":2500,"total":-2500},
                 "p2":{"paid":2500,"paidFor":0,"total":2500}},
               "reimbursements":[{"from":"p1","to":"p2","amount":2500}]}""",
        )

        val container = server.container()
        seedGroups(
            container.recentGroups,
            RecentGroup(
                groupId = "g1",
                groupName = "Flat",
                instanceURL = "https://spliit.app",
                activeParticipant = ActiveParticipant.Participant("p1"),
            ),
        )

        compose.setContent {
            SpliitTheme { GroupScreen(container = container, groupId = "g1", onBack = {}) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Balances").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Balances").performClick()

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("You owe").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("You owe").assertIsDisplayed()
        // The payments start folded away; open them to read the suggestion.
        compose.onNodeWithText("Suggested payments").performClick()
        // The hero amount and the suggested payment are the same €25.00.
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("€25.00").fetchSemanticsNodes().size >= 2
        }
        compose.onNodeWithText("Ana owes Bruno").assertIsDisplayed()
        compose.onNodeWithText("Mark as paid").assertIsDisplayed()
        // The card already says whose balance this is, so Ana appears once: her own row.
        assertEquals(1, compose.onAllNodesWithText("Ana").fetchSemanticsNodes().size)
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
