package app.spliit.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.spliit.AppContainer
import app.spliit.data.remote.SpliitApi
import app.spliit.domain.GroupLink
import app.spliit.domain.ServerInstance
import app.spliit.ui.theme.SpliitType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.spliit.data.local.RecentGroup

/**
 * Adds a group someone shared, by pasting its URL.
 *
 * Spliit has no accounts: a group URL *is* the invitation, so this is how a second device ever
 * learns about a group. The link says which server as well as which group, which is what lets
 * somebody be handed a group on an instance this device has never talked to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroupLinkSheet(
    container: AppContainer,
    onDismiss: () -> Unit,
    onAdded: (RecentGroup) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var defaultInstance by remember { mutableStateOf(ServerInstance.DEFAULT_BASE_URL) }
    var urlText by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        defaultInstance = container.defaultInstanceUrl.first()
    }

    fun add() {
        if (isChecking) return
        problem = null

        val link = GroupLink.parsePasted(urlText)
        if (link == null) {
            problem = "That doesn’t look like a Spliit group link."
            return
        }
        // A bare ID names no server, and the one this device creates groups on is the only
        // reasonable guess.
        val instanceUrl = link.instanceUrl ?: defaultInstance

        isChecking = true
        scope.launch {
            try {
                val group = container.client(instanceUrl).call(SpliitApi.group(link.groupId)).group
                if (group == null) {
                    problem = "No group with that link exists on " +
                        "${ServerInstance.displayName(instanceUrl)}."
                    return@launch
                }
                val recent = RecentGroup(
                    groupId = group.id,
                    groupName = group.name,
                    instanceURL = instanceUrl,
                )
                container.recentGroups.remember(recent)
                onAdded(recent)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                problem = error.localizedMessage
            } finally {
                isChecking = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Text("Add group", style = SpliitType.RowTitle)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { add() }, enabled = !isChecking && urlText.isNotEmpty()) {
                    Text(if (isChecking) "Adding…" else "Add")
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormHeader("Group link")
                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        problem = null
                    },
                    placeholder = { Text("$defaultInstance/groups/…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                problem?.let { message ->
                    Text(
                        text = message,
                        style = SpliitType.Secondary,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                FormFooter(
                    "Paste the link to a group that was shared with you, from spliit.app or " +
                        "from any other Spliit server, and it will appear in your list.",
                )
            }
        }
    }

}
