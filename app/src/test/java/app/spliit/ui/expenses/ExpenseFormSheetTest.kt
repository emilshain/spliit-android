package app.spliit.ui.expenses

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.spliit.data.local.RecentGroup
import app.spliit.data.model.Group
import app.spliit.data.model.Participant
import app.spliit.data.model.SplitMode
import app.spliit.domain.ExpenseFormDraft
import app.spliit.domain.ParticipantShare
import app.spliit.ui.FakeSpliitServer
import app.spliit.ui.seedGroups
import app.spliit.ui.theme.SpliitTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The expense form: it refuses a save the server would reject, and the split picker decides what
 * the shares mean on the wire.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ExpenseFormSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val group = Group(
        id = "g1",
        name = "Flat",
        currency = "€",
        currencyCode = "EUR",
        createdAt = Instant.parse("2026-01-05T10:00:00Z"),
        participants = listOf(
            Participant("p1", "Ana"),
            Participant("p2", "Bruno"),
        ),
    )

    @Test
    fun anExpenseWithNoAmount_isRefusedBeforeAnythingIsSent() {
        val server = FakeSpliitServer()
        val container = server.container()
        var saved = false

        show(
            server = server,
            draft = ExpenseFormDraft.creatingIn(group),
            onSaved = { saved = true },
        )

        compose.onNodeWithText("Save").performClick()

        compose.onNodeWithText("You must enter an amount.").assertIsDisplayed()
        assertFalse(saved)
        assertTrue(server.callsTo("groups.expenses.create") == 0)
    }

    @Test
    fun choosingPercent_makesTheSplitAddUpToAHundred() {
        val server = FakeSpliitServer()
        var saved = false

        show(
            server = server,
            draft = ExpenseFormDraft.creatingIn(group).copy(amountText = "30.00"),
            onSaved = { saved = true },
        )

        // The mode is a segmented picker: one tap changes what every field means.
        compose.onNodeWithText("Percent").performClick()
        compose.onNodeWithText("Save").performClick()

        compose.onNodeWithText("Sum of percentages must equal 100.").assertIsDisplayed()
        assertFalse(saved)
        assertTrue(server.callsTo("groups.expenses.create") == 0)
    }

    @Test
    fun aValidPercentageSplit_isSentWithSpreadByAThousand() {
        val server = FakeSpliitServer()
        server.answer("groups.expenses.create", """{"expenseId":"e1"}""")
        var saved = false

        val draft = ExpenseFormDraft.creatingIn(group)
            .copy(
                title = "Dinner",
                amountText = "30.00",
                splitMode = SplitMode.BY_PERCENTAGE,
                participants = listOf(
                    ParticipantShare("p1", "Ana", isIncluded = true, valueText = "60"),
                    ParticipantShare("p2", "Bruno", isIncluded = true, valueText = "40"),
                ),
            )

        show(server = server, draft = draft, onSaved = { saved = true })

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Save").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Save").performClick()

        compose.waitUntil(TIMEOUT) { saved }

        val body = server.lastBody("groups.expenses.create").orEmpty()
        assertTrue("expected the total in the body, got $body", body.contains("\"amount\":3000"))
        assertTrue("expected percentages on the ×100 scale, got $body", body.contains("\"shares\":6000"))
        assertTrue(body.contains("\"shares\":4000"))
        assertTrue(body.contains("\"splitMode\":\"BY_PERCENTAGE\""))
        // The ID is minted before the save, so the split the form showed is the one stored.
        assertTrue(body.contains("\"expenseId\":\"exp-1\""))
        // Written to the server the group is on, not to whatever its ID happens to spell.
        assertTrue(
            "expected the group's instance, got ${server.lastUrl("groups.expenses.create")}",
            server.lastUrl("groups.expenses.create").orEmpty().startsWith("https://spliit.app/"),
        )
    }

    @Test
    fun aSettlementPrefillsThePayerAndIsMarkedAsAReimbursement() {
        val server = FakeSpliitServer()
        server.answer("groups.expenses.create", """{"expenseId":"e1"}""")
        var saved = false

        val draft = ExpenseFormDraft.settling(
            app.spliit.data.model.Reimbursement(from = "p2", to = "p1", amount = 1250),
            group,
            title = "Payment from Bruno to Ana",
        )

        show(server = server, draft = draft, onSaved = { saved = true })

        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(TIMEOUT) { saved }

        val body = server.lastBody("groups.expenses.create").orEmpty()
        assertTrue(body.contains("\"isReimbursement\":true"))
        assertTrue(body.contains("\"paidBy\":\"p2\""))
    }

    private fun show(
        server: FakeSpliitServer,
        draft: ExpenseFormDraft,
        onSaved: () -> Unit,
    ) {
        val container = server.container()
        seedGroups(
            container.recentGroups,
            RecentGroup(groupId = "g1", groupName = "Flat", instanceURL = "https://spliit.app"),
        )

        compose.setContent {
            SpliitTheme {
                ExpenseFormSheet(
                    container = container,
                    group = group,
                    editing = null,
                    initialDraft = draft,
                    mintedExpenseId = "exp-1",
                    categories = emptyList(),
                    instanceUrl = "https://spliit.app",
                    actorId = null,
                    onDismiss = {},
                    onSaved = onSaved,
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Save").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
