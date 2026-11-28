package app.spliit.ui.search

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.spliit.data.model.ExpenseListItem
import app.spliit.data.model.Participant
import app.spliit.domain.ExpenseSearchMatch
import app.spliit.ui.expenses.ExpenseRow
import app.spliit.ui.expenses.ExpenseUndoBar
import app.spliit.ui.group.ExpenseSection
import app.spliit.ui.group.GroupScreenModel
import app.spliit.ui.theme.DateBucketHeader
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.ExpressiveMenu
import app.spliit.ui.theme.ExpressiveMenuItem
import app.spliit.ui.theme.pressScale
import app.spliit.ui.theme.SearchPill
import app.spliit.ui.theme.SkeletonRow
import app.spliit.ui.theme.stackedCardShape
import app.spliit.ui.theme.SpliitDimens
import app.spliit.ui.theme.SpliitMotion
import app.spliit.ui.theme.SpliitType

/**
 * What the search screen shows: the field, the payer filter, and whatever the server matched.
 *
 * The results are their own list rather than a filtered view of the expenses tab. Both are alive
 * at once, and narrowing the shared list would empty the list sitting behind the search field.
 *
 * The server does the matching — `groups.expenses.list` takes a `filter`, matching titles and,
 * since spliit#660, notes — so a search covers the whole group, not just the pages paged in.
 *
 * The page arrives as one thing. It used to be transparent, so the list behind it showed through
 * the field and the empty state for the length of the fade and then vanished in a single frame:
 * the prompt read as arriving before the page it belongs to, and the backdrop as snapping in.
 * Now the backdrop is part of the page and fades with it, and the contents wait until it is
 * mostly there.
 */
@Composable
fun ExpenseSearchTab(
    model: GroupScreenModel,
    query: String,
    onQueryChange: (String) -> Unit,
    onEdit: (String) -> Unit,
    fieldMorph: Modifier = Modifier,
) {
    val trimmed = query.trim()
    var payerFilter by remember { mutableStateOf<String?>(null) }
    val participants = model.group?.participants.orEmpty()

    // The backdrop leads, the contents follow: 0 to 1 for the page, and nothing below the field
    // until the page is more than half there.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, SpliitMotion.effectsDefault()) }
    val bodyAlpha = ((reveal.value - 0.4f) / 0.6f).coerceIn(0f, 1f)

    val visibleSections = filteredSearchSections(model.searchSections, payerFilter)
    val filterName = participants.firstOrNull { it.id == payerFilter }?.name
    val filterHidesEverything =
        payerFilter != null && visibleSections.isEmpty() && model.searchResults.isNotEmpty()

    // A filter is a question about the whole group, so while one is on the pages behind the
    // results are fetched without waiting to be scrolled to.
    LaunchedEffect(payerFilter, model.searchResults.size, model.hasMoreSearchResults) {
        if (payerFilter != null && model.hasMoreSearchResults) model.loadNextSearchPage()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = reveal.value))
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The same pill as the one on the expenses tab, so the page's bar is that bar
                // grown rather than a second one arriving.
                SearchPill(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = "Search expenses",
                    requestFocus = true,
                    modifier = Modifier
                        .weight(1f)
                        .then(fieldMorph),
                )
                SearchFilterButton(
                    participants = participants,
                    payerFilter = payerFilter,
                    onFilterChange = { payerFilter = it },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer { alpha = bodyAlpha },
            ) {
                // Driven by what is in the field, not by what the model has searched for: the two
                // disagree for the length of the debounce, and deciding on the model alone put the
                // prompt back on screen between characters.
                when {
                    trimmed.isEmpty() -> EmptyState(
                        art = EmptyStateArt.Icon(Icons.Outlined.Search),
                        title = "Search this group",
                        description = "Type a word from the expense you’re looking for.",
                    )

                    // Only once the answer on screen belongs to the words in the field: during
                    // the debounce the previous query's verdict must not stand in for this one's.
                    model.didFailToSearch && model.filter == trimmed -> EmptyState(
                        art = EmptyStateArt.Icon(Icons.Outlined.WifiOff),
                        title = "Couldn’t search",
                        description = model.searchFailure ?: "The server didn’t respond.",
                    )

                    model.hasNoMatches && model.filter == trimmed -> EmptyState(
                        art = EmptyStateArt.Icon(Icons.Outlined.Search),
                        title = "No matching expenses",
                        description = "No expense here matches “${model.filter.orEmpty()}”.",
                    )

                    // Typed, but nothing has come back yet — the debounce is still running or
                    // the request is in flight. Narrowing an existing search keeps the previous
                    // results up until the new ones replace them.
                    model.searchResults.isEmpty() -> SearchSkeleton()

                    // The server matched something and the filter took it all away: say which
                    // filter did that, rather than claiming the group has no such expense.
                    filterHidesEverything -> EmptyState(
                        art = EmptyStateArt.Icon(Icons.Outlined.Search),
                        title = "No matching expenses",
                        description = "Nothing paid by ${filterName ?: "that person"} matches " +
                            "“${model.filter.orEmpty()}”.",
                    )

                    else -> Results(
                        sections = visibleSections,
                        filter = model.filter,
                        model = model,
                        onEdit = onEdit,
                    )
                }
            }
        }

        ExpenseUndoBar(model = model, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * The filter for whose expenses are shown, drawn exactly as the expenses tab draws it: a pill
 * that fills with the accent while a person is chosen.
 */
@Composable
private fun SearchFilterButton(
    participants: List<Participant>,
    payerFilter: String?,
    onFilterChange: (String?) -> Unit,
) {
    val active = payerFilter != null
    Box {
        var open by remember { mutableStateOf(false) }

        Surface(
            onClick = { open = true },
            modifier = Modifier.size(46.dp),
            shape = RoundedCornerShape(50),
            color = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.FilterList,
                    contentDescription = "Filter results",
                    modifier = Modifier.size(20.dp),
                    tint = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        ExpressiveMenu(expanded = open, onDismissRequest = { open = false }) {
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

@Composable
private fun Results(
    sections: List<ExpenseSection>,
    filter: String?,
    model: GroupScreenModel,
    onEdit: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sections.forEach { section ->
            item(key = "header-${section.date}") {
                DateBucketHeader(section.title)
            }
            itemsIndexed(
                items = section.expenses,
                key = { _, expense -> expense.id },
            ) { index, expense ->
                // Results open on tap, like the list they came from: deleting lives on the
                // expense itself.
                // The same corner scale as the list these came out of, so a result and its
                // original row are the same card.
                val shape = stackedCardShape(
                    first = index == 0,
                    last = index == section.expenses.lastIndex,
                )
                // The card is the button: its own ripple, clipped to its own corners, rather
                // than a full-width rectangle behind it.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (index == 0) SpliitDimens.Space0 else SpliitDimens.CardGap)
                        .animateItem(),
                ) {
                    Column {
                        ExpenseRow(
                            onClick = { onEdit(expense.id) },
                            expense = expense,
                            payerPosition = model.participantPosition(expense.paidBy.id),
                            formatter = model.moneyFormatter,
                            activeParticipantId = model.actorId,
                            shape = shape,
                            pending = model.isPending(expense.id),
                        )
                        if (matchedInNotes(expense, filter)) {
                            Text(
                                text = "Matched in notes",
                                style = SpliitType.Secondary,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 78.dp, end = 16.dp, bottom = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        if (model.hasMoreSearchResults) {
            item(key = "more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
                LaunchedEffect(model.searchResults.size) { model.loadNextSearchPage() }
            }
        }

        item(key = "bottom") { Spacer(Modifier.padding(bottom = 24.dp)) }
    }
}

/** The results cut down to one person's expenses, in the day sections they already had. */
private fun filteredSearchSections(
    sections: List<ExpenseSection>,
    payerId: String?,
): List<ExpenseSection> {
    if (payerId == null) return sections
    return sections
        .map { section -> section.copy(expenses = section.expenses.filter { it.paidBy.id == payerId }) }
        .filter { it.expenses.isNotEmpty() }
}

/**
 * Against the filter the results answer to, not the field: during the debounce the field is
 * already a character ahead of them.
 */
private fun matchedInNotes(expense: ExpenseListItem, filter: String?): Boolean {
    val query = filter ?: return false
    return ExpenseSearchMatch.of(expense.title, query) == ExpenseSearchMatch.NOTES
}

/** Rows in the shape of the results that are on their way back. */
@Composable
private fun SearchSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(6) { SkeletonRow() }
    }
}
