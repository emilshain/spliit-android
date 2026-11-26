package app.spliit.ui.activity

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.spliit.data.model.Activity
import app.spliit.data.model.ActivityType
import app.spliit.ui.group.ActivitySection
import app.spliit.ui.group.GroupScreenModel
import app.spliit.ui.theme.DateBucketHeader
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.SkeletonRow
import app.spliit.ui.theme.SkeletonSection
import app.spliit.ui.theme.SpliitDimens
import app.spliit.ui.theme.pressScale
import app.spliit.ui.theme.SpliitType
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * What has happened to a group and when: expenses created, edited and deleted, and the times the
 * group's own settings changed.
 *
 * The only screen that reads history rather than state, and so the only one that shows lines about
 * things which no longer exist. A deleted expense keeps its row, under the title it had at the
 * time, and simply has nowhere to lead — which is the whole reason to keep a log.
 *
 * Pushed from the information tab rather than being a tab of its own: of the group's screens this
 * is the one you consult rather than work in.
 */
@Composable
fun ActivityTab(
    model: GroupScreenModel,
    onEdit: (String) -> Unit,
) {
    LaunchedEffect(model) { model.loadActivitiesIfNeeded() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
            val sections = model.activitySections

            when {
                model.isLoadingActivities -> ActivitySkeleton()

                model.didFailToLoad || model.didFailToLoadActivities -> EmptyState(
                    art = EmptyStateArt.Icon(Icons.Filled.WifiOff),
                    title = if (model.didFailToLoad) {
                        "Couldn’t load this group"
                    } else {
                        "Couldn’t load the activity"
                    },
                    description = model.activitiesFailure
                        ?: model.loadFailure
                        ?: "The server didn’t respond.",
                ) {
                    Button(onClick = { scope.launch { model.retryActivities() } }) {
                        Text("Try again")
                    }
                }

                // The sections rather than the raw list: a log made entirely of entries this
                // version cannot describe has nothing to draw.
                sections.isEmpty() -> EmptyState(
                    art = EmptyStateArt.Icon(Icons.Filled.History),
                    title = "No activity yet",
                )

                else -> ActivityList(
                    model = model,
                    sections = sections,
                    onEdit = onEdit,
                )
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityList(
    model: GroupScreenModel,
    sections: List<ActivitySection>,
    onEdit: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                try {
                    model.refreshActivities()
                } finally {
                    refreshing = false
                }
            }
        },
        state = refreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            sections.forEach { section ->
                item(key = "header-${section.date}") {
                    DateBucketHeader(section.title)
                }
                // A day's entries are one stack: shared sides take the small corner, so the
                // log reads as cards under a date rather than a wall of text.
                itemsIndexed(
                    items = section.activities,
                    key = { _, activity -> activity.id },
                ) { index, activity ->
                    ActivityRow(
                        activity = activity,
                        participantName = activity.participantId?.let { model.participant(it)?.name },
                        showsDate = false,
                        opensExpense = activity.expenseStillExists,
                        shape = activityShape(
                            first = index == 0,
                            last = index == section.activities.lastIndex,
                        ),
                        onOpen = { activity.expenseId?.let(onEdit) },
                    )
                }
            }

            if (model.hasMoreActivities) {
                item(key = "more") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                    LaunchedEffect(model.activities.size) { model.loadNextActivityPage() }
                }
            }
        }
    }
}

/**
 * A row leads to its expense when there is still one to lead to. Rows about a deleted expense,
 * and about the group's own settings, are text and stay text — an inert row is better than one
 * that opens an editor for something that is gone.
 */
@Composable
private fun ActivityRow(
    activity: Activity,
    participantName: String?,
    showsDate: Boolean,
    opensExpense: Boolean,
    shape: Shape,
    onOpen: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .then(
                if (opensExpense) {
                    Modifier
                        // The row dips under the finger; no ripple over a card.
                        .clickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current,
                            onClick = onOpen,
                        )
                        .pressScale(interaction, pressedScale = 0.98f)
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // A fixed column, so the sentences line up down the list rather than shifting with
            // the width of each glyph.
            Icon(
                imageVector = symbol(activity.activityType),
                contentDescription = null,
                modifier = Modifier.width(16.dp).padding(top = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = summary(activity, participantName),
                    style = SpliitType.RowTitle,
                )
                Text(
                    text = timestamp(activity.time, showsDate),
                    style = SpliitType.Secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A row's own corners, and the smaller ones it shares with the row above or below it. */
private fun activityShape(first: Boolean, last: Boolean): RoundedCornerShape = RoundedCornerShape(
    topStart = if (first) 16.dp else 6.dp,
    topEnd = if (first) 16.dp else 6.dp,
    bottomStart = if (last) 16.dp else 6.dp,
    bottomEnd = if (last) 16.dp else 6.dp,
)

/** The same glyphs the app uses for these actions elsewhere. */
private fun symbol(type: ActivityType): ImageVector = when (type) {
    ActivityType.CreateExpense -> Icons.Filled.Add
    ActivityType.UpdateExpense -> Icons.Filled.Edit
    ActivityType.DeleteExpense -> Icons.Filled.Delete
    ActivityType.UpdateGroup -> Icons.Filled.Settings
    is ActivityType.Unknown -> Icons.AutoMirrored.Filled.HelpOutline
}

/**
 * The whole sentence.
 *
 * The title is the one recorded with the activity, which is the point — renaming an expense
 * leaves the old name on the line describing its creation.
 */
private fun summary(activity: Activity, participantName: String?): String {
    val who = participantName ?: "Someone"
    val what = activity.title.orEmpty()
    return when (activity.activityType) {
        ActivityType.CreateExpense -> "$who added “$what”."
        ActivityType.UpdateExpense -> "$who updated “$what”."
        ActivityType.DeleteExpense -> "$who deleted “$what”."
        ActivityType.UpdateGroup -> "$who changed the group settings."
        is ActivityType.Unknown -> ""
    }
}

/**
 * The time, plus the date when the heading above has not already given it. Both are left to the
 * platform, which knows how the reader's locale writes them.
 */
private fun timestamp(time: Instant, showsDate: Boolean): String {
    val formatter = if (showsDate) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    } else {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    }
    return formatter
        .withLocale(Locale.getDefault())
        .format(time.atZone(ZoneId.systemDefault()))
}


/** The log before it arrives: a couple of days, each with its entries. */
@Composable
private fun ActivitySkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonSection()
        repeat(3) { SkeletonRow() }
        SkeletonSection(modifier = Modifier.padding(top = 12.dp))
        repeat(3) { SkeletonRow() }
    }
}
