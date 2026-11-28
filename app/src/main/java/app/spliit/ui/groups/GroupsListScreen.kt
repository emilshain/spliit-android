package app.spliit.ui.groups

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PlaylistRemove
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Warning
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.spliit.AppContainer
import app.spliit.R
import app.spliit.data.local.AppearancePalette
import app.spliit.data.local.RecentGroup
import app.spliit.data.local.ThemeChoice
import app.spliit.data.model.GroupSummary
import app.spliit.data.remote.SpliitApi
import app.spliit.domain.Failures
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
import app.spliit.ui.theme.SelectionCircle
import app.spliit.ui.theme.SelectionState
import app.spliit.ui.theme.stackedCardPadding
import app.spliit.ui.theme.stackedCardShape
import app.spliit.ui.theme.SpliitDimens
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
    /** Opens a group on its settings page, for the edit action of a one-row selection. */
    onEditGroup: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val model = remember(container) { GroupsListModel(container, scope) }
    var sheet by remember { mutableStateOf<GroupsSheet>(GroupsSheet.None) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var appearanceOpen by remember { mutableStateOf(false) }
    var selectionMenuOpen by remember { mutableStateOf(false) }
    // Long-press selection: while it is open the header holds the actions and a tap picks
    // rather than opens, the way a file list on Drive behaves.
    val selection = remember { SelectionState() }
    val themeChoice by container.appearance.theme.collectAsState(initial = ThemeChoice.System)
    val dynamicColor by container.appearance.dynamicColor.collectAsState(initial = true)
    val palette by container.appearance.palette.collectAsState(initial = AppearancePalette.Spliit)

    LaunchedEffect(model) { model.start() }

    // A search and a selection are two different jobs for the same list; starting one ends the
    // other, so the header never has to hold both.
    LaunchedEffect(searching) { if (searching) selection.clear() }

    // A group that has left the list cannot stay picked.
    LaunchedEffect(model.groups, selection.ids) {
        selection.retain(model.groups.mapTo(HashSet()) { it.groupId })
    }

    // Back leaves the selection before it leaves the screen.
    BackHandler(enabled = selection.active) { selection.clear() }

    fun starSelection() {
        val chosen = model.groups.filter { it.groupId in selection.ids }
        if (chosen.isEmpty()) return
        // Every one becomes starred unless every one already is, so the action always does
        // something rather than silently doing nothing to half the set.
        val star = chosen.any { !it.isStarred }
        selection.clear()
        scope.launch { chosen.forEach { container.recentGroups.setStarred(star, it.groupId) } }
    }

    fun archiveSelection() {
        val chosen = model.groups.filter { it.groupId in selection.ids }
        if (chosen.isEmpty()) return
        val archive = chosen.any { !it.isArchived }
        selection.clear()
        scope.launch { chosen.forEach { container.recentGroups.setArchived(archive, it.groupId) } }
    }

    fun removeSelection() {
        val chosen = model.groups.filter { it.groupId in selection.ids }
        selection.clear()
        scope.launch { chosen.forEach { container.recentGroups.forget(it.groupId) } }
    }

    // A start with nothing on disk reads in a frame or two, and drawing a splash for it would be
    // slower than the wait it hides. The mark appears only if the read is genuinely slow.
    var slowStart by remember { mutableStateOf(false) }
    LaunchedEffect(model.hasLoadedGroups) {
        if (model.hasLoadedGroups) return@LaunchedEffect
        delay(240)
        slowStart = true
    }

    // What the list is showing: the remembered groups, narrowed by the search field when one is
    // open. The header needs it for "select all", so it is worked out above the bars.
    val matches = remember(model.groups, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            model.groups
        } else {
            model.groups.filter { it.groupName.contains(needle, ignoreCase = true) }
        }
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
                        // Selecting starts at the icon rather than at the page gutter, so the
                        // close button sits where the thumb reaches and nothing is left blank
                        // to its left.
                        .padding(
                            start = if (selection.active) 4.dp else 20.dp,
                            end = if (selection.active) 0.dp else 12.dp,
                            top = 8.dp,
                            bottom = 4.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selection.active) {
                        // The app's name and its settings are not things to do to a group, so
                        // the selection replaces them with the things that are.
                        IconButton(onClick = { selection.clear() }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Stop selecting",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        // Row-sized rather than the page title's size: the bar has to hold the
                        // count and up to four actions on a phone.
                        Text(
                            text = if (selection.count == 1) {
                                "1 selected"
                            } else {
                                "${selection.count} selected"
                            },
                            style = SpliitType.RowTitle,
                            // The count is a label, not a heading: unbolded, in the highlight's
                            // own tone, so the bar states the selection instead of shouting it.
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        // Editing changes one group, so it is only offered for one.
                        if (selection.count == 1) {
                            IconButton(
                                onClick = {
                                    val id = selection.ids.firstOrNull() ?: return@IconButton
                                    selection.clear()
                                    onEditGroup(id)
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = "Edit group",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                val present = matches.map { it.groupId }
                                if (selection.containsAll(present)) {
                                    selection.clear()
                                } else {
                                    selection.selectAll(present)
                                }
                            },
                        ) {
                            Icon(
                                Icons.Outlined.SelectAll,
                                contentDescription = "Select all",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        IconButton(onClick = { starSelection() }) {
                            Icon(
                                Icons.Outlined.Star,
                                contentDescription = "Star or unstar",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        IconButton(onClick = { archiveSelection() }) {
                            Icon(
                                Icons.Outlined.Archive,
                                contentDescription = "Archive or unarchive",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        // Removing only forgets this device's copy, which is why it is not in
                        // the error colour: the group and its link stay on the server. It is
                        // also the one action with no way back here, so it sits in the menu
                        // rather than beside the two that have one.
                        Box {
                            IconButton(onClick = { selectionMenuOpen = true }) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = "More actions",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            ExpressiveMenu(
                                expanded = selectionMenuOpen,
                                onDismissRequest = { selectionMenuOpen = false },
                            ) {
                                ExpressiveMenuItem(
                                    text = "Remove from this phone",
                                    leadingIcon = Icons.Outlined.PlaylistRemove,
                                    onClick = {
                                        selectionMenuOpen = false
                                        removeSelection()
                                    },
                                )
                            }
                        }
                    } else {
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
                                    Icons.Outlined.Close
                                } else {
                                    Icons.Outlined.Search
                                },
                                contentDescription = if (searching) {
                                    "Close search"
                                } else {
                                    "Search groups"
                                },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Appearance lives beside search, both being things about the app
                        // rather than about a group.
                        IconButton(onClick = { appearanceOpen = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Appearance",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
            // Adding is not one of the things a selection is for, so the button leaves the way
            // it arrived — downwards, on the spring, rather than vanishing.
            AnimatedVisibility(
                visible = !selection.active,
                // SwiftUI's `.move(edge: .bottom).combined(with: .opacity)` on `.snappy`: the
                // button is pushed off the bottom by the selection and springs back when it
                // ends, with a little scale so it reads as leaving rather than sliding under
                // something.
                enter = slideInVertically(SpliitMotion.snappy()) { height -> height } +
                    fadeIn(SpliitMotion.snappy()) +
                    scaleIn(SpliitMotion.snappy(), initialScale = 0.9f),
                exit = slideOutVertically(SpliitMotion.snappy()) { height -> height } +
                    fadeOut(SpliitMotion.snappy()) +
                    scaleOut(SpliitMotion.snappy(), targetScale = 0.9f),
            ) {
                AddGroupButton(
                    onCreate = { sheet = GroupsSheet.Create },
                    onAddByLink = { sheet = GroupsSheet.AddByLink },
                )
            }
        },
    ) { padding ->
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
                    art = EmptyStateArt.Icon(Icons.Outlined.Search),
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
                    GroupsList(
                        model = model,
                        groups = matches,
                        selection = selection,
                        onOpenGroup = onOpenGroup,
                    )
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
                modifier = Modifier.padding(
                    start = SpliitDimens.ScreenGutter,
                    end = SpliitDimens.ScreenGutter,
                    bottom = SpliitDimens.CardSpacing,
                ),
            )
            ThemeChoice.entries.forEachIndexed { index, option ->
                val first = index == 0
                val last = index == ThemeChoice.entries.lastIndex && !offersDynamicColor
                val shape = stackedCardShape(first = first, last = last)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpliitDimens.ScreenGutter)
                        .padding(top = if (first) 0.dp else SpliitDimens.CardGap)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { onChoose(option) }
                        .padding(
                            horizontal = SpliitDimens.RowPaddingHorizontal,
                            vertical = SpliitDimens.RowPaddingVertical,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option.label(),
                        style = SpliitType.RowTitle,
                        modifier = Modifier.weight(1f),
                    )
                    if (option == choice) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (offersDynamicColor) {
                val lastShape = RoundedCornerShape(
                    topStart = SpliitDimens.AdjacentCorner,
                    topEnd = SpliitDimens.AdjacentCorner,
                    bottomStart = SpliitDimens.CardCorner,
                    bottomEnd = SpliitDimens.CardCorner,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpliitDimens.ScreenGutter)
                        .padding(top = SpliitDimens.CardGap)
                        .clip(lastShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .toggleable(
                            value = dynamicColor,
                            role = Role.Switch,
                            onValueChange = onDynamicColorChange,
                        )
                        .padding(
                            start = SpliitDimens.RowPaddingHorizontal,
                            end = SpliitDimens.CardSpacing,
                            top = SpliitDimens.RowPaddingVertical,
                            bottom = SpliitDimens.RowPaddingVertical,
                        ),
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
                    modifier = Modifier.padding(
                        start = SpliitDimens.ScreenGutter,
                        end = SpliitDimens.ScreenGutter,
                        top = SpliitDimens.SectionLabelTop,
                        bottom = SpliitDimens.RowPaddingVertical,
                    ),
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
    val shape = stackedCardShape(first = first, last = last)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = if (first) SpliitDimens.Space0 else SpliitDimens.CardGap)
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
                imageVector = Icons.Outlined.Check,
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
            FabAction("Create group", Icons.Outlined.Add, onCreate),
            FabAction("Add by link", Icons.Outlined.Link, onAddByLink),
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
        targetValue = if (open) 28.dp else SpliitDimens.FabCorner,
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
                imageVector = Icons.Outlined.Add,
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
    selection: SelectionState,
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
                        Icons.Outlined.Warning,
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
                    modifier = Modifier.animateItem(
                        placementSpec = SpliitMotion.spatialDefault<IntOffset>(),
                        fadeInSpec = SpliitMotion.effectsDefault(),
                        fadeOutSpec = SpliitMotion.effectsFast(),
                    ),
                    group = group,
                    summary = model.summaries[group.groupId],
                    first = index == 0,
                    last = index == starred.lastIndex,
                    selectionActive = selection.active,
                    selected = group.groupId in selection,
                    onClick = {
                        if (selection.active) selection.toggle(group.groupId)
                        else onOpenGroup(group.groupId)
                    },
                    onToggle = { selection.toggle(group.groupId) },
                    onLongPress = { selection.start(group.groupId) },
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
                    modifier = Modifier.animateItem(
                        placementSpec = SpliitMotion.spatialDefault<IntOffset>(),
                        fadeInSpec = SpliitMotion.effectsDefault(),
                        fadeOutSpec = SpliitMotion.effectsFast(),
                    ),
                    group = group,
                    summary = model.summaries[group.groupId],
                    first = index == 0,
                    last = index == recent.lastIndex,
                    selectionActive = selection.active,
                    selected = group.groupId in selection,
                    onClick = {
                        if (selection.active) selection.toggle(group.groupId)
                        else onOpenGroup(group.groupId)
                    },
                    onToggle = { selection.toggle(group.groupId) },
                    onLongPress = { selection.start(group.groupId) },
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
                    modifier = Modifier.animateItem(
                        placementSpec = SpliitMotion.spatialDefault<IntOffset>(),
                        fadeInSpec = SpliitMotion.effectsDefault(),
                        fadeOutSpec = SpliitMotion.effectsFast(),
                    ),
                    group = group,
                    summary = model.summaries[group.groupId],
                    first = index == 0,
                    last = index == archived.lastIndex,
                    selectionActive = selection.active,
                    selected = group.groupId in selection,
                    onClick = {
                        if (selection.active) selection.toggle(group.groupId)
                        else onOpenGroup(group.groupId)
                    },
                    onToggle = { selection.toggle(group.groupId) },
                    onLongPress = { selection.start(group.groupId) },
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
        modifier = Modifier.padding(
            start = SpliitDimens.SectionLabelStart,
            end = SpliitDimens.SectionLabelStart,
            top = SpliitDimens.SectionLabelTop,
            bottom = SpliitDimens.RowPaddingVertical,
        ),
    )
}

/**
 * A group, and the three things that can be done to it.
 *
 * Star and archive sit on opposite edges because they are opposite acts, and a full swipe from
 * the trailing edge archives rather than removes: removing is the one thing here with no way back
 * but the original link. A long press picks the group instead of opening a menu on it — the same
 * three actions move up into the header, where they apply to everything selected at once.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun GroupRow(
    group: RecentGroup,
    summary: GroupSummary?,
    first: Boolean,
    last: Boolean,
    selectionActive: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onStar: () -> Unit,
    onArchive: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }

    // Cards that touch are one group: the sides they share take the small corner and the sides
    // facing air keep the large one, so a section reads as a stack rather than a pile of tiles.
    val shape = stackedCardShape(first = first, last = last)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .stackedCardPadding(first),
    ) {
        ExpressiveSwipe(
            modifier = Modifier.fillMaxWidth(),
            startToEnd = SwipeAction(
                icon = Icons.Outlined.Star,
                label = if (group.isStarred) "Unstar" else "Star",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onAction = onStar,
            ),
            endToStart = SwipeAction(
                icon = Icons.Outlined.Archive,
                label = if (group.isArchived) "Unarchive" else "Archive",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onAction = onArchive,
            ),
        ) {
            // A card rather than a bare row: the list is a set of things, and each one is
            // something you open.
            Surface(
                // The card dips as a whole — surface, corners and content together — and
                // settles back on the spring. Scaling only what is drawn inside left the card
                // itself sitting still while its text shrank.
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScale(interaction, pressedScale = 0.96f),
                shape = shape,
                // A picked card takes the selection role, not a neighbouring tone of the card's
                // own colour: a stepped surface reads as a slightly different card, a container
                // reads as picked.
                color = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Clipped to the card before it is made clickable, so the ripple is
                        // the card's rather than a rectangle across the row.
                        .clip(shape)
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current,
                            onClick = { if (selectionActive) onToggle() else onClick() },
                            onLongClick = onLongPress,
                        )
                        .padding(
                            horizontal = SpliitDimens.RowPaddingHorizontal,
                            vertical = SpliitDimens.RowPaddingVertical,
                        ),
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

                    // The trailing slot says what a tap would do: open the group, or pick it.
                    // The circle takes the chevron's place rather than sitting beside it, so the
                    // row's own width does not change when a selection opens.
                    AnimatedContent(
                        targetState = selectionActive,
                        transitionSpec = {
                            fadeIn(SpliitMotion.effectsFast()) togetherWith
                                fadeOut(SpliitMotion.effectsFast())
                        },
                        label = "group trailing",
                    ) { selecting ->
                        if (selecting) {
                            SelectionCircle(selected)
                        } else {
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
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
            Icons.Outlined.People,
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
    // again rather than a row of "…". The copy on disk is read in [start], off the main thread;
    // reading it here would make constructing the screen a file read on the first frame.
    var summaries by mutableStateOf(container.lastGroupSummaries)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    private var refreshJob: Job? = null

    /** Reloads whenever the remembered groups change, as the iOS list does. */
    fun start() {
        scope.launch {
            // The last visit's counts, if this launch has not read them yet.
            if (summaries.isEmpty()) {
                val remembered = container.groupCache.loadSummaries()
                if (summaries.isEmpty() && remembered.isNotEmpty()) summaries = remembered
            }
        }
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
                errorMessage = Failures.message(error)
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
