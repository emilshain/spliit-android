package app.spliit.ui.group

// The figures: what the group spent, what each person did, and what it was spent on.
// Part of [GroupScreenModel].

import app.spliit.data.local.PendingExpense
import app.spliit.data.model.Activity
import app.spliit.data.model.ExpenseListItem
import app.spliit.data.model.ExpenseShareRow
import app.spliit.data.model.GroupStatsResponse
import app.spliit.data.remote.SpliitApi
import app.spliit.data.remote.TrpcClient
import app.spliit.data.remote.TrpcServerException
import app.spliit.data.remote.groupStats
import app.spliit.domain.CategorySpending
import app.spliit.domain.ExpenseShares
import app.spliit.domain.Failures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Loads the totals for whoever the user says they are, if they aren't already loaded.
 *
 * Lazy on purpose: it is another request on a screen that already makes several, and the only
 * tab that needs it is the totals tab. Keyed on the participant because two of the three
 * numbers are theirs — answering "who are you?" has to ask the question again.
 */
suspend fun GroupScreenModel.loadStats(participantId: String?) {
    if (didRequestStats && participantId == statsParticipantId) return
    refreshStats(participantId)
}

suspend fun GroupScreenModel.refreshStats(participantId: String?) {
    val current = client ?: return
    didRequestStats = true
    statsParticipantId = participantId
    isLoadingStats = true
    statsFailure = null

    // The figures the cached expenses already answer go up before the request: the tab is
    // worth reading immediately, and the server's answer replaces them when it lands.
    if (stats == null) {
        stats = localStats(participantId)
        statsAreLocal = true
        statsUnavailable = false
    }

    // With no network there is still an answer: the last whole-group total when there is
    // one, and otherwise a fold over the cached expenses, which covers only the pages that
    // were loaded. A stale total that adds up beats a page saying it could not be read.
    if (isOffline) {
        stats = offlineStats(participantId)
        statsUnavailable = false
        isLoadingStats = false
        return
    }

    try {
        val response = current.groupStats(groupId, participantId)
        // The picker may have moved on while this was in flight; somebody else's share must
        // not become the answer under your name.
        if (statsParticipantId != participantId) return
        statsBase = response
        statsBaseParticipantId = participantId
        stats = withPendingStats(response, participantId)
        statsAreLocal = false
        statsUnavailable = false
        // The group's whole total belongs in the snapshot: a later offline visit has no way
        // to fold the pages it never loaded.
        persistSnapshot()
    } catch (error: CancellationException) {
        throw error
    } catch (error: TrpcServerException) {
        // A request that failed is not a total: the local seed must not stand in for an
        // answer the server refused, or the tab would show a number it cannot vouch for.
        if (statsParticipantId == participantId && statsAreLocal) stats = null
        if (!error.isUnknownProcedure) {
            if (statsParticipantId == participantId) statsFailure = Failures.message(error)
            return
        }
        if (statsParticipantId != participantId) return
        statsUnavailable = true
        statsFailure = null
    } catch (error: Exception) {
        if (statsParticipantId == participantId) {
            if (group != null && isOfflineNetworkFailure(error)) {
                isOffline = true
                stats = offlineStats(participantId)
                statsFailure = null
            } else {
                if (statsAreLocal) stats = null
                statsFailure = Failures.message(error)
            }
        }
    } finally {
        if (statsParticipantId == participantId) isLoadingStats = false
    }
}

/**
 * The totals over the cached expenses.
 *
 * Same rules as the server's: reimbursements are not spending, and a participant's share is
 * the same apportionment the balances tab charges. It covers the cached pages only, which is
 * exactly the data the rest of the screen is showing.
 */
fun GroupScreenModel.localStats(participantId: String?): GroupStatsResponse {
    // `displayExpenses`, not `expenses`: a change waiting to be sent is spending too.
    val counted = displayExpenses.filterNot { it.isReimbursement }
    val groupSpendings = counted.sumOf { it.amount }
    val spendings = participantId?.let { id ->
        counted.filter { it.paidBy.id == id }.sumOf { it.amount }
    }
    val share = participantId?.let { id ->
        counted.sumOf { expense ->
            ExpenseShares.shares(
                expense.id,
                expense.amount,
                expense.splitMode,
                expense.paidFor,
            )[id] ?: 0
        }
    }?.toDouble()
    return GroupStatsResponse(
        totalGroupSpendings = groupSpendings,
        totalParticipantSpendings = spendings,
        totalParticipantShare = share,
    )
}

/**
 * The server's totals with the changes still waiting added in.
 *
 * A queued create has been saved, so it is spending — the server simply has not seen it yet.
 * Only creates are added: an edit keeps the server's own row, and a delete is already off the
 * list. Adding them here is what makes the totals agree with the rows on screen.
 */
fun GroupScreenModel.withPendingStats(
    response: GroupStatsResponse,
    participantId: String?,
): GroupStatsResponse {
    val queued = pendingExpenses.filter {
        it.kind == PendingExpense.Kind.CREATE && !it.isReimbursement
    }
    if (queued.isEmpty()) return response
    val group = queued.sumOf { it.amount }
    val spent = participantId?.let { id ->
        queued.filter { it.paidById == id }.sumOf { it.amount }
    }
    val share = participantId?.let { id ->
        queued.sumOf { pending ->
            ExpenseShares.shares(
                pending.key,
                pending.amount,
                pending.splitMode,
                pending.paidFor.map { row ->
                    object : ExpenseShareRow {
                        override val participantId: String = row.participantId
                        override val shares: Int = row.shares
                    }
                },
            )[id] ?: 0
        }
    }?.toDouble()
    return GroupStatsResponse(
        totalGroupSpendings = response.totalGroupSpendings + group,
        totalParticipantSpendings = response.totalParticipantSpendings
            ?.let { it + (spent ?: 0) },
        totalParticipantShare = response.totalParticipantShare
            ?.let { it + (share ?: 0.0) },
    )
}

/**
 * What the totals can honestly say with no network.
 *
 * The cached answer covers the whole group and the local fold covers only the pages that
 * loaded, so the cached one wins whenever it is for the same person; the queued creates are
 * added either way, because they have been saved.
 */
fun GroupScreenModel.offlineStats(participantId: String?): GroupStatsResponse {
    val base = statsBase?.takeIf { statsBaseParticipantId == participantId }
    if (base != null) {
        statsAreLocal = false
        return withPendingStats(base, participantId)
    }
    statsAreLocal = true
    return localStats(participantId)
}

/** The same, fired from a button rather than from a suspending effect. */
fun GroupScreenModel.refreshStatsNow(participantId: String?) {
    scope.launch { refreshStats(participantId) }
}

/** Re-runs the totals for whoever they were last run for, if the tab has been opened once. */
suspend fun GroupScreenModel.reloadStatsIfRequested(client: TrpcClient) {
    if (!didRequestStats) return
    coroutineScope {
        listOf(
            async { refreshStats(statsParticipantId) },
            async { refreshCategorySpending() },
        ).awaitAll()
    }
}

// MARK: - Spending by category

suspend fun GroupScreenModel.loadCategorySpending() {
    if (didLoadCategorySpending || isLoadingCategorySpending) return
    refreshCategorySpending()
}

/**
 * Sweeps the whole expense list, keeping only the sums.
 *
 * Each page is folded and let go, so what this holds is one page and a running total per
 * category however large the group is. The page cap keeps this from being an unbounded loop;
 * overrunning it is not a silent half-answer, because the section only draws when its total
 * reconciles with the group's.
 */
suspend fun GroupScreenModel.refreshCategorySpending() {
    val current = client ?: return
    isLoadingCategorySpending = true
    categorySpendingFailure = null

    // What the expenses on hand already say goes up first; the sweep replaces it when it
    // lands. The section only draws once it reconciles with the group total either way.
    if (categorySpending.isEmpty()) {
        categorySpending = CategorySpending.breakdownOf(displayExpenses)
    }

    // Offline the sweep is the same fold over what is already on hand.
    if (isOffline) {
        categorySpending = CategorySpending.breakdownOf(displayExpenses)
        didLoadCategorySpending = true
        isLoadingCategorySpending = false
        return
    }

    val fold = CategorySpending.Fold()
    val paid = HashMap<String, Int>()
    val share = HashMap<String, Int>()
    fun accumulate(expenses: List<ExpenseListItem>) {
        for (expense in expenses) {
            if (expense.isReimbursement) continue
            paid[expense.paidBy.id] = (paid[expense.paidBy.id] ?: 0) + expense.amount
            ExpenseShares.shares(
                expense.id,
                expense.amount,
                expense.splitMode,
                expense.paidFor,
            ).forEach { (id, amount) -> share[id] = (share[id] ?: 0) + amount }
        }
    }
    var cursor = 0
    try {
        for (page in 0 until GroupScreenModel.CATEGORY_PAGE_LIMIT) {
            val response = current.call(
                SpliitApi.expenses(groupId, cursor = cursor, limit = GroupScreenModel.CATEGORY_PAGE_SIZE),
            )
            fold.add(response.expenses)
            accumulate(response.expenses)
            cursor = response.nextCursor
            if (!response.hasMore) break
        }
        val queued = pendingExpenses.mapNotNull { it.toListItem(group) }
        fold.add(queued)
        accumulate(queued)
        participantSums = ParticipantSums(paid, share)
        categorySpending = fold.breakdown
        didLoadCategorySpending = true
    } catch (error: CancellationException) {
        // A cancelled sweep is not a failed one, but leaving it mid-flight would leave the
        // section spinning; it goes back to never-asked so the next visit tries again.
        categorySpendingFailure = null
        isLoadingCategorySpending = false
        throw error
    } catch (error: Exception) {
        // What was already folded stays on screen; it is only drawn while it reconciles, so a
        // stale breakdown either still adds up — in which case it is still true — or stops
        // being shown on its own.
        if (group != null && isOfflineNetworkFailure(error)) {
            isOffline = true
            participantSums = null
            categorySpending = CategorySpending.breakdownOf(displayExpenses)
            didLoadCategorySpending = true
            categorySpendingFailure = null
        } else {
            categorySpendingFailure = Failures.message(error)
        }
    } finally {
        isLoadingCategorySpending = false
    }
}

// MARK: - Activity
