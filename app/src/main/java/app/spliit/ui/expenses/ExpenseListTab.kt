package app.spliit.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.spliit.ui.group.GroupScreenModel
import app.spliit.ui.theme.DateBucketHeader
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.SpliitType

/**
 * A group's expenses, newest first, grouped into the same date buckets the old app used so an
 * upgrading user sees a familiar list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListTab(
    model: GroupScreenModel,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            model.isLoadingExpenses -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

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
                description = "Add the first expense and Spliit will work out who owes what.",
            ) {
                Button(onClick = onAdd) { Text("Add expense") }
            }

            else -> PullToRefreshBox(
                // A pull reloads the first page and everything that counts it; paging is the
                // footer's job and must not be what the spinner reports.
                isRefreshing = model.isRefreshing,
                onRefresh = { model.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
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

                    model.sections.forEach { section ->
                        item(key = "header-${section.group.name}") {
                            DateBucketHeader(section.group.title)
                        }
                        itemsIndexed(
                            items = section.expenses,
                            key = { _, expense -> expense.id },
                        ) { _, expense ->
                            SwipeableExpense(
                                onDelete = { model.requestDelete(expense) },
                                onClick = { onEdit(expense.id) },
                            ) {
                                ExpenseRow(
                                    expense = expense,
                                    payerPosition = model.participantPosition(expense.paidBy.id),
                                    formatter = model.moneyFormatter,
                                    activeParticipantId = model.actorId,
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

                    item(key = "bottom") { Spacer(Modifier.padding(bottom = 24.dp)) }
                }
            }
        }

        ExpenseUndoBar(
            model = model,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
