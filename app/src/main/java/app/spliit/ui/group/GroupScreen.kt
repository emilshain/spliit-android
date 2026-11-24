package app.spliit.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import app.spliit.data.model.Reimbursement
import app.spliit.domain.CalendarDay
import app.spliit.domain.ExpenseFormDraft
import app.spliit.domain.ServerInstance
import app.spliit.ui.balances.BalancesTab
import app.spliit.ui.expenses.ExpenseFormSheet
import app.spliit.ui.expenses.ExpenseListTab
import app.spliit.ui.groups.ActiveUserPickerSheet
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.Monogram
import app.spliit.ui.theme.MonogramPalette
import app.spliit.ui.theme.SpliitType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.random.Random

private val GroupTabs = listOf("Expenses", "Balances", "Information")

/**
 * A group: its expenses, its balances, and who the person holding the phone is in it.
 *
 * The three tabs share one model, which is what lets an expense added on the first update the
 * second without either knowing about the other.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    container: AppContainer,
    groupId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val model = remember(container, groupId) { GroupScreenModel(container, groupId, scope) }
    var tabIndex by remember { mutableStateOf(0) }
    var sheet by remember { mutableStateOf<ExpenseFormPresentation?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(model) { model.start() }
    // A delete the user has already asked for must not be lost to leaving the screen.
    DisposableEffect(model) { onDispose { model.flushPendingDeletion() } }

    val recentGroups by container.recentGroups.groups.collectAsState(initial = emptyList())
    val stored = recentGroups.firstOrNull { it.groupId == groupId }
    val group = model.group

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (tabIndex == 0 && group != null) {
                        IconButton(onClick = { sheet = createPresentation(group, model, stored) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add expense")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                group == null && model.isLoadingGroup -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                group == null && model.didFailToLoad -> EmptyState(
                    art = EmptyStateArt.Icon(Icons.Filled.Warning),
                    title = "Couldn’t load the group",
                    description = model.loadFailure,
                ) {
                    Button(onClick = { model.retry() }) { Text("Try again") }
                }

                group != null -> Column(modifier = Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = tabIndex) {
                        GroupTabs.forEachIndexed { index, title ->
                            Tab(
                                selected = tabIndex == index,
                                onClick = { tabIndex = index },
                                text = { Text(title) },
                            )
                        }
                    }

                    val identity = identityOf(stored, group)
                    Box(modifier = Modifier.weight(1f)) {
                        when (tabIndex) {
                            0 -> ExpenseListTab(
                                model = model,
                                onAdd = { sheet = createPresentation(group, model, stored) },
                                onEdit = { expenseId ->
                                    scope.launch {
                                        val expense = model.expense(expenseId) ?: return@launch
                                        sheet = ExpenseFormPresentation(
                                            draft = ExpenseFormDraft.editing(expense, group),
                                            editing = expense,
                                            mintedId = expense.id,
                                        )
                                    }
                                },
                            )

                            1 -> BalancesTab(
                                model = model,
                                identity = identity,
                                identityIsNobody = stored?.activeParticipant == ActiveParticipant.Nobody,
                                onIdentify = { pickerOpen = true },
                                onSettle = { reimbursement ->
                                    sheet = settlePresentation(reimbursement, group, model)
                                },
                            )

                            else -> InformationTab(
                                group = group,
                                model = model,
                                stored = stored,
                                onIdentify = { pickerOpen = true },
                            )
                        }
                    }
                }
            }
        }
    }

    sheet?.let { presentation ->
        val current = model.group ?: return@let
        ExpenseFormSheet(
            container = container,
            group = current,
            editing = presentation.editing,
            initialDraft = presentation.draft,
            mintedExpenseId = presentation.mintedId,
            categories = model.categories,
            instanceUrl = model.instanceUrl ?: ServerInstance.DEFAULT_BASE_URL,
            actorId = model.actorId,
            onDismiss = { sheet = null },
            onSaved = {
                sheet = null
                model.reloadAfterExpenseChange()
            },
        )
    }

    if (pickerOpen && group != null) {
        ActiveUserPickerSheet(
            group = group,
            selection = stored?.activeParticipant,
            onSelect = { participant ->
                scope.launch {
                    container.recentGroups.setActiveParticipant(participant, groupId)
                    model.refreshActor()
                }
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

/** What the form is being opened for: a new expense, an edit, or a settlement. */
private data class ExpenseFormPresentation(
    val draft: ExpenseFormDraft,
    val editing: app.spliit.data.model.ExpenseDetails?,
    val mintedId: String,
)

private fun createPresentation(
    group: Group,
    model: GroupScreenModel,
    stored: RecentGroup?,
): ExpenseFormPresentation = ExpenseFormPresentation(
    draft = ExpenseFormDraft.creatingIn(
        group = group,
        paidBy = model.actorId,
        defaultSplit = stored?.defaultSplit,
    ),
    editing = null,
    mintedId = mintExpenseId(),
)

private fun settlePresentation(
    reimbursement: Reimbursement,
    group: Group,
    model: GroupScreenModel,
): ExpenseFormPresentation {
    val from = model.participant(reimbursement.from)?.name ?: "someone"
    val to = model.participant(reimbursement.to)?.name ?: "someone"
    return ExpenseFormPresentation(
        draft = ExpenseFormDraft.settling(
            reimbursement = reimbursement,
            group = group,
            title = "Payment from $from to $to",
        ),
        editing = null,
        mintedId = mintExpenseId(),
    )
}

/** The person holding the phone, when the store says somebody the group still has. */
private fun identityOf(
    stored: RecentGroup?,
    group: Group,
): app.spliit.data.model.Participant? =
    (ActiveParticipant.resolve(stored?.activeParticipant, group.participants)
        as? ActiveParticipant.Participant)
        ?.let { answer -> group.participants.firstOrNull { it.id == answer.id } }

/**
 * The identity a new expense starts from, and the group's own facts.
 *
 * Phase 4 brings the totals, activity and search tabs here; this is the information the app
 * already had.
 */
@Composable
private fun InformationTab(
    group: Group,
    model: GroupScreenModel,
    stored: RecentGroup?,
    onIdentify: () -> Unit,
) {
    val identity = identityOf(stored, group)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!group.information.isNullOrBlank()) {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Notes", style = SpliitType.SectionHeader)
                    Text(group.information)
                }
            }
        }

        Card {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Participants", style = SpliitType.SectionHeader)
                group.participants.forEachIndexed { position, participant ->
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
                Fact("Currency", currencyLabel(group))
                Fact("Created", dateLabel(group.createdAt))
                model.instanceUrl?.let { Fact("Server", ServerInstance.displayName(it)) }
            }
        }

        // The question the balances tab needs answered, offered beside the answer it unlocks.
        Card {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onIdentify),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Who are you?", style = SpliitType.RowTitle)
                    Text(
                        text = when {
                            identity != null -> identity.name
                            stored?.activeParticipant == ActiveParticipant.Nobody -> "Nobody"
                            else -> "Not answered yet"
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

/**
 * A fresh expense ID, minted before saving so the split the form previews is the one the expense
 * is saved with: the ID decides who is offered the leftover minor unit.
 */
private fun mintExpenseId(): String {
    val alphabet = "useandom-26T198340PX75pxJACKVERYMINDBUSHWOLF_GQZbfghjklqvwyzrict"
    return (0 until 21).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
}
