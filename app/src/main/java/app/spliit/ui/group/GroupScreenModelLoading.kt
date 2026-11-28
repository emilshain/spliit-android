package app.spliit.ui.group

// Loading the group, its pages and the snapshot it is drawn from. Part of
// [GroupScreenModel]: one class, kept one concern per file, with its state in the facade.

import app.spliit.data.local.CachedGroup
import app.spliit.data.local.PendingExpense
import app.spliit.data.local.RecentGroup
import app.spliit.data.remote.SpliitApi
import app.spliit.data.remote.TrpcClient
import app.spliit.data.remote.TrpcServerException
import app.spliit.domain.Failures
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** The outbox as it is on disk: another screen or an earlier launch may have changed it. */
suspend fun GroupScreenModel.refreshPending() {
    pendingExpenses = container.outbox.forGroup(groupId)
}

/**
 * Sends whatever was waiting, oldest first.
 *
 * A change the server accepts leaves the outbox; a change the server refuses is dropped
 * rather than retried forever, because the answer will not change; a change that still cannot
 * be sent stops the run, so the next one is not sent out of order.
 */
suspend fun GroupScreenModel.flushPendingExpenses(): Boolean {
    val current = client ?: return false
    var sent = false
    for (pending in container.outbox.forGroup(groupId)) {
        val values: JsonElement? = pending.valuesJson?.let {
            runCatching { Json.parseToJsonElement(it) }.getOrNull()
        }
        if (pending.kind != PendingExpense.Kind.DELETE && values == null) {
            container.outbox.remove(pending)
            continue
        }
        try {
            when (pending.kind) {
                PendingExpense.Kind.CREATE -> current.call(
                    SpliitApi.createExpenseRaw(
                        groupId = groupId,
                        values = values!!,
                        byParticipantId = pending.actorId,
                        expenseId = pending.key,
                    ),
                )

                PendingExpense.Kind.UPDATE -> current.call(
                    SpliitApi.updateExpenseRaw(
                        groupId = groupId,
                        expenseId = pending.key,
                        values = values!!,
                        byParticipantId = pending.actorId,
                    ),
                )

                PendingExpense.Kind.DELETE -> current.call(
                    SpliitApi.deleteExpenseRaw(groupId, pending.key, pending.actorId),
                )
            }
            container.outbox.remove(pending)
            sent = true
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: TrpcServerException) {
            container.outbox.remove(pending)
            expensesFailure = Failures.message(error)
        } catch (error: Exception) {
            break
        }
    }
    if (sent) refreshPending()
    return sent
}

suspend fun GroupScreenModel.reload() {
    val current = client ?: return
    refreshPending()
    // The last good read goes up before the network is asked, so returning to a group shows
    // what it showed last time instead of a skeleton. A successful read replaces it below;
    // a failed one leaves it there, marked offline.
    if (group == null) container.groupCache.load(groupId)?.let { restoreSnapshot(it) }
    coroutineScope {
        listOf(
            async { loadGroup(current) },
            async { loadFirstPage(current) },
            async { loadBalances(current) },
            async { loadCategories(current) },
            async { loadActivitiesIfNeeded() },
        ).awaitAll()
    }
    // Reached the server: the snapshot on disk becomes this read, and whatever was waiting
    // to be sent is sent now that there is somewhere to send it. Written once, at the end:
    // the file holds the whole group, so a write before the flush would only be undone by
    // the one after it.
    if (group != null && !isOffline) {
        if (flushPendingExpenses()) {
            loadFirstPage(current)
            // What was just sent is part of the server's answer now. Without asking again
            // the cached total stays the one from before it was sent, and the next offline
            // visit would be short by that expense.
            reloadStatsIfRequested(current)
        }
        persistSnapshot()
    }
}

/** Writes the group as it stands, so the next launch has something to open. */
suspend fun GroupScreenModel.persistSnapshot() {
    val currentGroup = group ?: return
    // Details are another reader's cache; this write must not drop them.
    val keptDetails = knownDetails
        ?: container.groupCache.load(groupId)?.details.orEmpty().also { knownDetails = it }
    container.groupCache.save(
        CachedGroup(
            group = currentGroup,
            expenses = expenses,
            balances = balances,
            reimbursements = reimbursements,
            categories = categories,
            activities = activities,
            savedAtEpochMillis = System.currentTimeMillis(),
            hasMoreExpenses = hasMoreExpenses,
            nextCursor = nextCursor,
            // The server's answer, not the queued change added to it: the outbox still has
            // that change, so restoring it would count it twice.
            stats = statsBase,
            statsParticipantId = statsBaseParticipantId,
            categorySpending = categorySpending,
            details = keptDetails,
        ),
    )
}

/**
 * Puts a snapshot on screen and says it is one. Everything the screen asks ("have the
 * expenses loaded?") answers yes, because they have - just not now, and not from here.
 */
suspend fun GroupScreenModel.restoreSnapshot(snapshot: CachedGroup) {
    knownDetails = snapshot.details
    group = snapshot.group
    expenses = snapshot.expenses
    balances = snapshot.balances
    reimbursements = snapshot.reimbursements
    categories = snapshot.categories
    activities = snapshot.activities
    if (snapshot.activities.isNotEmpty()) didLoadActivities = true
    snapshot.stats?.let { saved ->
        statsBase = saved
        statsBaseParticipantId = snapshot.statsParticipantId
        stats = saved
        statsAreLocal = false
        statsParticipantId = snapshot.statsParticipantId
        didRequestStats = true
    }
    if (snapshot.categorySpending.isNotEmpty()) {
        categorySpending = snapshot.categorySpending
        didLoadCategorySpending = true
    }
    nextCursor = snapshot.nextCursor
    hasMoreExpenses = false
    didLoadExpenses = true
    didLoadBalances = true
    groupFailure = null
    expensesFailure = null
    balancesFailure = null
    isOffline = true
    cachedAt = java.time.Instant.ofEpochMilli(snapshot.savedAtEpochMillis)
    refreshActor()
}

fun GroupScreenModel.reloadAfterExpenseChange() {
    scope.launch { reloadAfterExpenseChangeNow() }
}

/**
 * The same reload, driven by a pull on the list.
 *
 * Deliberately not [loadNextPage]: paging appends the next page, while a pull asks the
 * question the screen asks when it opens — the first page, and everything that counts it.
 */
fun GroupScreenModel.refresh() {
    scope.launch {
        isRefreshing = true
        try {
            reloadAfterExpenseChangeNow()
        } finally {
            isRefreshing = false
        }
    }
}

/**
 * Refreshes what an expense change can affect — including an open search, whose results are a
 * view of the same expenses and would otherwise still show the old title, and the activity
 * log, which has just gained the line describing the change.
 */
/**
 * Saves the group's own facts, then re-reads the group so the screen shows what was stored.
 *
 * Throws when the instance refuses, which is what the settings form puts in front of the user.
 */
suspend fun GroupScreenModel.reloadAfterExpenseChangeNow() {
    val current = client ?: return
    // The change just made may already be in the outbox - the save that could not be sent
    // is still a change the person expects to see.
    refreshPending()
    coroutineScope {
        listOf(
            async { loadFirstPage(current) },
            async { loadBalances(current) },
            async { reloadSearchIfRequested(current) },
            async { reloadStatsIfRequested(current) },
            async { reloadActivitiesIfLoaded(current) },
        ).awaitAll()
    }
    // The snapshot is what the page shows next time it opens with no network, so a change
    // written here has to reach it too - otherwise going offline would undo the edit.
    if (!isOffline) {
        // Anything queued elsewhere on the screen (a delete, an import) goes now too.
        if (flushPendingExpenses()) {
            loadFirstPage(current)
            reloadStatsIfRequested(current)
        }
        persistSnapshot()
    }
    refreshPending()
}

suspend fun GroupScreenModel.loadGroup(client: TrpcClient) {
    try {
        val loaded = client.call(SpliitApi.group(groupId)).group
        isOffline = false
        cachedAt = null
        if (loaded == null) {
            groupFailure = "This group no longer exists on this server."
        } else {
            group = loaded
            groupFailure = null
            refreshActor()
            // Opening a group is the plainest statement that it belongs in the list.
            container.recentGroups.remember(
                RecentGroup(
                    groupId = loaded.id,
                    groupName = loaded.name,
                    instanceURL = client.baseUrl,
                ),
            )
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        groupFailure = Failures.message(error)
        if (group != null && isOfflineNetworkFailure(error)) isOffline = true
    }
}

suspend fun GroupScreenModel.loadFirstPage(client: TrpcClient) {
    try {
        val response = client.call(SpliitApi.expenses(groupId, cursor = 0, limit = GroupScreenModel.PAGE_SIZE))
        expenses = withoutPendingDeletion(response.expenses)
        hasMoreExpenses = response.hasMore
        nextCursor = response.nextCursor
        expensesFailure = null
        didLoadExpenses = true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        expensesFailure = Failures.message(error)
        didLoadExpenses = true
        if (expenses.isNotEmpty() && isOfflineNetworkFailure(error)) isOffline = true
    }
}

/**
 * Appends the next page.
 *
 * A failure here keeps what is already on screen and simply stops offering more, rather than
 * replacing the list with an error.
 */
fun GroupScreenModel.loadNextPage() {
    if (!hasMoreExpenses || isLoadingMore) return
    scope.launch {
        val current = client ?: return@launch
        isLoadingMore = true
        try {
            val response = current.call(
                SpliitApi.expenses(groupId, cursor = nextCursor, limit = GroupScreenModel.PAGE_SIZE),
            )
            // Guard against a duplicate page if an expense was added while paging.
            val known = expenses.mapTo(HashSet()) { it.id }
            expenses = expenses + withoutPendingDeletion(response.expenses)
                .filterNot { it.id in known }
            hasMoreExpenses = response.hasMore
            nextCursor = response.nextCursor
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            hasMoreExpenses = false
        } finally {
            isLoadingMore = false
        }
    }
}

suspend fun GroupScreenModel.loadBalances(client: TrpcClient) {
    try {
        val response = client.call(SpliitApi.balances(groupId))
        balances = response.balances
        reimbursements = response.reimbursements
        balancesFailure = null
        didLoadBalances = true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        balancesFailure = Failures.message(error)
        didLoadBalances = true
    }
}

suspend fun GroupScreenModel.loadCategories(client: TrpcClient) {
    if (categories.isNotEmpty()) return
    categories = runCatching { client.call(SpliitApi.categories()).categories }.getOrDefault(emptyList())
}

// MARK: - Totals
