package app.spliit.ui.groups

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.spliit.data.local.ActiveParticipant
import app.spliit.data.local.RecentGroup
import app.spliit.data.model.Group
import app.spliit.data.model.Participant
import app.spliit.ui.FakeSpliitServer
import app.spliit.ui.seedGroups
import app.spliit.ui.theme.SpliitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * "Who are you?" — the one question the app asks about a person, and the only place a group's
 * local identity can be answered.
 *
 * The choice goes into the recent-groups store through the same repository the group screen
 * writes to, so what is asserted is what a later screen would read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ActiveUserPickerSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val group = Group(
        id = GROUP_ID,
        name = "Flat",
        currency = "€",
        currencyCode = "EUR",
        createdAt = Instant.parse("2026-01-05T10:00:00Z"),
        participants = listOf(
            Participant(id = "p1", name = "Ana"),
            Participant(id = "p2", name = "Bruno"),
        ),
    )

    @Test
    fun pickingAParticipant_isPersistedForTheGroup() {
        val container = FakeSpliitServer().container()
        seedGroups(
            container.recentGroups,
            RecentGroup(groupId = GROUP_ID, groupName = "Flat", instanceURL = "https://spliit.app"),
        )

        showPicker(container)

        // The row's own name, not the participant list elsewhere: only the picker's rows click.
        compose.onNode(hasText("Bruno") and hasClickAction()).performClick()

        assertEquals(
            ActiveParticipant.Participant("p2"),
            runBlocking { container.recentGroups.activeParticipant(GROUP_ID) },
        )
    }

    /**
     * "Nobody" is an answer, not a missing one: a shared phone must not be asked again, so this
     * has to persist as [ActiveParticipant.Nobody] rather than leave the group unanswered.
     */
    @Test
    fun pickingNobody_isAnAnswerRatherThanSilence() {
        val container = FakeSpliitServer().container()
        seedGroups(
            container.recentGroups,
            RecentGroup(groupId = GROUP_ID, groupName = "Flat", instanceURL = "https://spliit.app"),
        )

        showPicker(container)

        compose.onNode(hasText("Nobody") and hasClickAction()).performClick()

        assertEquals(
            ActiveParticipant.Nobody,
            runBlocking { container.recentGroups.activeParticipant(GROUP_ID) },
        )
    }

    private fun showPicker(container: app.spliit.AppContainer) {
        compose.setContent {
            SpliitTheme {
                ActiveUserPickerSheet(
                    group = group,
                    selection = null,
                    onSelect = { choice ->
                        runBlocking { container.recentGroups.setActiveParticipant(choice, GROUP_ID) }
                    },
                    onDismiss = {},
                )
            }
        }

        // The sheet occupies its own window, so wait for its rows before touching them.
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText("Bruno") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Who are you?").assertIsDisplayed()
    }

    private companion object {
        const val GROUP_ID = "group-flat"
        const val TIMEOUT = 5_000L
    }
}
