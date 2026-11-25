package app.spliit.ui.groups

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.spliit.AppContainer
import app.spliit.data.local.RecentGroup
import app.spliit.data.model.GroupSummary
import app.spliit.data.remote.SpliitApi
import app.spliit.domain.ServerInstance
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.ExpressiveMenu
import app.spliit.ui.theme.ExpressiveMenuItem
import app.spliit.ui.theme.MenuPlacement
import app.spliit.ui.theme.Monogram
import app.spliit.ui.theme.MonogramPalette
import app.spliit.ui.theme.pressScale
import app.spliit.ui.theme.SpliitElevation
import app.spliit.ui.theme.SpliitMotion
import app.spliit.ui.theme.SpliitType
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
            // The app's own name, once: the list below is plainly the groups, and a heading
            // saying so would be a label on the obvious. A plain headline rather than an app
            // bar: there is no navigation or action up here, and the bar's fixed 64dp box left
            // the title floating in a band of empty space. Sized to the text, it sits on the
            // system inset and the list starts straight under it.
            Text(
                text = "Spliit",
                style = SpliitType.Title,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
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
                onSaved = { group ->
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
 * Tapping it unfolds the two kinds of adding — making one, or pasting a link to one — as pills
 * that rise out of the button, staggered from the one nearest it upwards. The plus turns into a
 * close, and the same button folds them away again.
 */
@Composable
private fun AddGroupButton(onCreate: () -> Unit, onAddByLink: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(emptyList<Int>()) }
    val fabInteraction = remember { MutableInteractionSource() }

    val actions = remember(onCreate, onAddByLink) {
        listOf(
            FabAction("Create group", Icons.Filled.Add, onCreate),
            FabAction("Add by link", Icons.Filled.Link, onAddByLink),
        )
    }

    // Revealed from the button upwards, one per beat: the stack unfolds instead of appearing
    // all at once. Closing clears them together, because a stagger on the way out reads as lag.
    LaunchedEffect(open) {
        if (!open) {
            revealed = emptyList()
            return@LaunchedEffect
        }
        for (index in actions.indices.reversed()) {
            revealed = revealed + index
            delay(50)
        }
    }

    val rotation by animateFloatAsState(
        targetValue = if (open) 45f else 0f,
        animationSpec = SpliitMotion.snappy(),
        label = "add button rotation",
    )
    val containerColor by animateColorAsState(
        targetValue = if (open) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = SpliitMotion.smooth(),
        label = "add button container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (open) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        animationSpec = SpliitMotion.smooth(),
        label = "add button content",
    )

    Column(
        modifier = Modifier.padding(end = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        actions.forEachIndexed { index, action ->
            AnimatedVisibility(
                visible = index in revealed,
                enter = scaleIn(
                    animationSpec = SpliitMotion.snappy(),
                    initialScale = 0.86f,
                    transformOrigin = TransformOrigin(1f, 1f),
                ) + fadeIn(SpliitMotion.smooth()),
                exit = fadeOut(SpliitMotion.effectsFast()),
            ) {
                Surface(
                    onClick = {
                        open = false
                        action.onClick()
                    },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .shadow(elevation = SpliitElevation.FloatingAction, shape = RoundedCornerShape(50)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(action.label, style = SpliitType.RowTitle)
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { open = !open },
            interactionSource = fabInteraction,
            // Off the Scaffold's own 16dp corner by the same 4dp/8dp the expense list's add
            // button sits in from its edges, so the two land in the same place on screen.
            modifier = Modifier.pressScale(fabInteraction),
            shape = RoundedCornerShape(18.dp),
            containerColor = containerColor,
            contentColor = contentColor,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = if (open) "Close" else "Add group",
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

private data class FabAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

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
                color = MaterialTheme.colorScheme.surfaceContainerLow,
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

            ExpressiveMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                minWidth = 200.dp,
            ) {
                ExpressiveMenuItem(
                    text = if (group.isStarred) "Unstar" else "Star",
                    leadingIcon = Icons.Filled.Star,
                    onClick = {
                        menuOpen = false
                        onStar()
                    },
                )
                ExpressiveMenuItem(
                    text = if (group.isArchived) "Unarchive" else "Archive",
                    leadingIcon = Icons.Filled.Archive,
                    onClick = {
                        menuOpen = false
                        onArchive()
                    },
                )
                ExpressiveMenuItem(
                    text = "Remove",
                    leadingIcon = Icons.Filled.Delete,
                    destructive = true,
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
