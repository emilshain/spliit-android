package app.spliit.ui.group

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import app.spliit.AppContainer
import app.spliit.data.local.ActiveParticipant
import app.spliit.data.local.RecentGroup
import app.spliit.data.model.ExpenseDetails
import app.spliit.data.model.Group
import app.spliit.data.model.Reimbursement
import app.spliit.data.remote.DeleteGroupInput
import app.spliit.data.remote.SpliitApi
import app.spliit.domain.CalendarDay
import app.spliit.domain.ExpenseFormDraft
import app.spliit.domain.GroupExport
import app.spliit.domain.GroupFormDraft
import app.spliit.domain.GroupLink
import app.spliit.domain.SplitwiseCsv
import app.spliit.domain.ParticipantDraft
import app.spliit.domain.ServerInstance
import app.spliit.ui.activity.ActivityTab
import app.spliit.ui.balances.BalancesTab
import app.spliit.ui.expenses.ExpenseDetailScreen
import app.spliit.ui.expenses.ExpenseFormSheet
import app.spliit.ui.expenses.ExpenseListTab
import app.spliit.ui.groups.ActiveUserPickerSheet
import app.spliit.ui.groups.CurrencyPickerDialog
import app.spliit.ui.search.ExpenseSearchTab
import app.spliit.ui.settlements.SettlementsScreen
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.ExpressiveMenu
import app.spliit.ui.theme.ExpressiveMenuItem
import app.spliit.ui.theme.ParticipantDot
import app.spliit.ui.theme.SkeletonBox
import app.spliit.ui.theme.SkeletonCard
import app.spliit.ui.theme.SkeletonLine
import app.spliit.ui.theme.SkeletonRow
import app.spliit.ui.theme.SkeletonSection
import app.spliit.ui.theme.SpliitDimens
import app.spliit.ui.theme.SpliitMotion
import app.spliit.ui.theme.pressScale
import app.spliit.ui.theme.SpliitType
import app.spliit.ui.totals.TotalsTab
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val GroupTabs = listOf("Expenses", "Balances", "Totals", "Activity")

/** Where the group screen is, when it isn't showing a tab. */
private enum class GroupDestination { TABS, SEARCH, SETTINGS, DETAIL, SETTLEMENTS }

/** The page the group screen is on, as far as the screen-to-screen transition is concerned. */
private enum class GroupPage { MAIN, SETTINGS, DETAIL, SETTLEMENTS }

/**
 * A group: its expenses, its balances, what it has spent, its notes, and who the person holding
 * the phone is in it.
 *
 * The tabs share one model, which is what lets an expense added on the first update the others
 * without any of them knowing about each other.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun GroupScreen(
    container: AppContainer,
    groupId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val model = remember(container, groupId) { GroupScreenModel(container, groupId, scope) }
    // One pager for the four tabs: tapping and swiping move the same state, so the pill has one
    // position to follow rather than two that could disagree.
    val pagerState = rememberPagerState(pageCount = { GroupTabs.size })
    var destination by remember { mutableStateOf(GroupDestination.TABS) }
    var query by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf<ExpenseFormPresentation?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    var confirmDeleteGroup by remember { mutableStateOf(false) }
    var isDeletingGroup by remember { mutableStateOf(false) }
    var deleteGroupFailure by remember { mutableStateOf<String?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<ExpenseDetails?>(null) }
    var detailFailure by remember { mutableStateOf<String?>(null) }
    var isLoadingDetail by remember { mutableStateOf(false) }
    var groupMenuOpen by remember { mutableStateOf(false) }
    var searchCollapsed by remember { mutableStateOf(false) }

    // Import and export used to live on the settings screen; they are things done to the group,
    // so they belong in the group's own menu. The state sits here for the same reason.
    val groupContext = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }
    var exportFailure by remember { mutableStateOf<String?>(null) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(0) }
    var importTotal by remember { mutableStateOf(0) }
    var pendingImport by remember { mutableStateOf<List<SplitwiseCsv.Row>?>(null) }
    var importFailure by remember { mutableStateOf<String?>(null) }
    var importSummary by remember { mutableStateOf<String?>(null) }

    val createCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val csv = pendingCsv
        pendingCsv = null
        if (uri == null || csv == null) return@rememberLauncherForActivityResult
        runCatching {
            groupContext.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(csv.toByteArray(Charsets.UTF_8))
            } ?: error("The chosen file could not be opened.")
        }.onFailure { error ->
            exportFailure = error.localizedMessage ?: "Couldn’t write the file."
        }
    }

    val openCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            groupContext.contentResolver.openInputStream(uri)?.use { stream ->
                SplitwiseCsv.parse(stream.readBytes().toString(Charsets.UTF_8))
            } ?: error("The chosen file could not be opened.")
        }.onSuccess { rows ->
            if (rows.isEmpty()) {
                importFailure = "No expenses were found in that file."
            } else {
                pendingImport = rows
            }
        }.onFailure { error ->
            importFailure = error.localizedMessage ?: "That file is not an expense CSV."
        }
    }

    fun importExpenses(rows: List<SplitwiseCsv.Row>) {
        pendingImport = null
        if (isImporting) return
        isImporting = true
        importTotal = rows.size
        importProgress = 0
        scope.launch {
            try {
                val outcome = model.importExpenses(rows) { done -> importProgress = done }
                importSummary = when {
                    outcome.created == 0 -> "Nothing was imported. ${outcome.firstFailure.orEmpty()}"
                    outcome.failed == 0 -> "${outcome.created} expenses imported."
                    else -> "${outcome.created} imported, ${outcome.failed} skipped. " +
                        outcome.firstFailure.orEmpty()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                importFailure = error.localizedMessage ?: "The import did not finish."
            } finally {
                isImporting = false
            }
        }
    }

    fun exportCsv() {
        if (isExporting) return
        isExporting = true
        scope.launch {
            try {
                val csv = model.exportSplitwiseCsv()
                pendingCsv = csv
                createCsv.launch(GroupExport.fileName(model.group?.name.orEmpty()))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                exportFailure = error.localizedMessage ?: "Couldn’t export the expenses."
            } finally {
                isExporting = false
            }
        }
    }

    LaunchedEffect(model) { model.start() }

    // Coming back into signal is the moment the backlog can go; it must not wait for the screen
    // to be reopened. Every reading that says a network is there is worth acting on: the model
    // does nothing when there is nothing waiting, and a missed change of state - the phone
    // reporting a network before it is usable, or two callbacks in a row - must not strand a
    // change on disk.
    LaunchedEffect(model) {
        container.connectivity.online.collect { online ->
            if (online) model.retryWhenBackOnline()
        }
    }
    // A delete the user has already asked for must not be lost to leaving the screen.
    DisposableEffect(model) { onDispose { model.flushPendingDeletion() } }
    // Restarting on every keystroke cancels the run before it, which is what makes the wait
    // inside `search` a debounce rather than a delay.
    LaunchedEffect(query) { model.search(query) }
    // The search icon belongs to the expenses page; any other tab takes it away.
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != 0) searchCollapsed = false
    }

    // Back leaves the page this screen opened, not the group: search, an expense and the group's
    // settings are all pages of this screen, and closing the group from one of them loses the
    // place the person was working in.
    BackHandler(enabled = destination != GroupDestination.TABS) {
        query = ""
        destination = GroupDestination.TABS
    }

    val recentGroups by container.recentGroups.groups.collectAsState(initial = emptyList())
    val stored = recentGroups.firstOrNull { it.groupId == groupId }
    val group = model.group
    // Who the person holding the phone is. The screens that read one side of the ledger need it.
    val identity = group?.let { identityOf(stored, it) }

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

    // The tabs and the search page are one page here: search grows out of the search bar
    // rather than arriving as a new screen. Settings, an expense and the settlements are pages
    // of their own, and push in from the trailing edge the way a stack does in SwiftUI.
    val page = when (destination) {
        GroupDestination.SETTINGS -> GroupPage.SETTINGS
        GroupDestination.DETAIL -> GroupPage.DETAIL
        GroupDestination.SETTLEMENTS -> GroupPage.SETTLEMENTS
        else -> GroupPage.MAIN
    }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val spatial = SpliitMotion.spatialSlow<IntOffset>()
            val forward = targetState != GroupPage.MAIN
            // Slides only, the way SwiftUI pushes: nothing fades, so no frame shows the page
            // behind through a half-transparent screen.
            if (forward) {
                slideInHorizontally(spatial) { it } togetherWith
                    slideOutHorizontally(spatial) { -it / 4 }
            } else {
                slideInHorizontally(spatial) { -it / 4 } togetherWith
                    slideOutHorizontally(spatial) { it }
            }
        },
        label = "group page",
    ) { page ->
    if (page == GroupPage.SETTINGS) {
        EditGroupScreen(
            group = group,
            model = model,
            stored = stored,
            onBack = { destination = GroupDestination.TABS },
            onIdentify = { pickerOpen = true },
        )
    } else if (page == GroupPage.DETAIL) {
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
            // Deleting from here is the list's delete: the row leaves the list and the undo
            // window it already had is the way back.
            onDelete = {
                detailId?.let { model.requestDelete(it) }
                destination = GroupDestination.TABS
            },
        )
    } else if (page == GroupPage.SETTLEMENTS) {
        SettlementsScreen(
            model = model,
            identity = identity,
            onBack = { destination = GroupDestination.TABS },
            onSettle = { reimbursement ->
                val current = group
                if (current != null) sheet = settlePresentation(reimbursement, current, model)
            },
        )
    } else {
        Scaffold(
            topBar = {
                // The bar is part of the header, not a thing floating over the pages: nothing
                // scrolls under it, so the first card can start where the header ends.
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
                            // What the group is, where the screen's own tabs can't say it.
                            // Shrunk rather than removed, so opening search does not make the
                            // whole page jump up by a line.
                            AnimatedVisibility(
                                visible = destination == GroupDestination.TABS && group != null,
                                enter = expandVertically(SpliitMotion.spatialDefault()) +
                                    fadeIn(SpliitMotion.effectsFast()),
                                exit = shrinkVertically(SpliitMotion.spatialFast()) +
                                    fadeOut(SpliitMotion.effectsFast()),
                            ) {
                                group?.let { GroupSubtitle(group = it, model = model) }
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
                    actions = {
                        if (destination == GroupDestination.TABS && group != null) {
                            // The search bar scrolls under the header, so the header carries a
                            // small search of its own once it does.
                            if (searchCollapsed && pagerState.currentPage == 0) {
                                IconButton(onClick = { destination = GroupDestination.SEARCH }) {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = "Search expenses",
                                    )
                                }
                            }
                            // Everything done to the group rather than inside it, in one menu:
                            // handing it on, changing what it is, moving its expenses in and out,
                            // and getting rid of it.
                            Box {
                                IconButton(onClick = { groupMenuOpen = true }) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "Group actions",
                                    )
                                }
                                ExpressiveMenu(
                                    expanded = groupMenuOpen,
                                    onDismissRequest = { groupMenuOpen = false },
                                ) {
                                    ExpressiveMenuItem(
                                        text = "Share",
                                        leadingIcon = Icons.Filled.Share,
                                        onClick = {
                                            groupMenuOpen = false
                                            shareOpen = true
                                        },
                                    )
                                    ExpressiveMenuItem(
                                        text = "Edit group",
                                        leadingIcon = Icons.Filled.Edit,
                                        onClick = {
                                            groupMenuOpen = false
                                            destination = GroupDestination.SETTINGS
                                        },
                                    )
                                    ExpressiveMenuItem(
                                        text = if (isImporting) {
                                            "Importing $importProgress of $importTotal…"
                                        } else {
                                            "Import from CSV"
                                        },
                                        leadingIcon = Icons.Filled.UploadFile,
                                        onClick = {
                                            groupMenuOpen = false
                                            openCsv.launch(
                                                arrayOf(
                                                    "text/csv",
                                                    "text/comma-separated-values",
                                                    "text/plain",
                                                ),
                                            )
                                        },
                                    )
                                    ExpressiveMenuItem(
                                        text = if (isExporting) "Preparing the file…" else "Export as CSV",
                                        leadingIcon = Icons.Filled.FileDownload,
                                        onClick = {
                                            groupMenuOpen = false
                                            exportCsv()
                                        },
                                    )
                                    ExpressiveMenuItem(
                                        text = "Delete group",
                                        leadingIcon = Icons.Filled.Delete,
                                        destructive = true,
                                        onClick = {
                                            groupMenuOpen = false
                                            confirmDeleteGroup = true
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
                    // Grown and shrunk rather than added and removed: opening search must not
                    // move everything below the header in a single frame.
                    AnimatedVisibility(
                        visible = destination == GroupDestination.TABS,
                        enter = expandVertically(SpliitMotion.spatialDefault()) +
                            fadeIn(SpliitMotion.effectsDefault()),
                        exit = shrinkVertically(SpliitMotion.spatialFast()) +
                            fadeOut(SpliitMotion.effectsFast()),
                    ) {
                        GroupPillTabs(
                            selected = pagerState.currentPage,
                            // The pill rides the page as it is dragged, instead of waiting for
                            // it to settle and then catching up.
                            position = pagerState.currentPage +
                                pagerState.currentPageOffsetFraction,
                            onSelect = { page ->
                                scope.launch { pagerState.animateScrollToPage(page) }
                            },
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding),
            ) {
                when {
                    group == null && model.isLoadingGroup -> GroupPageSkeleton()

                    group == null && model.didFailToLoad -> EmptyState(
                        art = EmptyStateArt.Icon(Icons.Filled.Warning),
                        title = "Couldn’t load the group",
                        description = model.loadFailure,
                    ) {
                        Button(onClick = { model.retry() }) { Text("Try again") }
                    }

                    // Search is the same page as the tabs, with the bar grown into a field.
                    group != null -> SharedTransitionLayout {
                        AnimatedContent(
                            targetState = destination,
                            transitionSpec = {
                                // Opening search crossfades under the field that grew into it.
                                // Closing it fades both ways on the fast spring: the search
                                // page's backdrop used to hang over the list until the incoming
                                // page had finished arriving.
                                if (targetState == GroupDestination.SEARCH) {
                                    fadeIn(SpliitMotion.effectsDefault()) togetherWith
                                        ExitTransition.None
                                } else {
                                    fadeIn(SpliitMotion.effectsFast()) togetherWith
                                        fadeOut(SpliitMotion.effectsFast())
                                }
                            },
                            label = "group destination",
                        ) { target ->
                            // Both AnimatedContents here are the same scope type, so take this
                            // one's before stepping into the shared-transition scope below.
                            val visibility = this
                            val morph = with(this@SharedTransitionLayout) {
                                Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState("group-search"),
                                    animatedVisibilityScope = visibility,
                                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                    enter = fadeIn(SpliitMotion.effectsDefault()),
                                    exit = fadeOut(SpliitMotion.effectsFast()),
                                )
                            }
                            if (target == GroupDestination.SEARCH) {
                                ExpenseSearchTab(
                                    model = model,
                                    query = query,
                                    onQueryChange = { query = it },
                                    onEdit = openDetail,
                                    fieldMorph = morph,
                                )
                            } else {
                            // A pull anywhere on the group reloads it: the four pages are one
                            // subject, and a gesture that only worked on the first of them (and
                            // not on an empty list) read as broken.
                            val refreshState = rememberPullToRefreshState()
                            PullToRefreshBox(
                                isRefreshing = model.isRefreshing,
                                onRefresh = { model.refresh() },
                                state = refreshState,
                                indicator = {
                                    PullToRefreshDefaults.Indicator(
                                        state = refreshState,
                                        isRefreshing = model.isRefreshing,
                                        modifier = Modifier.align(Alignment.TopCenter),
                                    )
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                // The pages themselves are the gesture: a swipe moves one page
                                // across, and the pill in the header answers before they settle.
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                ) { page ->
                                    when (page) {
                                        0 -> ExpenseListTab(
                                            model = model,
                                            searchBarMorph = morph,
                                            onAdd = {
                                                sheet = createPresentation(group, model, stored)
                                            },
                                            onEdit = openDetail,
                                            onSearch = {
                                                destination = GroupDestination.SEARCH
                                            },
                                            onSearchCollapsed = { searchCollapsed = it },
                                        )

                                        1 -> BalancesTab(
                                            model = model,
                                            identity = identity,
                                            identityIsNobody = stored?.activeParticipant ==
                                                ActiveParticipant.Nobody,
                                            onIdentify = { pickerOpen = true },
                                            onOpenSettlements = {
                                                destination = GroupDestination.SETTLEMENTS
                                            },
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

                                        else -> ActivityTab(
                                            model = model,
                                            onEdit = openExpense,
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

    if (confirmDeleteGroup && group != null) {
        val current = group
        AlertDialog(
            onDismissRequest = { if (!isDeletingGroup) confirmDeleteGroup = false },
            title = { Text("Delete this group?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        // The link keeps working through the grace period — the group is only
                        // given a deletion date — so this must not promise that access ends now.
                        "“${current.name}” and its expenses will be scheduled for deletion. " +
                            "Until then, its link still works.",
                    )
                    // The server's own rule, said where the decision is made: `groups.delete`
                    // only sets a deletion date, and a purge deletes what is left after it.
                    Text(
                        "Deleting a group starts a 30-day deactivation period. After that, all " +
                            "of its data is permanently deleted and unrecoverable.",
                        style = SpliitType.Secondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeletingGroup,
                    onClick = {
                        isDeletingGroup = true
                        scope.launch {
                            try {
                                container.clientForGroup(groupId).call(
                                    SpliitApi.deleteGroup(
                                        DeleteGroupInput(
                                            groupId = groupId,
                                            groupName = current.name,
                                            participantId = model.actorId,
                                        ),
                                    ),
                                )
                                container.recentGroups.forget(groupId)
                                container.groupCache.forget(groupId)
                                confirmDeleteGroup = false
                                onBack()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                deleteGroupFailure =
                                    error.localizedMessage ?: "Couldn’t delete the group."
                            } finally {
                                isDeletingGroup = false
                            }
                        }
                    },
                ) {
                    Text(
                        text = if (isDeletingGroup) "Deleting…" else "Delete",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeletingGroup,
                    onClick = { confirmDeleteGroup = false },
                ) { Text("Cancel") }
            },
        )
    }

    deleteGroupFailure?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteGroupFailure = null },
            title = { Text("Couldn’t delete the group") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { deleteGroupFailure = null }) { Text("OK") }
            },
        )
    }

    if (shareOpen && group != null) {
        GroupShareSheet(
            groupName = group.name,
            url = GroupLink.shareUrl(
                groupId = groupId,
                instanceUrl = model.instanceUrl ?: ServerInstance.DEFAULT_BASE_URL,
            ),
            onDismiss = { shareOpen = false },
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

    pendingImport?.let { rows ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import ${rows.size} expenses?") },
            text = {
                Text(
                    "They are added to this group as new expenses, with the people and the " +
                        "split the file states. Importing twice duplicates them.",
                )
            },
            confirmButton = { TextButton(onClick = { importExpenses(rows) }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }

    importSummary?.let { message ->
        MessageDialog(
            title = "Imported",
            message = message,
            onDismiss = { importSummary = null },
        )
    }

    importFailure?.let { message ->
        MessageDialog(
            title = "Couldn’t import the expenses",
            message = message,
            onDismiss = { importFailure = null },
        )
    }

    exportFailure?.let { message ->
        MessageDialog(
            title = "Couldn’t export the expenses",
            message = message,
            onDismiss = { exportFailure = null },
        )
    }
}

/**
 * The tabs as one pill, with the selection riding inside it.
 *
 * A stock TabRow draws the selection as an underline, which reads as "page". A pill in a track
 * reads as "choice", and the change of page is something the thumb sees before the content lands.
 */
@Composable
private fun GroupPillTabs(
    selected: Int,
    position: Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // No scrim and no fade: the bar sits on its own row of the header, with nothing passing
    // behind it to hide.
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
        // 48dp: a tab is a touch target before it is a pill.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            val itemWidth = maxWidth / GroupTabs.size
            Box(
                modifier = Modifier
                    .offset(x = itemWidth * position)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50)),
            )
            Row(modifier = Modifier.fillMaxSize()) {
                GroupTabs.forEachIndexed { index, title ->
                    // A tab is a choice, not a button: the ripple washing over the pill read as
                    // a press on glass. It dips under the thumb and springs back instead.
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            // A tab is a choice in a contained track, not a button: the ripple
                            // washing over the pill read as a press on glass. It dips instead.
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                            ) { onSelect(index) }
                            .pressScale(interaction, pressedScale = 0.94f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (index == selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
        }
        Spacer(Modifier.height(SpliitDimens.TabBarGap))
    }
}

/**
 * How many people are in the group, and whether this is a copy.
 *
 * The count is the one fact the header can give about a group, and it is as true offline as the
 * snapshot it came from; the word beside it says how fresh that snapshot is.
 */
@Composable
private fun GroupSubtitle(group: Group, model: GroupScreenModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Filled.People,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The count is the point: it is as true offline as the snapshot it came from, so it is
        // shown whether or not the server answered, with a word when it did not.
        Text(
            text = group.participants.size.toString(),
            style = SpliitType.Secondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (model.isOffline) {
            Text(
                text = "· offline",
                style = SpliitType.Secondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

/** The group's own facts, one step in from the gear in the toolbar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditGroupScreen(
    group: Group?,
    model: GroupScreenModel,
    stored: RecentGroup?,
    onBack: () -> Unit,
    onIdentify: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("Edit group", style = SpliitType.Title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (group == null) {
                SettingsSkeleton()
            } else {
                SettingsTab(
                    group = group,
                    model = model,
                    stored = stored,
                    onIdentify = onIdentify,
                )
            }
        }
    }
}

/**
 * The group's own facts, the identity a new expense starts from, and the two things that change
 * the group itself rather than anything in it.
 */
@Composable
private fun SettingsTab(
    group: Group,
    model: GroupScreenModel,
    stored: RecentGroup?,
    onIdentify: () -> Unit,
) {
    val identity = identityOf(stored, group)
    val scope = rememberCoroutineScope()

    // The group's own facts, edited where they are read: the fields are the screen.
    var draft by remember(group.id) { mutableStateOf(GroupFormDraft.editing(group)) }
    var hasAttemptedSave by remember(group.id) { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveFailure by remember { mutableStateOf<String?>(null) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    val isDirty = draft != GroupFormDraft.editing(group)

    fun save() {
        hasAttemptedSave = true
        if (!draft.isValid || !isDirty || isSaving) return
        isSaving = true
        saveFailure = null
        scope.launch {
            try {
                model.updateGroup(draft.formValues)
                draft = GroupFormDraft.editing(model.group ?: group)
                hasAttemptedSave = false
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                saveFailure = error.localizedMessage ?: "Couldn’t save the group."
            } finally {
                isSaving = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        // Every card is a side of the one above or below it, so the page reads as one grouped
        // list rather than a scattering of tiles.
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Card(first = true) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Group", style = SpliitType.SectionHeader)
                        Spacer(Modifier.weight(1f))
                        if (isDirty) {
                            TextButton(
                                enabled = !isSaving,
                                onClick = {
                                    draft = GroupFormDraft.editing(group)
                                    hasAttemptedSave = false
                                },
                            ) { Text("Reset") }
                        }
                        Button(onClick = { save() }, enabled = isDirty && !isSaving) {
                            Text(if (isSaving) "Saving…" else "Save")
                        }
                    }

                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                        label = { Text("Group name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (hasAttemptedSave) {
                        draft.problems.firstOrNull {
                            it is GroupFormDraft.Problem.NameTooShort ||
                                it is GroupFormDraft.Problem.NameTooLong
                        }?.let { FieldProblem(it.message) }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCurrencyPicker = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Currency", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = settingsCurrencySummary(draft),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (draft.usesCustomSymbol) {
                        OutlinedTextField(
                            value = draft.currency,
                            onValueChange = { draft = draft.copy(currency = it) },
                            label = { Text("Currency symbol") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (hasAttemptedSave) {
                            draft.problems.firstOrNull {
                                it is GroupFormDraft.Problem.CurrencyMissing ||
                                    it is GroupFormDraft.Problem.CurrencyTooLong
                            }?.let { FieldProblem(it.message) }
                        }
                    }
                    if (
                        hasAttemptedSave &&
                        draft.problems.contains(GroupFormDraft.Problem.CurrencyCodeInvalid)
                    ) {
                        FieldProblem(GroupFormDraft.Problem.CurrencyCodeInvalid.message)
                    }

                    OutlinedTextField(
                        value = draft.information,
                        onValueChange = { draft = draft.copy(information = it) },
                        label = { Text("Notes") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Participants", style = SpliitType.SectionHeader)
                    draft.participants.forEachIndexed { index, participant ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ParticipantDot(position = index, size = 9.dp)
                                OutlinedTextField(
                                    value = participant.name,
                                    onValueChange = { newName ->
                                        draft = draft.copy(
                                            participants = draft.participants.map {
                                                if (it.id == participant.id) {
                                                    it.copy(name = newName)
                                                } else {
                                                    it
                                                }
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
                                            participants = draft.participants.filterNot {
                                                it.id == participant.id
                                            },
                                        )
                                    },
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Remove participant",
                                    )
                                }
                            }
                            if (hasAttemptedSave) {
                                draft.problemsForParticipant(participant.id).firstOrNull()
                                    ?.let { FieldProblem(it.message) }
                            }
                        }
                    }
                    if (hasAttemptedSave &&
                        draft.problems.contains(GroupFormDraft.Problem.NoParticipants)
                    ) {
                        FieldProblem(GroupFormDraft.Problem.NoParticipants.message)
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
                }
            }

        // Who the person holding the phone is in this group: the identity a new expense starts
        // from, and the only thing the app needs to answer "am I owed?".
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

        // The group's own history, last: it is read once, not changed here.
        Card(last = true) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Fact("Created", dateLabel(group.createdAt))
                model.instanceUrl?.let { Fact("Server", ServerInstance.displayName(it)) }
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

    saveFailure?.let { message ->
        MessageDialog(
            title = "Couldn’t save the group",
            message = message,
            onDismiss = { saveFailure = null },
        )
    }

}

@Composable
private fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

/** A problem with one field, under it and only after a save was attempted. */
@Composable
private fun FieldProblem(text: String) {
    Text(
        text = text,
        style = SpliitType.Secondary,
        color = MaterialTheme.colorScheme.error,
    )
}

/** What the currency row shows on the right: the name and symbol, or the symbol alone. */
private fun settingsCurrencySummary(draft: GroupFormDraft): String {
    val currency = draft.selectedCurrency()
    return when {
        currency != null -> "${currency.name} (${currency.symbol})"
        !draft.currencyCode.isNullOrEmpty() -> draft.currencyCode!!
        else -> draft.currency
    }
}

@Composable
private fun Card(
    first: Boolean = false,
    last: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = if (first) SpliitDimens.CardCorner else SpliitDimens.AdjacentCorner,
            topEnd = if (first) SpliitDimens.CardCorner else SpliitDimens.AdjacentCorner,
            bottomStart = if (last) SpliitDimens.CardCorner else SpliitDimens.AdjacentCorner,
            bottomEnd = if (last) SpliitDimens.CardCorner else SpliitDimens.AdjacentCorner,
        ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Box(modifier = Modifier.padding(SpliitDimens.CardPadding)) { content() }
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

/** The group page before its group arrives: title, tab track, first rows. */
@Composable
private fun GroupPageSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        SkeletonLine(
            modifier = Modifier.padding(start = 20.dp, top = 20.dp),
            widthFraction = 0.45f,
            height = 26.dp,
        )
        SkeletonBox(
            modifier = Modifier
                .padding(
                    start = SpliitDimens.ScreenGutter,
                    end = SpliitDimens.ScreenGutter,
                    top = SpliitDimens.CardPadding + SpliitDimens.CardSpacing / 2,
                )
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(50),
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(5) { SkeletonRow() }
        }
    }
}

/** The settings form before the group arrives. */
@Composable
private fun SettingsSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SkeletonSection(modifier = Modifier.padding(bottom = 8.dp))
        SkeletonCard(height = 180.dp, shape = RoundedCornerShape(SpliitDimens.CardCorner))
        SkeletonCard(height = 56.dp, shape = RoundedCornerShape(SpliitDimens.AdjacentCorner))
        SkeletonCard(height = 76.dp, shape = RoundedCornerShape(SpliitDimens.AdjacentCorner))
        SkeletonCard(height = 56.dp, shape = RoundedCornerShape(SpliitDimens.CardCorner))
    }
}
