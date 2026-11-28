package app.spliit.ui.group

// Deleting, with a way back. Part of [GroupScreenModel].

import app.spliit.data.local.PendingExpense
import app.spliit.data.model.ExpenseListItem
import app.spliit.data.remote.SpliitApi
import app.spliit.domain.Failures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Takes the expense off the screen and starts the undo window.
 *
 * The row goes immediately, because a delete that waits five seconds to look deleted is a
 * delete that gets tapped twice. The request is what waits.
 */
fun GroupScreenModel.requestDelete(expense: ExpenseListItem) {
    // One at a time: deleting a second expense settles the first rather than queueing it,
    // which keeps "Undo" meaning the thing that just disappeared.
    commitPendingDeletion()
    pendingDeletion = GroupScreenModel.PendingDeletion(
        expense = expense,
        index = expenses.indexOfFirst { it.id == expense.id }.takeIf { it >= 0 },
        searchIndex = searchResults.indexOfFirst { it.id == expense.id }.takeIf { it >= 0 },
        actorId = actorId,
    )
    expenses = expenses.filterNot { it.id == expense.id }
    searchResults = searchResults.filterNot { it.id == expense.id }

    deletionJob?.cancel()
    deletionJob = scope.launch {
        delay(GroupScreenModel.UNDO_WINDOW)
        // Forget the handle before committing: committing cancels whatever is in
        // `deletionJob`, which at this point is this task, and a cancelled task would carry
        // the cancellation into the request.
        deletionJob = null
        commitPendingDeletion()
    }
}

/**
 * The same delete, for a screen that has an expense's identity rather than the row it came
 * from: the expense detail knows everything about one expense except where it sat.
 */
fun GroupScreenModel.requestDelete(expenseId: String) {
    val item = expenses.firstOrNull { it.id == expenseId }
        ?: searchResults.firstOrNull { it.id == expenseId }
        ?: return
    requestDelete(item)
}

/** Puts the row back where it was. Nothing was ever sent, so nothing needs undoing. */
fun GroupScreenModel.undoDelete() {
    deletionJob?.cancel()
    deletionJob = null
    val pending = pendingDeletion ?: return
    pendingDeletion = null

    // Back where it was, and only there: a row that came from the results goes back into
    // the results.
    pending.index?.let { was ->
        expenses = expenses.toMutableList().apply {
            add(was.coerceAtMost(size), pending.expense)
        }
    }

    val searchIndex = pending.searchIndex
    if (searchIndex != null && searchIndex <= searchResults.size) {
        searchResults = searchResults.toMutableList().apply {
            add(searchIndex, pending.expense)
        }
    }
}

/** Sends the delete the undo window was holding back. */
fun GroupScreenModel.commitPendingDeletion() {
    val pending = pendingDeletion ?: return
    deletionJob?.cancel()
    deletionJob = null
    pendingDeletion = null

    scope.launch {
        // A change still waiting has never reached the server, so there is nothing there to
        // delete: the delete is this phone forgetting it, and sending one would leave the
        // create in the outbox instead.
        val waiting = container.outbox.forGroup(groupId)
            .firstOrNull { it.key == pending.expense.id }
        if (waiting?.kind == PendingExpense.Kind.CREATE) {
            container.outbox.remove(waiting)
            refreshPending()
            return@launch
        }
        val current = client ?: return@launch
        try {
            current.call(SpliitApi.deleteExpense(groupId, pending.expense.id, pending.actorId))
            reloadAfterExpenseChangeNow()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isOfflineNetworkFailure(error)) {
                // The delete is kept, and the row stays gone: the intention is the data, not
                // the row. It will be sent when the instance answers.
                container.outbox.put(
                    PendingExpense(
                        key = pending.expense.id,
                        groupId = groupId,
                        kind = PendingExpense.Kind.DELETE,
                        actorId = pending.actorId,
                        title = pending.expense.title,
                        amount = pending.expense.amount,
                        expenseDateEpochMillis = pending.expense.expenseDate.toEpochMilli(),
                        queuedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
                isOffline = true
                refreshPending()
                return@launch
            }
            // The server still has it, so the screen should too.
            pending.index?.let { was ->
                expenses = expenses.toMutableList().apply {
                    add(was.coerceAtMost(size), pending.expense)
                }
            }
            val searchIndex = pending.searchIndex
            if (searchIndex != null && searchIndex <= searchResults.size) {
                searchResults = searchResults.toMutableList().apply {
                    add(searchIndex, pending.expense)
                }
            }
            expensesFailure = Failures.message(error)
        }
    }
}

/**
 * Deletes several expenses at once, for a list that is selecting.
 *
 * A selection has no undo window to hang a single row off, so the rows leave together and
 * each delete is sent in turn. A row that could not be sent is kept in the outbox exactly as
 * a single delete would be; a row the server refused comes back to the list and says why,
 * because a bulk action that silently drops what it could not do is worse than none.
 *
 * A row that has never reached the server — a create still waiting in the outbox — is not
 * deleted at all: the way to get rid of it is to forget the change, which is what this does.
 */
fun GroupScreenModel.deleteExpenses(ids: Collection<String>) {
    if (ids.isEmpty()) return
    val chosen = ids.toList()
    val queued = pendingExpenses.filter { it.key in ids }
    val removed = expenses.filter { it.id in ids }
    val removedSearch = searchResults.filter { it.id in ids }

    expenses = expenses.filterNot { it.id in ids }
    searchResults = searchResults.filterNot { it.id in ids }
    // A queued create is only a row here; the server has never seen it. It leaves the screen
    // now, and the outbox itself is rewritten below, off the main thread.
    pendingExpenses = pendingExpenses.filterNot { it.key in ids }

    scope.launch {
        queued.forEach { container.outbox.remove(it) }
        refreshPending()
        val current = client ?: return@launch
        var failure: String? = null
        val refused = mutableListOf<ExpenseListItem>()
        for (item in removed) {
            try {
                current.call(SpliitApi.deleteExpense(groupId, item.id, actorId))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isOfflineNetworkFailure(error)) {
                    container.outbox.put(
                        PendingExpense(
                            key = item.id,
                            groupId = groupId,
                            kind = PendingExpense.Kind.DELETE,
                            actorId = actorId,
                            title = item.title,
                            amount = item.amount,
                            expenseDateEpochMillis = item.expenseDate.toEpochMilli(),
                            queuedAtEpochMillis = System.currentTimeMillis(),
                        ),
                    )
                    isOffline = true
                } else {
                    refused += item
                    if (failure == null) failure = Failures.message(error)
                }
            }
        }
        if (refused.isNotEmpty()) {
            val known = expenses.mapTo(HashSet()) { it.id }
            expenses = (expenses + refused.filterNot { it.id in known }).sortedByDescending {
                it.expenseDate
            }
            val knownInSearch = searchResults.mapTo(HashSet()) { it.id }
            searchResults = (searchResults + removedSearch.filter {
                it.id in refused.map { refusedRow -> refusedRow.id }
            }.filterNot { it.id in knownInSearch })
        }
        refreshPending()
        failure?.let { expensesFailure = it }
        reloadAfterExpenseChangeNow()
    }
}

/** Closes the window on the way out of the screen. */
fun GroupScreenModel.flushPendingDeletion() {
    val pending = pendingDeletion ?: return
    deletionJob?.cancel()
    deletionJob = null
    pendingDeletion = null

    val current = client ?: return
    scope.launch {
        runCatching { current.call(SpliitApi.deleteExpense(groupId, pending.expense.id, pending.actorId)) }
    }
}

/** Drops anything waiting to be deleted out of a freshly loaded page. */
fun GroupScreenModel.withoutPendingDeletion(items: List<ExpenseListItem>): List<ExpenseListItem> {
    val queued = pendingExpenses.filter { it.kind == PendingExpense.Kind.DELETE }
        .map { it.key }
        .toSet()
    val id = pendingDeletion?.expense?.id
    if (id == null && queued.isEmpty()) return items
    return items.filterNot { it.id == id || it.id in queued }
}
