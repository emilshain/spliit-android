package app.spliit.ui.groups

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.spliit.domain.GroupFormDraft
import app.spliit.ui.FakeSpliitServer
import app.spliit.ui.theme.SpliitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The create form's first job: refuse an empty group here, next to the field, instead of sending
 * a request the server would reject.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class CreateGroupSheetTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun savingWithNoName_showsTheProblemAndCallsNothing() {
        val server = FakeSpliitServer()
        val container = server.container()
        var created = false

        compose.setContent {
            SpliitTheme {
                CreateGroupSheet(
                    container = container,
                    defaultInstanceUrl = "https://spliit.app",
                    onDismiss = {},
                    onSaved = { created = true },
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Create").fetchSemanticsNodes().isNotEmpty()
        }

        // Nothing has been attempted yet, so no error is showing.
        compose.onNodeWithText(GroupFormDraft.Problem.NameTooShort.message).assertDoesNotExist()

        compose.onNodeWithText("Create").performClick()

        compose.onNodeWithText(GroupFormDraft.Problem.NameTooShort.message).assertIsDisplayed()
        assertFalse("a group must not be reported created", created)
        assertEquals("the form must not reach the server", 0, server.callsTo("groups.create"))
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
