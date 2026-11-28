package app.spliit.ui.expenses

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.spliit.data.model.Participant
import app.spliit.ui.group.ExpenseSection
import app.spliit.ui.group.GroupScreenModel
import app.spliit.ui.theme.DateBucketHeader
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.ExpressiveMenu
import app.spliit.ui.theme.ExpressiveMenuItem
import app.spliit.ui.theme.LocalSpliitColors
import app.spliit.ui.theme.Monogram
import app.spliit.ui.theme.MonogramPalette
import app.spliit.ui.theme.pressScale
import app.spliit.ui.theme.SkeletonCard
import app.spliit.ui.theme.SkeletonRow
import app.spliit.ui.theme.stackedCardShape
import app.spliit.ui.theme.SpliitDimens
import app.spliit.ui.theme.SpliitMotion
import app.spliit.ui.theme.SpliitType
import java.util.Locale
import kotlin.math.abs

/**
 * A group's expenses, newest first, under the day each one happened.
 *
 * The screen opens on the answer most people came for — what this group leaves them owed or
 * owing — and the list follows it, behind one long search field and a filter for whose expenses
 * to show. Adding is a button over the list rather than a toolbar action, because it belongs to
 * the expenses rather than to the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListTab(
    model: GroupScreenModel,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onSearch: () -> Unit,
    searchBarMorph: Modifier = Modifier,
    /** True while the search bar has scrolled up under the header. */
    onSearchCollapsed: (Boolean) -> Unit = {},
) {
    var payerFilter by remember { mutableStateOf<String?>(null) }
    val participants = model.group?.participants.orEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            model.isLoadingExpenses -> ExpenseListSkeleton()

            model.didFailToLoad || model.didFailToLoadExpenses -> EmptyState(
                art = EmptyStateArt.Icon(Icons.Filled.WifiOff),
                title = if (model.didFailToLoad) {
                    "Couldn’t load this group"
                } else {
                    "Couldn’t load the expenses"
                },
                description = model.loadFailure ?: "The server didn’t respond.",
            ) {
                Button(onClick = { model.retry() }) { Text("Try again") }
            }

            model.expenses.isEmpty() -> EmptyState(
                art = EmptyStateArt.Icon(Icons.AutoMirrored.Filled.List),
                title = "No expenses yet",
            ) {
                Button(onClick = onAdd) { Text("Add expense") }
            }

            else -> {
                val sections = filtered(model.sections, payerFilter)
                val filterName = participants.firstOrNull { it.id == payerFilter }?.name

                // A filter is a question about the whole group, so while one is on the pages
                // behind the list are fetched without waiting to be scrolled to.
                LaunchedEffect(payerFilter, model.expenses.size, model.hasMoreExpenses) {
                    if (payerFilter != null && model.hasMoreExpenses) model.loadNextPage()
                }

                // The search bar is the second row; once it is gone under the header the header
                // takes over as a small search button, so searching never scrolls out of reach.
                val listState = rememberLazyListState()
                val searchCollapsed by remember {
                    derivedStateOf {
                        val first = listState.firstVisibleItemIndex
                        first > 1 || (first == 1 && listState.firstVisibleItemScrollOffset > 16)
                    }
                }
                LaunchedEffect(searchCollapsed) { onSearchCollapsed(searchCollapsed) }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                        item(key = "summary") { BalanceSummaryCard(model = model) }

                        item(key = "search") {
                            ExpensesToolbar(
                                participants = participants,
                                payerFilter = payerFilter,
                                onFilterChange = { payerFilter = it },
                                onSearch = onSearch,
                                searchBarMorph = searchBarMorph,
                            )
                        }

                        if (filterName != null) {
                            item(key = "filter-caption") {
                                Text(
                                    text = "Showing only what $filterName paid for",
                                    style = SpliitType.Secondary,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        start = SpliitDimens.SectionLabelStart,
                                        end = SpliitDimens.SectionLabelStart,
                                        top = SpliitDimens.SectionLabelBottom,
                                        bottom = SpliitDimens.CardGap,
                                    ),
                                )
                            }
                        }

                        model.expensesFailure?.let { failure ->
                            item(key = "failure") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.padding(2.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = failure,
                                        style = SpliitType.Secondary,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        if (sections.isEmpty() && filterName != null && !model.hasMoreExpenses) {
                            item(key = "no-filter-results") {
                                Text(
                                    text = "Nothing here was paid by $filterName.",
                                    style = SpliitType.Secondary,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                )
                            }
                        }

                        sections.forEach { section ->
                            item(key = "header-${section.date}") {
                                DateBucketHeader(section.title)
                            }
                            itemsIndexed(
                                items = section.expenses,
                                key = { _, expense -> expense.id },
                            ) { index, expense ->
                                // Opening is the whole gesture here: deleting lives on the
                                // expense itself, so the row is free to belong to the pager.
                                val shape = stackedCardShape(
                                    first = index == 0,
                                    last = index == section.expenses.lastIndex,
                                )
                                // The ripple belongs to the card, not to the row's full width:
                                // the tap is passed into the card, so it is clipped to its own
                                // shape and does not run from edge to edge.
                                // A card that arrives or leaves settles on the fast spatial
                                // spring. The list's default one is the search page's: slower
                                // on purpose, because results stream in there and a card that
                                // lands instantly over a half-typed word reads as a jump. Here
                                // the rows are already known, so the wait is just lag.
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = if (index == 0) SpliitDimens.Space0 else SpliitDimens.CardGap)
                                        .animateItem(
                                            placementSpec = SpliitMotion.spatialFast<IntOffset>(),
                                            fadeInSpec = SpliitMotion.effectsDefault(),
                                            fadeOutSpec = SpliitMotion.effectsFast(),
                                        ),
                                ) {
                                    ExpenseRow(
                                        onClick = { onEdit(expense.id) },
                                        expense = expense,
                                        payerPosition = model.participantPosition(expense.paidBy.id),
                                        formatter = model.moneyFormatter,
                                        activeParticipantId = model.actorId,
                                        shape = shape,
                                        pending = model.isPending(expense.id),
                                    )
                                }
                            }
                        }

                        if (model.hasMoreExpenses) {
                            item(key = "more") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                }
                                // The footer being composed is the request for the next page.
                                LaunchedEffect(model.expenses.size) { model.loadNextPage() }
                            }
                        }

                        item(key = "bottom") { Spacer(Modifier.padding(bottom = 88.dp)) }
                    }
            }
        }

        val addInteraction = remember { MutableInteractionSource() }

        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .pressScale(addInteraction),
            interactionSource = addInteraction,
            shape = RoundedCornerShape(SpliitDimens.FabCorner),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add expense")
        }

        ExpenseUndoBar(
            model = model,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** The list cut down to one person's expenses, in the order the list already had. */
private fun filtered(sections: List<ExpenseSection>, payerId: String?): List<ExpenseSection> {
    if (payerId == null) return sections
    return sections
        .map { section ->
            section.copy(expenses = section.expenses.filter { it.paidBy.id == payerId })
        }
        .filter { it.expenses.isNotEmpty() }
}

/**
 * A search field that is a doorway, and the filter for whose expenses are shown.
 *
 * The field itself is not editable here: tapping it opens the search screen, where the keyboard,
 * the results and the list that is being searched all have room. The button beside it narrows
 * the list on the spot, by whoever paid.
 */
@Composable
private fun ExpensesToolbar(
    participants: List<Participant>,
    payerFilter: String?,
    onFilterChange: (String?) -> Unit,
    onSearch: () -> Unit,
    searchBarMorph: Modifier = Modifier,
) {
    val active = payerFilter != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onSearch,
            // The search page's field grows out of this pill, so the transition has one bar to
            // morph rather than two to cross-fade.
            modifier = Modifier.weight(1f).height(46.dp).then(searchBarMorph),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Search expenses",
                    style = SpliitType.Secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box {
            var open by remember { mutableStateOf(false) }

            Surface(
                onClick = { open = true },
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(50),
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.FilterList,
                        contentDescription = "Filter expenses",
                        modifier = Modifier.size(20.dp),
                        tint = if (active) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            ExpressiveMenu(
                expanded = open,
                onDismissRequest = { open = false },
            ) {
                ExpressiveMenuItem(
                    text = "Anyone",
                    selected = payerFilter == null,
                    onClick = {
                        open = false
                        onFilterChange(null)
                    },
                )
                participants.forEach { participant ->
                    ExpressiveMenuItem(
                        text = "Paid by ${participant.name}",
                        selected = payerFilter == participant.id,
                        onClick = {
                            open = false
                            onFilterChange(participant.id)
                        },
                    )
                }
            }
        }
    }
}

/**
 * What this group leaves the person holding the phone with, and who is in it.
 *
 * Drawn only once the app knows who they are: a balance under nobody's name would be a number
 * about a stranger, and the balances tab is where the whole picture lives.
 */
@Composable
private fun BalanceSummaryCard(model: GroupScreenModel) {
    val actor = model.actorId ?: return
    val balance = model.balances[actor]?.total ?: return
    val participants = model.group?.participants.orEmpty()

    val label = when {
        balance > 0 -> "You are owed"
        balance < 0 -> "You owe"
        else -> "Settled up"
    }
    val amountColor = when {
        balance > 0 -> LocalSpliitColors.current.moneyPositive
        balance < 0 -> LocalSpliitColors.current.moneyNegative
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpliitDimens.ScreenGutter, vertical = 8.dp),
        shape = RoundedCornerShape(SpliitDimens.CardCorner),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(SpliitDimens.CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label.uppercase(Locale.getDefault()),
                style = SpliitType.Overline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = model.moneyFormatter.string(abs(balance)),
                style = SpliitType.Display,
                color = amountColor,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            if (participants.isEmpty()) {
                Text(
                    text = "No participants yet",
                    style = SpliitType.Secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // A handful is enough to say who is here; the information tab lists them all.
                    participants.take(SHOWN_PARTICIPANTS).forEachIndexed { position, participant ->
                        Monogram(
                            name = participant.name,
                            colorIndex = MonogramPalette.indexAtPosition(position),
                            size = 30.dp,
                        )
                    }
                    if (participants.size > SHOWN_PARTICIPANTS) {
                        Text(
                            text = "+${participants.size - SHOWN_PARTICIPANTS}",
                            style = SpliitType.Secondary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private const val SHOWN_PARTICIPANTS = 6

/**
 * The list before the expenses arrive: the summary card, then the rows in the shape they will
 * take. Drawn instead of a spinner so the page does not jump when the data lands.
 */
@Composable
private fun ExpenseListSkeleton() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 60.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            SkeletonCard(
                height = 92.dp,
                shape = RoundedCornerShape(SpliitDimens.CardCorner),
                modifier = Modifier.padding(horizontal = SpliitDimens.ScreenGutter, vertical = 6.dp),
            )
        }
        items(6) {
            SkeletonRow(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}
