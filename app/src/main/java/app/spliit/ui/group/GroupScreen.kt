package app.spliit.ui.group

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.spliit.AppContainer
import app.spliit.data.local.ActiveParticipant
import app.spliit.data.local.RecentGroup
import app.spliit.data.model.ExpenseDetails
import app.spliit.data.model.Group
import app.spliit.data.model.Reimbursement
import app.spliit.domain.CalendarDay
import app.spliit.domain.ExpenseFormDraft
import app.spliit.domain.ServerInstance
import app.spliit.ui.activity.ActivityLogScreen
import app.spliit.ui.balances.BalancesTab
import app.spliit.ui.expenses.ExpenseDetailScreen
import app.spliit.ui.expenses.ExpenseFormSheet
import app.spliit.ui.expenses.ExpenseListTab
import app.spliit.ui.groups.ActiveUserPickerSheet
import app.spliit.ui.search.ExpenseSearchTab
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.Monogram
import app.spliit.ui.theme.MonogramPalette
import app.spliit.ui.theme.SpliitMotion
import app.spliit.ui.theme.SpliitType
import app.spliit.ui.totals.TotalsTab
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.random.Random

private val GroupTabs = listOf("Expenses", "Balances", "Totals", "Information")

/** Where the group screen is, when it isn't showing a tab. */
private enum class GroupDestination { TABS, SEARCH, ACTIVITY, DETAIL }

/**
 * A group: its expenses, its balances, what it has spent, its notes, and who the person holding
 * the phone is in it.
 *
 * The tabs share one model, which is what lets an expense added on the first update the others
 * without any of them knowing about each other.
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
    var destination by remember { mutableStateOf(GroupDestination.TABS) }
    var query by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf<ExpenseFormPresentation?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<ExpenseDetails?>(null) }
    var detailFailure by remember { mutableStateOf<String?>(null) }
    var isLoadingDetail by remember { mutableStateOf(false) }

    LaunchedEffect(model) { model.start() }
    // A delete the user has already asked for must not be lost to leaving the screen.
    DisposableEffect(model) { onDispose { model.flushPendingDeletion() } }
    // Restarting on every keystroke cancels the run before it, which is what makes the wait
    // inside `search` a debounce rather than a delay.
    LaunchedEffect(query) { model.search(query) }

    val recentGroups by container.recentGroups.groups.collectAsState(initial = emptyList())
    val stored = recentGroups.firstOrNull { it.groupId == groupId }
    val group = model.group

    val openExpense: (String) -> Unit = { expenseId ->
        scope.launch {
            val expense = model.expense(expenseId) ?: return@launch
            sheet = ExpenseFormPresentation(
                draft = ExpenseFormDraft.editing(expense, group ?: return@launch),
                editing = expense,
                mintedId = expense.id,
            )
        }
    }

    // A tap on a row asks what it is before it asks how to change it, so the form is one step
    // further in than it used to be and the screen it opens onto can be read without editing.
    fun loadDetail(id: String) {
        isLoadingDetail = true
        detailFailure = null
        scope.launch {
            val loaded = model.expense(id)
            detail = loaded
            detailFailure = if (loaded == null) {
                "This expense couldn’t be loaded. It may have been deleted."
            } else {
                null
            }
            isLoadingDetail = false
        }
    }

    val openDetail: (String) -> Unit = { id ->
        detailId = id
        detail = null
        destination = GroupDestination.DETAIL
        loadDetail(id)
    }

    if (destination == GroupDestination.ACTIVITY) {
        ActivityLogScreen(
            model = model,
            onBack = {
                destination = GroupDestination.TABS
                tabIndex = GroupTabs.indexOf("Information").coerceAtLeast(0)
            },
            onEdit = openExpense,
        )
    } else if (destination == GroupDestination.DETAIL) {
        ExpenseDetailScreen(
            expense = detail,
            isLoading = isLoadingDetail,
            failure = detailFailure,
            formatter = model.moneyFormatter,
            actorId = model.actorId,
            participants = group?.participants.orEmpty(),
            onBack = { destination = GroupDestination.TABS },
            onRetry = { detailId?.let { loadDetail(it) } },
            onEdit = {
                val editing = detail
                val current = group
                if (editing != null && current != null) {
                    sheet = ExpenseFormPresentation(
                        draft = ExpenseFormDraft.editing(editing, current),
                        editing = editing,
                        mintedId = editing.id,
                    )
                }
            },
        )
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (destination == GroupDestination.SEARCH) {
                                    "Search"
                                } else {
                                    group?.name.orEmpty()
                                },
                                style = SpliitType.RowTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (destination == GroupDestination.TABS && group != null) {
                                // What the group is, where the screen's own tabs can't say it.
                                Text(
                                    text = groupStatus(group, model),
                                    style = SpliitType.Secondary,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                when (destination) {
                                    GroupDestination.SEARCH -> {
                                        destination = GroupDestination.TABS
                                        query = ""
                                    }

                                    else -> onBack()
                                }
                            },
                        ) {
                            Icon(
                                if (destination == GroupDestination.SEARCH) {
                                    Icons.Filled.Close
                                } else {
                                    Icons.AutoMirrored.Filled.ArrowBack
                                },
                                contentDescription = if (destination == GroupDestination.SEARCH) {
                                    "Close search"
                                } else {
                                    "Back"
                                },
                            )
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

                    // Search is a destination of its own rather than a page of the tab row, so it
                    // arrives like one: from the right, over the tabs, and leaves the same way.
                    group != null -> AnimatedContent(
                        targetState = destination,
                        transitionSpec = {
                            val forward = targetState == GroupDestination.SEARCH
                            val spec = SpliitMotion.spatialSlow<IntOffset>()
                            (slideInHorizontally(spec) { width ->
                                if (forward) width / 3 else -width / 3
                            } + fadeIn(SpliitMotion.effectsDefault())) togetherWith
                                (slideOutHorizontally(spec) { width ->
                                    if (forward) -width / 3 else width / 3
                                } + fadeOut(SpliitMotion.effectsFast()))
                        },
                        label = "group destination",
                    ) { target ->
                        if (target == GroupDestination.SEARCH) {
                            ExpenseSearchTab(
                                model = model,
                                query = query,
                                onQueryChange = { query = it },
                                onEdit = openDetail,
                            )
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                TabRow(selectedTabIndex = tabIndex) {
                                    GroupTabs.forEachIndexed { index, title ->
                                        Tab(
                                            selected = tabIndex == index,
                                            onClick = { tabIndex = index },
                                            // Four labels on one row: "Information" has to fit a
                                            // quarter of the width without breaking, so it is set a
                                            // size down.
                                            text = {
                                                Text(
                                                    text = title,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                )
                                            },
                                        )
                                    }
                                }

                                val identity = identityOf(stored, group)
                                Box(modifier = Modifier.weight(1f)) {
                                    // Only the page moves: the tab row above it has already
                                    // answered the finger, and sliding it too would read as the
                                    // whole screen jumping.
                                    AnimatedContent(
                                        targetState = tabIndex,
                                        transitionSpec = {
                                            val forward = targetState > initialState
                                            val spec = SpliitMotion.spatialDefault<IntOffset>()
                                            (slideInHorizontally(spec) { width ->
                                                val shift = width / 12
                                                if (forward) shift else -shift
                                            } + fadeIn(SpliitMotion.effectsDefault())) togetherWith
                                                (slideOutHorizontally(spec) { width ->
                                                    val shift = width / 12
                                                    if (forward) -shift else shift
                                                } + fadeOut(SpliitMotion.effectsFast()))
                                        },
                                        label = "tab page",
                                    ) { page ->
                                        when (page) {
                                            0 -> ExpenseListTab(
                                                model = model,
                                                onAdd = {
                                                    sheet = createPresentation(group, model, stored)
                                                },
                                                onEdit = openDetail,
                                                onSearch = {
                                                    destination = GroupDestination.SEARCH
                                                },
                                            )

                                            1 -> BalancesTab(
                                                model = model,
                                                identity = identity,
                                                identityIsNobody = stored?.activeParticipant ==
                                                    ActiveParticipant.Nobody,
                                                onIdentify = { pickerOpen = true },
                                                onSettle = { reimbursement ->
                                                    sheet = settlePresentation(
                                                        reimbursement,
                                                        group,
                                                        model,
                                                    )
                                                },
                                            )

                                            2 -> TotalsTab(
                                                model = model,
                                                identity = identity,
                                                identityIsNobody = stored?.activeParticipant ==
                                                    ActiveParticipant.Nobody,
                                                onIdentify = { pickerOpen = true },
                                            )

                                            else -> InformationTab(
                                                group = group,
                                                model = model,
                                                stored = stored,
                                                onIdentify = { pickerOpen = true },
                                                onOpenActivity = {
                                                    destination = GroupDestination.ACTIVITY
                                                },
                                            )
                                        }
                                    }
                                }
                            }
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
                // An edit from the detail screen has to come back to it, so the screen behind the
                // form isn't the version before the change.
                detailId?.let { loadDetail(it) }
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

/** "Settled up", or how many people the group has. Never a number with nothing behind it. */
private fun groupStatus(group: Group, model: GroupScreenModel): String {
    val balances = model.balances
    if (balances.isNotEmpty() && balances.values.all { it.total == 0 }) return "Settled up"
    val count = group.participants.size
    return if (count == 1) "1 participant" else "$count participants"
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
 * The identity a new expense starts from, the group's own facts, and the way to its history.
 */
@Composable
private fun InformationTab(
    group: Group,
    model: GroupScreenModel,
    stored: RecentGroup?,
    onIdentify: () -> Unit,
    onOpenActivity: () -> Unit,
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

        // The log belongs with the things this tab holds because it is something you look up
        // rather than work in — and there is no room for a fifth tab.
        Card {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenActivity),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Activity", style = SpliitType.RowTitle)
                    Text(
                        text = "Everything that has happened in this group, and who did it.",
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
