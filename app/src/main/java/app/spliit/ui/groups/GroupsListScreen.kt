package app.spliit.ui.groups

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.spliit.AppContainer
import app.spliit.R
import app.spliit.data.local.AppearancePalette
import app.spliit.data.local.RecentGroup
import app.spliit.data.local.ThemeChoice
import app.spliit.data.model.GroupSummary
import app.spliit.data.remote.SpliitApi
import app.spliit.domain.ServerInstance
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.ExpressiveMenu
import app.spliit.ui.theme.ExpressiveMenuItem
import app.spliit.ui.theme.ExpressiveSwipe
import app.spliit.ui.theme.SwipeAction
import app.spliit.ui.theme.MenuPlacement
import app.spliit.ui.theme.Monogram
import app.spliit.ui.theme.MonogramPalette
import app.spliit.ui.theme.PaletteTemplates
import app.spliit.ui.theme.pressScale
import app.spliit.ui.theme.SearchPill
import app.spliit.ui.theme.SpliitElevation
import app.spliit.ui.theme.SpliitMotion
import app.spliit.ui.theme.SpliitPalette
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
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var appearanceOpen by remember { mutableStateOf(false) }
    val themeChoice by container.appearance.theme.collectAsState(initial = ThemeChoice.System)
    val dynamicColor by container.appearance.dynamicColor.collectAsState(initial = true)
    val palette by container.appearance.palette.collectAsState(initial = AppearancePalette.Spliit)

    LaunchedEffect(model) { model.start() }

    // A start with nothing on disk reads in a frame or two, and drawing a splash for it would be
    // slower than the wait it hides. The mark appears only if the read is genuinely slow.
    var slowStart by remember { mutableStateOf(false) }
    LaunchedEffect(model.hasLoadedGroups) {
        if (model.hasLoadedGroups) return@LaunchedEffect
        delay(240)
        slowStart = true
    }

    Scaffold(
        topBar = {
            // The app's own name, once: the list below is plainly the groups, and a heading
            // saying so would be a label on the obvious. The search sits beside it, because a
            // group you already have is something you look for rather than something you add.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Spliit",
                        style = SpliitType.Title,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            searching = !searching
                            if (!searching) query = ""
                        },
                    ) {
                        Icon(
                            imageVector = if (searching) {
                                Icons.Filled.Close
                            } else {
                                Icons.Filled.Search
                            },
                            contentDescription = if (searching) "Close search" else "Search groups",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Appearance lives beside search, both being things about the app rather
                    // than about a group.
                    IconButton(onClick = { appearanceOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Appearance",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = searching,
                    enter = expandVertically(SpliitMotion.spatialDefault()) +
                        fadeIn(SpliitMotion.effectsDefault()),
                    exit = shrinkVertically(SpliitMotion.spatialFast()) +
                        fadeOut(SpliitMotion.effectsFast()),
                ) {
                    SearchPill(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Search groups",
                        requestFocus = true,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                    )
                }
            }
        },
        floatingActionButton = {
            AddGroupButton(
                onCreate = { sheet = GroupsSheet.Create },
                onAddByLink = { sheet = GroupsSheet.AddByLink },
            )
        },
    ) { padding ->
        val matches = remember(model.groups, query) {
            val needle = query.trim()
            if (needle.isEmpty()) {
                model.groups
            } else {
                model.groups.filter { it.groupName.contains(needle, ignoreCase = true) }
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!model.hasLoadedGroups) {
                StartupMark(visible = slowStart)
            } else if (model.groups.isEmpty()) {
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
            } else if (matches.isEmpty()) {
                EmptyState(
                    art = EmptyStateArt.Icon(Icons.Filled.Search),
                    title = "No groups match “${query.trim()}”",
                ) {
                    Button(onClick = { query = "" }) { Text("Clear search") }
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = model.isRefreshing,
                    onRefresh = { model.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    GroupsList(model = model, groups = matches, onOpenGroup = onOpenGroup)
                }
            }
        }
    }

    if (appearanceOpen) {
        AppearanceSheet(
            choice = themeChoice,
            dynamicColor = dynamicColor,
            palette = palette,
            onChoose = { choice ->
                scope.launch { container.appearance.setTheme(choice) }
                appearanceOpen = false
            },
            onDynamicColorChange = { enabled ->
                scope.launch { container.appearance.setDynamicColor(enabled) }
            },
            onPaletteChange = { chosen ->
                scope.launch { container.appearance.setPalette(chosen) }
            },
            onDismiss = { appearanceOpen = false },
        )
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

/** The mark alone, on the page background, for a start that is taking a moment. */
@Composable
private fun StartupMark(visible: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(SpliitMotion.effectsDefault()),
            exit = fadeOut(SpliitMotion.effectsFast()),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_mark),
                contentDescription = null,
                modifier = Modifier.size(84.dp),
            )
        }
    }
}

/**
 * The app's appearance, as a pick rather than a switch: System follows the phone, and Light or
 * Dark pins this install to one side whatever the phone is set to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSheet(
    choice: ThemeChoice,
    dynamicColor: Boolean,
    palette: AppearancePalette,
    onChoose: (ThemeChoice) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onPaletteChange: (AppearancePalette) -> Unit,
    onDismiss: () -> Unit,
) {
    // Adaptive colour is the platform's, so the switch only exists where the platform has it.
    val offersDynamicColor = SpliitPalette.supportsDynamicColor
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            // Tall enough now for themes, the switch and five templates: it scrolls rather than
            // running off the bottom of a short screen.
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "Appearance",
                style = SpliitType.Title,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            )
            ThemeChoice.entries.forEachIndexed { index, option ->
                val first = index == 0
                val last = index == ThemeChoice.entries.lastIndex && !offersDynamicColor
                val shape = RoundedCornerShape(
                    topStart = if (first) GroupCardCorner else AdjacentCardCorner,
                    topEnd = if (first) GroupCardCorner else AdjacentCardCorner,
                    bottomStart = if (last) GroupCardCorner else AdjacentCardCorner,
                    bottomEnd = if (last) GroupCardCorner else AdjacentCardCorner,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = if (first) 0.dp else 2.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { onChoose(option) }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option.label(),
                        style = SpliitType.RowTitle,
                        modifier = Modifier.weight(1f),
                    )
                    if (option == choice) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (offersDynamicColor) {
                val lastShape = RoundedCornerShape(
                    topStart = AdjacentCardCorner,
                    topEnd = AdjacentCardCorner,
                    bottomStart = GroupCardCorner,
                    bottomEnd = GroupCardCorner,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 2.dp)
                        .clip(lastShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .toggleable(
                            value = dynamicColor,
                            role = Role.Switch,
                            onValueChange = onDynamicColorChange,
                        )
                        .padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dynamic color",
                            style = SpliitType.RowTitle,
                        )
                        Text(
                            text = "Match your wallpaper",
                            style = SpliitType.Secondary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = null,
                    )
                }
            }

            // A wallpaper palette cannot coexist with a template, so the templates are offered
            // exactly when the wallpaper is not in charge.
            if (!offersDynamicColor || !dynamicColor) {
                Text(
                    text = "Color",
                    style = SpliitType.SectionHeader,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
                )
                AppearancePalette.entries.forEachIndexed { index, template ->
                    PaletteRow(
                        palette = template,
                        selected = template == palette,
                        first = index == 0,
                        last = index == AppearancePalette.entries.lastIndex,
                        onClick = { onPaletteChange(template) },
                    )
                }
            }
        }
    }
}

/**
 * One template: its seed as a dot, its name, and a check. The dot is the colour the palette is
 * built from, so choosing is a matter of recognising a hue rather than reading its name.
 */
@Composable
private fun PaletteRow(
    palette: AppearancePalette,
    selected: Boolean,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = if (first) GroupCardCorner else AdjacentCardCorner,
        topEnd = if (first) GroupCardCorner else AdjacentCardCorner,
        bottomStart = if (last) GroupCardCorner else AdjacentCardCorner,
        bottomEnd = if (last) GroupCardCorner else AdjacentCardCorner,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = if (first) 0.dp else 2.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(50))
                .background(PaletteTemplates.seed(palette)),
        )
        Text(
            text = palette.label(),
            style = SpliitType.RowTitle,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun AppearancePalette.label(): String = when (this) {
    AppearancePalette.Spliit -> "Spliit"
    AppearancePalette.Iris -> "Iris"
    AppearancePalette.Lagoon -> "Lagoon"
    AppearancePalette.Ember -> "Ember"
    AppearancePalette.Meadow -> "Meadow"
}

private fun ThemeChoice.label(): String = when (this) {
    ThemeChoice.System -> "System"
    ThemeChoice.Light -> "Light"
    ThemeChoice.Dark -> "Dark"
}

/** A card's own corners, and the smaller ones it shares with the card above or below it. */
private val GroupCardCorner = 22.dp
private val AdjacentCardCorner = 10.dp

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
    // The plus folds into a close by turning, and the button comes with it: the squarish FAB
    // rounds out to a circle while it is open, so the X sits in a shape that says "close" rather
    // than a rotated plus in a square. 56dp tall, so half of it is a circle.
    val corner by animateDpAsState(
        targetValue = if (open) 28.dp else 18.dp,
        animationSpec = SpliitMotion.spatialFast(),
        label = "add button corner",
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
                // Only the fade and the appear/disappear are the transition's: the pill scales
                // and casts its shadow on one layer of its own, see [FabActionPill].
                enter = fadeIn(SpliitMotion.smooth()),
                exit = fadeOut(SpliitMotion.effectsFast()),
            ) {
                FabActionPill(
                    action = action,
                    onClick = {
                        open = false
                        action.onClick()
                    },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }

        FloatingActionButton(
            onClick = { open = !open },
            interactionSource = fabInteraction,
            // Off the Scaffold's own 16dp corner by the same 4dp/8dp the expense list's add
            // button sits in from its edges, so the two land in the same place on screen.
            modifier = Modifier.pressScale(fabInteraction),
            shape = RoundedCornerShape(corner),
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

/**
 * One of the action pills that unfolds from the add button.
 *
 * The arrival is a single `graphicsLayer`: the scale that grows the pill out of the button's
 * corner and the elevation that draws its shadow are on the same render node, so the shadow is
 * drawn in the same pass as the shape it belongs to. With `scaleIn` and `Modifier.shadow` those
 * sat on two layers and the render thread recomputed the shadow a frame behind the pill.
 */
@Composable
private fun FabActionPill(
    action: FabAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    val scale = remember { Animatable(0.86f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, SpliitMotion.snappy()) }

    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            transformOrigin = TransformOrigin(1f, 1f)
            shadowElevation = SpliitElevation.FloatingAction.toPx()
            this.shape = shape
            clip = false
        },
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

private data class FabAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun GroupsList(
    model: GroupsListModel,
    groups: List<RecentGroup>,
    onOpenGroup: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val starred = groups.filter { it.isStarred && !it.isArchived }
    val recent = groups.filter { !it.isStarred && !it.isArchived }
    val archived = groups.filter { it.isArchived }
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
            itemsIndexed(starred, key = { _, group -> group.groupId }) { index, group ->
                GroupRow(
                    group = group,
                    summary = model.summaries[group.groupId],
                    first = index == 0,
                    last = index == starred.lastIndex,
                    onClick = { onOpenGroup(group.groupId) },
                    onStar = { scope.launch { container.recentGroups.setStarred(!group.isStarred, group.groupId) } },
                    onArchive = { scope.launch { container.recentGroups.setArchived(!group.isArchived, group.groupId) } },
                    onRemove = { scope.launch { container.recentGroups.forget(group.groupId) } },
                )
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "recent") { SectionHeader("Recent groups") }
            itemsIndexed(recent, key = { _, group -> group.groupId }) { index, group ->
                GroupRow(
                    group = group,
                    summary = model.summaries[group.groupId],
                    first = index == 0,
                    last = index == recent.lastIndex,
                    onClick = { onOpenGroup(group.groupId) },
                    onStar = { scope.launch { container.recentGroups.setStarred(!group.isStarred, group.groupId) } },
                    onArchive = { scope.launch { container.recentGroups.setArchived(!group.isArchived, group.groupId) } },
                    onRemove = { scope.launch { container.recentGroups.forget(group.groupId) } },
                )
            }
        }

        if (archived.isNotEmpty()) {
            item(key = "archived") { SectionHeader("Archived") }
            itemsIndexed(archived, key = { _, group -> group.groupId }) { index, group ->
                GroupRow(
                    group = group,
                    summary = model.summaries[group.groupId],
                    first = index == 0,
                    last = index == archived.lastIndex,
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
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    onStar: () -> Unit,
    onArchive: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    // Cards that touch are one group: the sides they share take the small corner and the sides
    // facing air keep the large one, so a section reads as a stack rather than a pile of tiles.
    val shape = RoundedCornerShape(
        topStart = if (first) GroupCardCorner else AdjacentCardCorner,
        topEnd = if (first) GroupCardCorner else AdjacentCardCorner,
        bottomStart = if (last) GroupCardCorner else AdjacentCardCorner,
        bottomEnd = if (last) GroupCardCorner else AdjacentCardCorner,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            // Neighbours nearly touch; only the top of a section gets air above it.
            .padding(top = if (first) 6.dp else 0.dp, bottom = 2.dp),
    ) {
        ExpressiveSwipe(
            modifier = Modifier.fillMaxWidth(),
            startToEnd = SwipeAction(
                icon = Icons.Filled.Star,
                label = if (group.isStarred) "Unstar" else "Star",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onAction = onStar,
            ),
            endToStart = SwipeAction(
                icon = Icons.Filled.Archive,
                label = if (group.isArchived) "Unarchive" else "Archive",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onAction = onArchive,
            ),
        ) {
            // A card rather than a bare row: the list is a set of things, and each one is
            // something you open.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current,
                            onClick = onClick,
                            onLongClick = { menuOpen = true },
                        )
                        // The card dips under the finger and settles back on a spring, with no
                        // ripple spreading across it.
                        .pressScale(interaction, pressedScale = 0.98f)
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
                        GroupMeta(summary)
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        ExpressiveMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
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
            // Removing only forgets this copy: the group and its link stay on the server, which
            // is why this is a playlist minus rather than a bin, and not in the error colour.
            ExpressiveMenuItem(
                text = "Remove",
                leadingIcon = Icons.Filled.PlaylistRemove,
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}

/**
 * The date and the number of people, with the people drawn as the glyph the web app uses.
 *
 * The count comes from the snapshot when an instance cannot be reached, so a group that was read
 * once keeps saying how many people are in it instead of losing the line to an apology.
 */
@Composable
private fun GroupMeta(summary: GroupSummary?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summary?.let { dateLabel(it.createdAt) } ?: "…",
            style = SpliitType.Secondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = "·",
            style = SpliitType.Secondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            Icons.Filled.People,
            contentDescription = "Participants",
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = summary?.participantCount?.toString() ?: "…",
            style = SpliitType.Secondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
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

    /**
     * The remembered list has been read at least once.
     *
     * Until then an empty list means "not read yet", not "no groups", and drawing the welcome
     * screen for it is the flash this exists to stop.
     */
    var hasLoadedGroups by mutableStateOf(false)
        private set
    // A visit's own counts, so coming back to the list shows them while the servers answer
    // again rather than a row of "…".
    var summaries by mutableStateOf(
        container.lastGroupSummaries.ifEmpty { container.groupCache.loadSummaries() },
    )
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
                hasLoadedGroups = true
                // Nobody asked for this one: the list on screen is already the answer, so the
                // details behind it are fetched without a spinner over them.
                refresh(showIndicator = false)
            }
        }
    }

    /**
     * Asks every instance again. [showIndicator] is for the pull, which the person started and
     * therefore has to see an answer to; an automatic refresh says nothing.
     */
    fun refresh(showIndicator: Boolean = true) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            if (showIndicator) isRefreshing = true
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

        // What was read before, plus what was just read: an instance that could not be reached
        // keeps its last-known counts instead of losing them to this refresh.
        val merged = container.groupCache.loadSummaries() + found
        summaries = merged
        container.lastGroupSummaries = merged
        // On disk too: dates and participant counts are the two facts the list can still give
        // when no instance answers, and losing them would leave every row saying "…".
        container.groupCache.saveSummaries(merged)
        // A server that cannot be reached costs freshness, not the list: the names, dates and
        // participant counts are all stored here, so the rows stay exactly as readable as they
        // were. Nothing is said about it, because there is nothing the reader has to do.
        errorMessage = null
    }
}
