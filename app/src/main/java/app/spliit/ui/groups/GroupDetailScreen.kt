package app.spliit.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.spliit.AppContainer
import app.spliit.data.local.ActiveParticipant
import app.spliit.data.local.RecentGroup
import app.spliit.data.model.Group
import app.spliit.data.remote.SpliitApi
import app.spliit.domain.ServerInstance
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.Monogram
import app.spliit.ui.theme.MonogramPalette
import app.spliit.ui.theme.SpliitType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * A group, as Phase 2 needs it: who is in it, and who the person holding the phone is.
 *
 * The expenses, balances, totals and activity tabs are Phase 3 and Phase 4. This screen exists
 * now because "Who are you?" is offered beside the answer it unlocks — the reference puts it on
 * the group's information tab, not in front of the group as a toll gate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    container: AppContainer,
    groupId: String,
    onBack: () -> Unit,
) {
    var group by remember { mutableStateOf<Group?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var instanceUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val recentGroups by container.recentGroups.groups.collectAsState(initial = emptyList())

    LaunchedEffect(groupId) {
        try {
            val instance = container.instanceForGroup(groupId)
            instanceUrl = instance
            val loaded = container.client(instance).call(SpliitApi.group(groupId)).group
            if (loaded == null) {
                failure = "That group isn’t on ${ServerInstance.displayName(instance)}."
            } else {
                group = loaded
                // Opening a group is the plainest statement that it belongs in the list.
                container.recentGroups.remember(
                    RecentGroup(
                        groupId = loaded.id,
                        groupName = loaded.name,
                        instanceURL = instance,
                    ),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failure = error.localizedMessage
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val loaded = group
        when {
            failure != null -> EmptyState(
                art = EmptyStateArt.Icon(Icons.Filled.Warning),
                title = "Couldn’t load the group",
                description = failure,
            )

            loaded == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            else -> {
                val stored = recentGroups.firstOrNull { it.groupId == groupId }?.activeParticipant
                val selection = ActiveParticipant.resolve(stored, loaded.participants)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Card {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Participants", style = SpliitType.SectionHeader)
                            loaded.participants.forEachIndexed { position, participant ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Monogram(
                                        name = participant.name,
                                        colorIndex = MonogramPalette.indexAtPosition(position),
                                        size = 28.dp,
                                    )
                                    Text(participant.name)
                                }
                            }
                        }
                    }

                    Card {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Group", style = SpliitType.SectionHeader)
                            Fact("Currency", currencyLabel(loaded))
                            loaded.createdAt?.let { Fact("Created", dateLabel(it)) }
                            instanceUrl?.let { Fact("Server", ServerInstance.displayName(it)) }
                        }
                    }

                    // The question the balances tab will need answered. Offered here, beside the
                    // answer it unlocks, rather than as a sheet in front of the expenses.
                    Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPicker = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Who are you?", style = SpliitType.RowTitle)
                                Text(
                                    text = when (selection) {
                                        is ActiveParticipant.Participant ->
                                            loaded.participants.firstOrNull { it.id == selection.id }?.name
                                                ?: "Someone who has left"

                                        ActiveParticipant.Nobody -> "Nobody"
                                        null -> "Not answered yet"
                                    },
                                    style = SpliitType.Secondary,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (showPicker) {
                    ActiveUserPickerSheet(
                        group = loaded,
                        selection = selection,
                        onSelect = { participant ->
                            scope.launch {
                                container.recentGroups.setActiveParticipant(participant, groupId)
                            }
                            showPicker = false
                        },
                        onDismiss = { showPicker = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Box(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = SpliitType.Secondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(text = value)
    }
}

private fun currencyLabel(group: Group): String =
    group.currencyCode?.let { "${group.currency} ($it)" } ?: group.currency

private fun dateLabel(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(instant.atZone(ZoneId.systemDefault()).toLocalDate())
