package app.spliit.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import app.spliit.data.model.ExpenseListItem
import app.spliit.domain.ExpenseSearchMatch
import app.spliit.ui.expenses.ExpenseRow
import app.spliit.ui.expenses.ExpenseUndoBar
import app.spliit.ui.expenses.SwipeableExpense
import app.spliit.ui.group.GroupScreenModel
import app.spliit.ui.theme.DateBucketHeader
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.SpliitType

/**
 * What the search screen shows: the field, and whatever the server matched.
 *
 * The results are their own list rather than a filtered view of the expenses tab. Both are alive
 * at once, and narrowing the shared list would empty the list sitting behind the search field.
 *
 * The server does the matching — `groups.expenses.list` takes a `filter`, matching titles and,
 * since spliit#660, notes — so a search covers the whole group, not just the pages paged in.
 */
@Composable
fun ExpenseSearchTab(
    model: GroupScreenModel,
    query: String,
    onQueryChange: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // Opening search should put the caret in the field.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val trimmed = query.trim()

    // The keyboard is up for as long as this screen is: the empty state and the results have to
    // sit above it rather than behind it.
    Box(modifier = Modifier.fillMaxSize().imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("Search expenses") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Search,
                ),
            )

            // Driven by what is in the field, not by what the model has searched for: the two
            // disagree for the length of the debounce, and deciding on the model alone put the
            // prompt back on screen between characters.
            when {
                trimmed.isEmpty() -> EmptyState(
                    art = EmptyStateArt.Icon(Icons.Filled.Search),
                    title = "Search this group",
                    description = "Type a word from the expense you’re looking for.",
                )

                model.didFailToSearch -> EmptyState(
                    art = EmptyStateArt.Icon(Icons.Filled.WifiOff),
                    title = "Couldn’t search",
                    description = model.searchFailure ?: "The server didn’t respond.",
                )

                model.hasNoMatches -> EmptyState(
                    art = EmptyStateArt.Icon(Icons.Filled.Search),
                    title = "No matching expenses",
                    description = "No expense here matches “${model.filter.orEmpty()}”.",
                )

                // Typed, but nothing has come back yet — the debounce is still running or the
                // request is in flight. Narrowing an existing search keeps the previous results
                // up until the new ones replace them.
                model.searchResults.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                else -> Results(model = model, filter = model.filter, onEdit = onEdit)
            }
        }

        ExpenseUndoBar(model = model, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Results(
    model: GroupScreenModel,
    filter: String?,
    onEdit: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        model.searchSections.forEach { section ->
            item(key = "header-${section.date}") {
                DateBucketHeader(section.title)
            }
            items(section.expenses, key = { it.id }) { expense ->
                SwipeableExpense(
                    onDelete = { model.requestDelete(expense) },
                    onClick = { onEdit(expense.id) },
                    modifier = Modifier.animateItem(),
                ) {
                    Column {
                        ExpenseRow(
                            expense = expense,
                            payerPosition = model.participantPosition(expense.paidBy.id),
                            formatter = model.moneyFormatter,
                            activeParticipantId = model.actorId,
                        )
                        if (matchedInNotes(expense, filter)) {
                            Text(
                                text = "Matched in notes",
                                style = SpliitType.Secondary,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 62.dp, bottom = 8.dp),
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

/**
 * Against the filter the results answer to, not the field: during the debounce the field is
 * already a character ahead of them.
 */
private fun matchedInNotes(expense: ExpenseListItem, filter: String?): Boolean {
    val query = filter ?: return false
    return ExpenseSearchMatch.of(expense.title, query) == ExpenseSearchMatch.NOTES
}
