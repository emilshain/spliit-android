package app.spliit.ui.groups

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.spliit.AppContainer
import app.spliit.data.local.RecentGroup
import app.spliit.data.model.GroupSummary
import app.spliit.data.remote.SpliitApi
import app.spliit.domain.ServerInstance
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.Monogram
import app.spliit.ui.theme.MonogramPalette
import app.spliit.ui.theme.SpliitType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The home screen: the groups this device remembers, with participant counts and dates fetched
 * from the instance.
 *
 * The list itself is local — Spliit has no accounts — so it renders immediately and the server
 * details fill in. A server that can't be reached costs detail, not the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsListScreen(
    container: AppContainer,
    onOpenGroup: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val model = remember(container) { GroupsListModel(container, scope) }
    var sheet by remember { mutableStateOf<GroupsSheet>(GroupsSheet.None) }

    LaunchedEffect(model) { model.start() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                // The app's own name, once: the list below is plainly the groups, and a heading
                // saying so would be a label on the obvious.
                title = { Text("Spliit", style = SpliitType.Title) },
            )
        },
        floatingActionButton = {
            AddGroupButton(
                onCreate = { sheet = GroupsSheet.Create },
                onAddByLink = { sheet = GroupsSheet.AddByLink },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (model.groups.isEmpty()) {
                EmptyState(
                    art = EmptyStateArt.Logo,
                    title = "Welcome to Spliit",
                    description = "Create a group to start splitting expenses with friends, " +
                        "or add one that was shared with you.",
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // The one thing most people opening this screen are here to do, at a size
                        // that says so: full width, above the pair below it.
                        Button(
                            onClick = { sheet = GroupsSheet.Create },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Create group", style = SpliitType.RowTitle)
                        }
                        OutlinedButton(
                            onClick = { sheet = GroupsSheet.AddByLink },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Add by link")
                        }
                    }
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = model.isRefreshing,
                    onRefresh = { model.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    GroupsList(model = model, onOpenGroup = onOpenGroup)
                }
            }
        }
    }

    when (sheet) {
        GroupsSheet.None -> Unit
        GroupsSheet.Create -> {
            val defaultInstance by container.defaultInstanceUrl.collectAsState(
                initial = ServerInstance.DEFAULT_BASE_URL,
            )
            CreateGroupSheet(
                container = container,
                defaultInstanceUrl = defaultInstance,
                onDismiss = { sheet = GroupsSheet.None },
                onCreated = { group ->
                    sheet = GroupsSheet.None
                    onOpenGroup(group.groupId)
                },
            )
        }

        GroupsSheet.AddByLink -> AddGroupLinkSheet(
            container = container,
            onDismiss = { sheet = GroupsSheet.None },
            onAdded = { sheet = GroupsSheet.None },
        )
    }
}

private enum class GroupsSheet { None, Create, AddByLink }

/**
 * The one way in to a new group, at the bottom right where an add belongs.
 *
 * It opens onto the two kinds of adding — making one, or pasting a link to one — because the
 * difference is a decision, not something to hide behind a guess.
 */
@Composable
private fun AddGroupButton(onCreate: () -> Unit, onAddByLink: () -> Unit) {
    Box {
        var open by remember { mutableStateOf(false) }

        FloatingActionButton(
            onClick = { open = true },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add group")
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Create group") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    open = false
                    onCreate()
                },
            )
            DropdownMenuItem(
                text = { Text("Add by link") },
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                onClick = {
                    open = false
                    onAddByLink()
                },
            )
        }
    }
}

@Composable
private fun GroupsList(model: GroupsListModel, onOpenGroup: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val starred = model.groups.filter { it.isStarred && !it.isArchived }
    val recent = model.groups.filter { !it.isStarred && !it.isArchived }
    val archived = model.groups.filter { it.isArchived }
    val container = model.container

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        model.errorMessage?.let { message ->
            item(key = "error") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = message,
                        style = SpliitType.Secondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Three sections, each absent until it has something in it — so a list with nothing
        // starred or archived looks exactly as it did before either existed.
        if (starred.isNotEmpty()) {
            item(key = "starred") { SectionHeader("Starred") }
            items(starred, key = { it.groupId }) { group ->
                GroupRow(
                    group = group,
                    summary = model.summaries[group.groupId],
                    onClick = { onOpenGroup(group.groupId) },
                    onStar = { scope.launch { container.recentGroups.setStarred(!group.isStarred, group.groupId) } },
                    onArchive = { scope.launch { container.recentGroups.setArchived(!group.isArchived, group.groupId) } },
                    onRemove = { scope.launch { container.recentGroups.forget(group.groupId) } },
                )
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "recent") { SectionHeader("Recent groups") }
            items(recent, key = { it.groupId }) { group ->
                GroupRow(
                    group = group,
                    summary = model.summaries[group.groupId],
                    onClick = { onOpenGroup(group.groupId) },
                    onStar = { scope.launch { container.recentGroups.setStarred(!group.isStarred, group.groupId) } },
                    onArchive = { scope.launch { container.recentGroups.setArchived(!group.isArchived, group.groupId) } },
                    onRemove = { scope.launch { container.recentGroups.forget(group.groupId) } },
                )
            }
        }

        if (archived.isNotEmpty()) {
            item(key = "archived") { SectionHeader("Archived") }
            items(archived, key = { it.groupId }) { group ->
                GroupRow(
                    group = group,
                    summary = model.summaries[group.groupId],
                    onClick = { onOpenGroup(group.groupId) },
                    onStar = { scope.launch { container.recentGroups.setStarred(!group.isStarred, group.groupId) } },
                    onArchive = { scope.launch { container.recentGroups.setArchived(!group.isArchived, group.groupId) } },
                    onRemove = { scope.launch { container.recentGroups.forget(group.groupId) } },
                )
            }
        }

        // Room for the last card to clear the add button.
        item(key = "bottom") { Spacer(Modifier.padding(bottom = 96.dp)) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = SpliitType.SectionHeader,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

/**
 * A group, and the three things that can be done to it.
 *
 * Star and archive sit on opposite edges because they are opposite acts; the same three actions
 * are in a long-press menu because a gesture nobody discovers is a feature nobody has. A full
 * swipe from the trailing edge archives rather than removes: removing is the one thing here
 * with no way back but the original link.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun GroupRow(
    group: RecentGroup,
    summary: GroupSummary?,
    onClick: () -> Unit,
    onStar: () -> Unit,
    onArchive: () -> Unit,
    onRemove: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onStar()
                SwipeToDismissBoxValue.EndToStart -> onArchive()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // The row stays: these are toggles, not a dismissal, and the list re-renders from
            // the store with the new flag.
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val isStar = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (isStar) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        imageVector = if (isStar) Icons.Filled.Star else Icons.Filled.Archive,
                        contentDescription = null,
                        tint = if (isStar) Color(0xFFFFCC00) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        Box {
            var menuOpen by remember { mutableStateOf(false) }

            // A card rather than a bare row: the list is a set of things, and each one is
            // something you open.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = { menuOpen = true },
                        )
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Seeding on the ID rather than the name keeps a group's colour across a rename.
                    Monogram(
                        name = group.groupName,
                        colorIndex = MonogramPalette.indexFor(group.groupId),
                        size = 42.dp,
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = group.groupName,
                            style = SpliitType.RowTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = metaLine(summary),
                            style = SpliitType.Secondary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (group.isStarred) "Unstar" else "Star") },
                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onStar()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (group.isArchived) "Unarchive" else "Archive") },
                    leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onArchive()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Remove") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }
    }
}

/** "Nov 6, 2026 · 4 participants" — what the card can say that its title can't. */
private fun metaLine(summary: GroupSummary?): String {
    if (summary == null) return "…"
    return listOf(dateLabel(summary.createdAt), participantText(summary)).joinToString(" · ")
}

/**
 * Localised, and pluralised here only because the app has no translation yet: the count of a
 * group's members is the one string in this list that changes shape.
 */
private fun participantText(summary: GroupSummary?): String {
    val count = summary?.participantCount ?: return "…"
    return if (count == 1) "1 participant" else "$count participants"
}

/** "Nov 24, 2026", in the user's own date format. */
private fun dateLabel(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(instant.atZone(ZoneId.systemDefault()).toLocalDate())

/**
 * What the list knows, and what the servers told it.
 *
 * Fetches every instance's groups at once and keeps whatever comes back. A server that is down
 * costs its own groups their detail and nothing else.
 */
@Stable
class GroupsListModel(
    val container: AppContainer,
    private val scope: CoroutineScope,
) {
    var groups by mutableStateOf<List<RecentGroup>>(emptyList())
        private set
    var summaries by mutableStateOf<Map<String, GroupSummary>>(emptyMap())
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    private var refreshJob: Job? = null

    /** Reloads whenever the remembered groups change, as the iOS list does. */
    fun start() {
        scope.launch {
            container.recentGroups.groups.collect { remembered ->
                groups = remembered
                refresh()
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            isRefreshing = true
            try {
                load()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.localizedMessage
            } finally {
                isRefreshing = false
            }
        }
    }

    private suspend fun load() {
        val remembered = container.recentGroups.snapshot()
        if (remembered.isEmpty()) {
            summaries = emptyMap()
            errorMessage = null
            return
        }

        val fallback = container.defaultInstance()
        val byInstance = remembered.groupBy { it.instanceURL ?: fallback }
        val found = mutableMapOf<String, GroupSummary>()
        val unreachable = mutableListOf<String>()

        coroutineScope {
            byInstance.entries
                .map { (instance, rows) ->
                    async {
                        val response = try {
                            container.client(instance).call(SpliitApi.groups(rows.map { it.groupId }))
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            null
                        }
                        instance to response
                    }
                }
                .awaitAll()
                .forEach { (instance, response) ->
                    if (response == null) {
                        unreachable += ServerInstance.displayName(instance)
                    } else {
                        response.groups.forEach { found[it.id] = it }
                    }
                }
        }

        summaries = found
        errorMessage = if (unreachable.isEmpty()) {
            null
        } else {
            // The names are stored locally, so the list is still usable — say what's missing
            // rather than replacing everything with an error screen.
            val names = unreachable.distinct().sorted().joinToString(" and ")
            "Couldn’t reach $names, so group details may be out of date."
        }
    }
}
