package app.spliit.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.spliit.AppContainer
import app.spliit.data.local.RecentGroup
import app.spliit.data.model.Group
import app.spliit.data.remote.SpliitApi
import app.spliit.domain.CurrencyCatalog
import app.spliit.domain.CurrencyInfo
import app.spliit.domain.GroupFormDraft
import app.spliit.domain.InstanceChoice
import app.spliit.domain.ParticipantDraft
import app.spliit.domain.ServerInstance
import app.spliit.ui.theme.ExpandChevron
import app.spliit.ui.theme.ExpressiveMenu
import app.spliit.ui.theme.ExpressiveMenuItem
import app.spliit.ui.theme.ParticipantDot
import app.spliit.ui.theme.SpliitType
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The group form, presented as a sheet: a new group from the group list, or an existing one from
 * its settings.
 *
 * Errors only appear once a save has been attempted — a form that is red before you have typed
 * anything reads as broken rather than helpful.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupSheet(
    container: AppContainer,
    defaultInstanceUrl: String,
    /** The group being edited, or null when this sheet is making a new one. */
    editing: Group? = null,
    /** Where [editing] lives. A group never moves between servers, so it is not asked for. */
    editingInstanceUrl: String? = null,
    /** Who to credit in the activity log. */
    actorId: String? = null,
    onDismiss: () -> Unit,
    onSaved: (RecentGroup) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editingGroup = editing
    val isEditing = editingGroup != null

    var draft by remember(editingGroup?.id) {
        mutableStateOf(editingGroup?.let { GroupFormDraft.editing(it) } ?: GroupFormDraft.newGroup())
    }
    // Where the form opens: the last instance a group was deliberately created on.
    var instance by remember {
        mutableStateOf(InstanceChoice(url = editingInstanceUrl ?: defaultInstanceUrl))
    }
    var knownInstances by remember { mutableStateOf(listOf(defaultInstanceUrl)) }
    var hasAttemptedSave by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var serverMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        knownInstances = container.knownInstances()
    }

    fun save() {
        hasAttemptedSave = true
        val instanceUrl = editingInstanceUrl?.takeIf { isEditing } ?: instance.resolved
        if (!draft.isValid || instanceUrl == null || isSaving) return
        if (!isEditing && !instance.isValid) return

        isSaving = true
        scope.launch {
            try {
                val values = draft.formValues
                val groupId = if (editingGroup != null) {
                    container.client(instanceUrl).call(
                        SpliitApi.updateGroup(editingGroup.id, values, actorId),
                    )
                    editingGroup.id
                } else {
                    val response = container.client(instanceUrl).call(SpliitApi.createGroup(values))
                    container.noteInstanceUsedForNewGroup(instanceUrl)
                    response.groupId
                }
                // The stored name is what the group list shows, so a rename has to reach it.
                val group = RecentGroup(
                    groupId = groupId,
                    groupName = values.name,
                    instanceURL = instanceUrl,
                )
                container.recentGroups.remember(group)
                onSaved(group)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failure = error.localizedMessage ?: if (isEditing) {
                    "Couldn’t save the group."
                } else {
                    "Couldn’t create the group."
                }
            } finally {
                isSaving = false
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
                Text(
                    if (isEditing) "Edit group" else "Create group",
                    style = SpliitType.RowTitle,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { save() }, enabled = !isSaving) {
                    Text(
                        when {
                            isSaving && isEditing -> "Saving…"
                            isSaving -> "Creating…"
                            isEditing -> "Save"
                            else -> "Create"
                        },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormHeader("Group information")

                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val nameProblem = draft.problems.firstOrNull {
                    it is GroupFormDraft.Problem.NameTooShort || it is GroupFormDraft.Problem.NameTooLong
                }
                if (hasAttemptedSave && nameProblem != null) {
                    FieldError(nameProblem.message)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCurrencyPicker = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Currency", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = currencySummary(draft),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ExpandChevron(expanded = showCurrencyPicker)
                }

                // Only when there is no currency to derive it from. A symbol that disagreed with
                // the group's ISO code would be a lie told beside every amount.
                if (draft.usesCustomSymbol) {
                    OutlinedTextField(
                        value = draft.currency,
                        onValueChange = { draft = draft.copy(currency = it) },
                        label = { Text("Currency symbol") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val currencyProblem = draft.problems.firstOrNull {
                        it is GroupFormDraft.Problem.CurrencyMissing ||
                            it is GroupFormDraft.Problem.CurrencyTooLong
                    }
                    if (hasAttemptedSave && currencyProblem != null) {
                        FieldError(currencyProblem.message)
                    }
                }
                if (hasAttemptedSave && draft.problems.contains(GroupFormDraft.Problem.CurrencyCodeInvalid)) {
                    FieldError(GroupFormDraft.Problem.CurrencyCodeInvalid.message)
                }

                if (!isEditing) {
                    // The server a group is created on. A group cannot move between servers, so the
                    // address is only ever chosen once.
                    FormHeader("Server")

                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { serverMenuOpen = true }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Server", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (instance.isTypingAddress) {
                                    "Other server"
                                } else {
                                    ServerInstance.displayName(instance.url)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ExpandChevron(expanded = serverMenuOpen)
                        }

                        ExpressiveMenu(
                            expanded = serverMenuOpen,
                            onDismissRequest = { serverMenuOpen = false },
                        ) {
                            knownInstances.forEach { url ->
                                ExpressiveMenuItem(
                                    text = ServerInstance.displayName(url),
                                    selected = !instance.isTypingAddress &&
                                        instance.url == url,
                                    onClick = {
                                        serverMenuOpen = false
                                        instance = instance.use(url)
                                    },
                                )
                            }
                            ExpressiveMenuItem(
                                text = "Other server",
                                selected = instance.isTypingAddress,
                                onClick = {
                                    serverMenuOpen = false
                                    instance = instance.typeAddress()
                                },
                            )
                        }
                    }

                    if (instance.isTypingAddress) {
                        OutlinedTextField(
                            value = instance.address,
                            onValueChange = { instance = instance.copy(address = it) },
                            label = { Text("spliit.example.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                autoCorrectEnabled = false,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (hasAttemptedSave && !instance.isValid) {
                            FieldError(
                                "That doesn’t look like a web address. Try something like " +
                                    "spliit.example.com.",
                            )
                        }
                    }
                    FormFooter(
                        "Where this group is created. Groups on different Spliit servers sit in " +
                            "the same list, and a group stays on the server it was made on.",
                    )
                }

                FormHeader("Notes")
                OutlinedTextField(
                    value = draft.information,
                    onValueChange = { draft = draft.copy(information = it) },
                    label = { Text("What should participants know?") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )

                FormHeader("Participants")
                draft.participants.forEachIndexed { index, participant ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // The colour this participant will wear on the balances screen,
                            // shown where the list that decides it is being edited.
                            ParticipantDot(position = index, size = 9.dp)
                            OutlinedTextField(
                                value = participant.name,
                                onValueChange = { newName ->
                                    draft = draft.copy(
                                        participants = draft.participants.map {
                                            if (it.id == participant.id) it.copy(name = newName) else it
                                        },
                                    )
                                },
                                label = { Text("Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    draft = draft.copy(
                                        participants = draft.participants.filterNot { it.id == participant.id },
                                    )
                                },
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove participant")
                            }
                        }
                        if (hasAttemptedSave) {
                            draft.problemsForParticipant(participant.id).firstOrNull()?.let {
                                FieldError(it.message)
                            }
                        }
                    }
                }

                TextButton(
                    onClick = {
                        draft = draft.copy(
                            participants = draft.participants + ParticipantDraft(),
                        )
                    },
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add participant")
                }

                if (hasAttemptedSave && draft.problems.contains(GroupFormDraft.Problem.NoParticipants)) {
                    FieldError(GroupFormDraft.Problem.NoParticipants.message)
                } else {
                    FormFooter("Anyone who already appears on an expense can’t be removed.")
                }

                HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            }
        }
    }

    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            selectedCode = draft.currencyCode,
            onPick = { currency ->
                draft = if (currency == null) draft.useCustomSymbol() else draft.use(currency)
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false },
        )
    }

    failure?.let { message ->
        AlertDialog(
            onDismissRequest = { failure = null },
            title = {
                Text(if (isEditing) "Couldn’t save the group" else "Couldn’t create the group")
            },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { failure = null }) { Text("OK") }
            },
        )
    }
}

/** What the picker row shows on the right: the name and symbol, or the symbol alone. */
private fun currencySummary(draft: GroupFormDraft): String {
    val currency = draft.selectedCurrency()
    return when {
        currency != null -> "${currency.name} (${currency.symbol})"
        !draft.currencyCode.isNullOrEmpty() -> draft.currencyCode!!
        else -> draft.currency
    }
}

@Composable
internal fun FormHeader(text: String) {
    Text(
        text = text,
        style = SpliitType.SectionHeader,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
internal fun FormFooter(text: String) {
    Text(
        text = text,
        style = SpliitType.Secondary,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun FieldError(text: String) {
    Text(
        text = text,
        style = SpliitType.Secondary,
        color = MaterialTheme.colorScheme.error,
    )
}

/**
 * The currency picker: the device's own currency first, then everything the platform can name,
 * searchable by any of the three things a person might type.
 */
@Composable
internal fun CurrencyPickerDialog(
    selectedCode: String?,
    onPick: (CurrencyInfo?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val locale = Locale.getDefault()
    val all = remember(locale) { CurrencyCatalog.all(locale) }
    val matches = remember(query, all) {
        if (query.isBlank()) {
            all
        } else {
            val needle = query.trim().lowercase(locale)
            all.filter {
                it.name.lowercase(locale).contains(needle) ||
                    it.code.lowercase(locale).contains(needle) ||
                    it.symbol.lowercase(locale).contains(needle)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Currency") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    ExpressiveMenuItem(
                        text = "Custom symbol",
                        selected = selectedCode == null,
                        onClick = { onPick(null) },
                    )
                    matches.forEach { currency ->
                        ExpressiveMenuItem(
                            text = "${currency.name} (${currency.symbol})",
                            selected = currency.code == selectedCode,
                            onClick = { onPick(currency) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
