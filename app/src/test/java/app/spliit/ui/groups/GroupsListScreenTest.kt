package app.spliit.ui.groups

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import app.spliit.data.local.RecentGroup
import app.spliit.ui.FakeSpliitServer
import app.spliit.ui.seedGroups
import app.spliit.ui.theme.SpliitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The home screen, rendered for the first time: what a new install sees, and where the three
 * sections put the groups the store remembers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class GroupsListScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyStore_offersCreatingOrJoiningAGroup() {
        val container = FakeSpliitServer().container()

        compose.setContent {
            SpliitTheme {
                GroupsListScreen(container = container, onOpenGroup = {}, onEditGroup = {})
            }
        }

        compose.onNodeWithText("Welcome to Spliit").assertIsDisplayed()
        compose.onNodeWithText("Create group").assertIsDisplayed()
        compose.onNodeWithText("Add by link").assertIsDisplayed()
    }

    @Test
    fun seededStore_showsStarredRecentAndArchivedSections() {
        val server = FakeSpliitServer()
        server.answer("groups.list", """{"groups":[]}""")
        val container = server.container()
        seedGroups(
            container.recentGroups,
            RecentGroup(groupId = "g-star", groupName = "Ski trip", isStarred = true),
            RecentGroup(groupId = "g-recent", groupName = "Flat"),
            RecentGroup(groupId = "g-archived", groupName = "Old flat", isArchived = true),
        )

        compose.setContent {
            SpliitTheme {
                GroupsListScreen(container = container, onOpenGroup = {}, onEditGroup = {})
            }
        }

        // The rows come from the store's flow, so the first frame is still the empty state.
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Starred").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Starred").assertIsDisplayed()
        compose.onNodeWithText("Recent groups").assertIsDisplayed()
        compose.onNodeWithText("Archived").assertIsDisplayed()

        compose.onNodeWithText("Ski trip").assertIsDisplayed()
        compose.onNodeWithText("Flat").assertIsDisplayed()
        compose.onNodeWithText("Old flat").assertIsDisplayed()

        // The welcome screen is for a list with nothing in it, and this one has three groups.
        compose.onNodeWithText("Welcome to Spliit").assertDoesNotExist()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
