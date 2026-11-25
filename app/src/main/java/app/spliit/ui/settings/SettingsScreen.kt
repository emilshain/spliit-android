package app.spliit.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.spliit.AppContainer
import app.spliit.domain.ServerInstance
import app.spliit.ui.theme.SpliitType
import kotlinx.coroutines.launch

/**
 * The handful of things this app can be told.
 *
 * Spliit has no accounts, so there is nothing to sign out of: the one real setting is which
 * instance a group with no address of its own belongs to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val current by container.defaultInstanceUrl.collectAsState(
        initial = ServerInstance.DEFAULT_BASE_URL,
    )
    var field by remember(current) { mutableStateOf(current) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = SpliitType.RowTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Server", style = SpliitType.SectionHeader)
                    Text(
                        text = "Groups you create start on this instance, and groups carried over " +
                            "from an older install without an address are read from it.",
                        style = SpliitType.Secondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = field,
                        onValueChange = {
                            field = it
                            message = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Instance address") },
                        placeholder = { Text(ServerInstance.DEFAULT_BASE_URL) },
                        singleLine = true,
                    )
                    message?.let { text ->
                        Text(
                            text = text,
                            style = SpliitType.Secondary,
                            color = if (isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val saved = container.serverSettings.setBaseUrl(field)
                                    isError = !saved
                                    message = if (saved) {
                                        "Saved."
                                    } else {
                                        "That doesn’t look like an address."
                                    }
                                }
                            },
                        ) {
                            Text("Save")
                        }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    container.serverSettings.reset()
                                    field = ServerInstance.DEFAULT_BASE_URL
                                    isError = false
                                    message = "Back to ${ServerInstance.DEFAULT_BASE_URL}."
                                }
                            },
                        ) {
                            Text("Reset")
                        }
                    }
                }
            }

            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("About", style = SpliitType.SectionHeader)
                    Text("Spliit for Android", style = SpliitType.RowTitle)
                    Text(
                        text = "An independent client for Spliit. Your groups live on the server " +
                            "you point the app at, not on this phone.",
                        style = SpliitType.Secondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(18.dp)) { content() }
    }
}
