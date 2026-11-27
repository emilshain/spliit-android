package app.spliit.ui.group

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.spliit.AppContainer
import app.spliit.data.local.CachedGroup
import app.spliit.data.local.PendingExpense
import app.spliit.data.local.RecentGroup
import app.spliit.data.model.Activity
import app.spliit.data.model.Balance
import app.spliit.data.model.ExpenseCategory
import app.spliit.data.model.ExpenseDetails
import app.spliit.data.model.ExpenseListItem
import app.spliit.data.model.ExpenseShareRow
import app.spliit.data.model.Group
import app.spliit.data.model.GroupStatsResponse
import app.spliit.data.model.Participant
import app.spliit.data.model.Reimbursement
import app.spliit.data.model.SplitMode
import app.spliit.data.remote.ExpenseFormValues
import app.spliit.data.remote.GroupFormValues
import app.spliit.data.remote.SpliitApi
import app.spliit.data.remote.TrpcClient
import app.spliit.data.remote.TrpcServerException
import app.spliit.data.remote.groupStats
import app.spliit.domain.CalendarDay
import app.spliit.domain.DateHeading
import app.spliit.domain.CategorySpending
import app.spliit.domain.ExpenseShares
import app.spliit.domain.GroupExport
import app.spliit.domain.MoneyFormatter
import app.spliit.domain.SplitwiseCsv
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything one group's screens need, loaded once and shared by the tabs, so adding an expense
 * updates the balances without either tab knowing about the other.
 *
 * A port of the reference's `GroupDetailModel`: the expenses, balances and identity of Phase 3,
 * and the totals, the category sweep, the activity log and the search of Phase 4.
 */
@Stable
class GroupScreenModel(
    val container: AppContainer,
    val groupId: String,
    private val scope: CoroutineScope,
    /** How long a pause in typing counts as "done typing". Shortened by tests. */
    private val searchDebounceMillis: Long = SEARCH_DEBOUNCE,
) {

    var group by mutableStateOf<Group?>(null)
        private set
    var expenses by mutableStateOf<List<ExpenseListItem>>(emptyList())
        private set

    /**
     * Changes still waiting to reach the server, read back from the outbox on disk.
     *
     * They are part of the list, not a separate section: a person who saved an expense expects
     * to see it where every other expense is, with a mark saying it has not been sent yet.
     */
    var pendingExpenses by mutableStateOf<List<PendingExpense>>(emptyList())
        private set
    var balances by mutableStateOf<Map<String, Balance>>(emptyMap())
        private set
    var reimbursements by mutableStateOf<List<Reimbursement>>(emptyList())
        private set
    var categories by mutableStateOf<List<ExpenseCategory>>(emptyList())
        private set

    var hasMoreExpenses by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set

    /** A pull on the list, which reloads rather than pages. */
    var isRefreshing by mutableStateOf(false)
        private set

    /** One per request, because they run concurrently and finish in any order. */
    var groupFailure by mutableStateOf<String?>(null)
        private set
    var expensesFailure by mutableStateOf<String?>(null)
        private set
    var balancesFailure by mutableStateOf<String?>(null)
        private set

    /**
     * Showing a snapshot rather than the server's answer.
     *
     * Set when a load failed and the cache had the group, cleared by the next load that reaches
     * the server. The screen says so beside the group's status: what is on it is true as of
     * [cachedAt], and nothing new has been read.
     */
    var isOffline by mutableStateOf(false)
        private set

    /** When the snapshot on screen was taken, when [isOffline]. */
    var cachedAt by mutableStateOf<java.time.Instant?>(null)
        private set

    private var didLoadExpenses = false
    private var didLoadBalances = false
    private var nextCursor = 0
    private var client: TrpcClient? = null

    /** Who the local store says the person holding this phone is in this group. */
    var actorId by mutableStateOf<String?>(null)
        private set

    /** The instance this group is on, once the screen knows. */
    var instanceUrl by mutableStateOf<String?>(null)
        private set

    /** What went wrong most recently, preferring the failure that explains the most. */
    val loadFailure: String?
        get() = groupFailure ?: expensesFailure ?: balancesFailure

    val didFailToLoad: Boolean get() = group == null && groupFailure != null

    /** The first page hasn't landed, so an empty list means "not yet", not "none". */
    val isLoadingExpenses: Boolean
        get() = expenses.isEmpty() && (!didLoadExpenses || isLoadingGroup)

    val didFailToLoadExpenses: Boolean
        get() = expenses.isEmpty() && expensesFailure != null

    val isLoadingGroup: Boolean get() = group == null && groupFailure == null

    val isLoadingBalances: Boolean get() = !didLoadBalances

    val didFailToLoadBalances: Boolean
        get() = !didLoadBalances && balancesFailure != null

    /**
     * What each person paid and what they carry, folded from the expenses on screen.
     *
     * The server answers what the group spent and what one participant spent, but nothing that
     * puts a name on every other row. This is the same fold the web app's totals page draws its
     * participant section from: reimbursements are not spending, and a person's share is the
     * apportionment the balances tab charges them.
     */
    val participantTotals: List<ParticipantTotal>
        get() {
            val currentGroup = group ?: return emptyList()
            // The sweep over every page fills these; until it has, the rows on hand answer.
            participantSums?.let { sums ->
                return currentGroup.participants
                    .mapIndexed { position, participant ->
                        ParticipantTotal(
                            participant = participant,
                            position = position,
                            paid = sums.paid[participant.id] ?: 0,
                            share = sums.share[participant.id] ?: 0,
                        )
                    }
                    .sortedWith(
                        compareByDescending<ParticipantTotal> { it.paid }
                            .thenBy { it.position },
                    )
            }
            val counted = displayExpenses.filterNot { it.isReimbursement }
            val paid = HashMap<String, Int>()
            val share = HashMap<String, Int>()
            for (expense in counted) {
                paid[expense.paidBy.id] = (paid[expense.paidBy.id] ?: 0) + expense.amount
                val parts = ExpenseShares.shares(
                    expense.id,
                    expense.amount,
                    expense.splitMode,
                    expense.paidFor,
                )
                for ((id, amount) in parts) {
                    share[id] = (share[id] ?: 0) + amount
                }
            }
            return currentGroup.participants
                .mapIndexed { position, participant ->
                    ParticipantTotal(
                        participant = participant,
                        position = position,
                        paid = paid[participant.id] ?: 0,
                        share = share[participant.id] ?: 0,
                    )
                }
                .sortedWith(
                    compareByDescending<ParticipantTotal> { it.paid }
                        .thenBy { it.position },
                )
        }

    /** Expenses grouped into the sections the list shows, newest bucket first. */
    val sections: List<ExpenseSection>
        get() = sectionsOf(displayExpenses)

    /** Whether a row is a change that has not reached the server yet. */
    fun isPending(expenseId: String): Boolean = pendingExpenses.any { it.key == expenseId }

    /**
     * The server's rows with the waiting changes folded in: a queued create is drawn as itself,
     * and an expense with a queued edit or delete keeps the server's row (the newest version is
     * what will be sent).
     */
    private val displayExpenses: List<ExpenseListItem>
        get() {
            val queued = pendingExpenses
            if (queued.isEmpty()) return expenses
            // A queued delete hides the row; a queued edit keeps it, because the server's
            // version is still the one on screen and the newest version is what will be sent.
            val replaced = queued.filter { it.kind == PendingExpense.Kind.DELETE }
                .map { it.key }
                .toSet()
            val local = queued.filter { it.kind == PendingExpense.Kind.CREATE }
                .mapNotNull { it.toListItem(group) }
            return local + expenses.filterNot { it.id in replaced }
        }

    val moneyFormatter: MoneyFormatter
        get() = MoneyFormatter(group?.currency.orEmpty(), group?.currencyCode)

    fun participant(id: String): Participant? = group?.participants?.firstOrNull { it.id == id }

    /**
     * One expense, for its page and the edit form.
     *
     * A detail that has been read once is kept in the snapshot, so the page still opens with no
     * network; a change still waiting to be sent is drawn from the outbox, because it has never
     * been on the server to be read back.
     */
    suspend fun expense(id: String): ExpenseDetails? {
        val cached = container.groupCache.load(groupId)?.details?.get(id)
        // Whatever was written here is newer than any read, and with no network the read is not
        // going to happen at all.
        val offlineCopy = { pendingDetail(id) ?: cached ?: listDetail(id) }
        val current = client ?: return offlineCopy()
        if (isOffline) return offlineCopy()
        val loaded = runCatching {
            current.call(SpliitApi.expense(groupId, id)).expense
        }.getOrNull()
        if (loaded != null) {
            container.groupCache.saveDetail(groupId, loaded)
            return loaded
        }
        return offlineCopy()
    }

    /**
     * One expense rebuilt from its row in the cached list.
     *
     * The list is what a group keeps for the whole of its history, the detail only for the ones
     * that were opened. Reading a row is not as rich - no notes, no receipts - but it is the
     * expense, and it can be edited, which is what the page is for offline.
     */
    private fun listDetail(id: String): ExpenseDetails? {
        val item = expenses.firstOrNull { it.id == id } ?: return null
        return ExpenseDetails(
            id = item.id,
            groupId = groupId,
            title = item.title,
            amount = item.amount,
            categoryId = item.category?.id ?: 0,
            category = item.category,
            expenseDate = item.expenseDate,
            createdAt = item.createdAt,
            paidById = item.paidBy.id,
            paidBy = item.paidBy,
            paidFor = item.paidFor.map { share ->
                ExpenseDetails.PaidFor(share.participantId, share.shares)
            },
            isReimbursement = item.isReimbursement,
            splitMode = item.splitMode,
            recurrenceRule = item.recurrenceRule,
        )
    }

    /** The detail of a change that has not reached the server, built from what it will send. */
    private fun pendingDetail(id: String): ExpenseDetails? {
        val currentGroup = group ?: return null
        val pending = container.outbox.forGroup(groupId).firstOrNull { it.key == id } ?: return null
        val payer = currentGroup.participants.firstOrNull { it.id == pending.paidById }
            ?: Participant(id = pending.paidById, name = "Someone")
        val category = pending.categoryId.takeIf { it != 0 }?.let {
            ExpenseCategory(
                id = it,
                grouping = pending.categoryGrouping ?: "Uncategorized",
                name = pending.categoryName ?: "General",
            )
        }
        return ExpenseDetails(
            id = pending.key,
            groupId = groupId,
            title = pending.title,
            amount = pending.amount,
            categoryId = pending.categoryId,
            category = category,
            expenseDate = Instant.ofEpochMilli(pending.expenseDateEpochMillis),
            createdAt = Instant.ofEpochMilli(pending.queuedAtEpochMillis),
            paidById = pending.paidById,
            paidBy = payer,
            paidFor = pending.paidFor.map { share ->
                ExpenseDetails.PaidFor(share.participantId, share.shares)
            },
            isReimbursement = pending.isReimbursement,
            splitMode = pending.splitMode,
        )
    }

    /** Where someone sits in the group's participant list, which is what their colour comes from. */
    fun participantPosition(id: String): Int =
        group?.participants?.indexOfFirst { it.id == id }?.takeIf { it >= 0 } ?: 0

    /** Loads everything once per screen. */
    fun start() {
        if (client != null) return
        scope.launch {
            val instance = container.instanceForGroup(groupId)
            instanceUrl = instance
            client = container.client(instance)
            refreshActor()
            reload()
        }
    }

    /**
     * A network has come back: send the backlog, and re-read the group if it was showing a copy.
     *
     * Driven by the screen, not by this model's scope: an observer of a flow that never ends
     * belongs to the screen that is watching it.
     */
    fun retryWhenBackOnline() {
        refreshPending()
        if (!isOffline && pendingExpenses.isEmpty()) return
        if (reconnectJob?.isActive == true) return
        // The network being back does not mean the server answers on the first try: a Wi-Fi
        // handshake, a warm TLS session and a group that was being read all take a moment.
        // Keep trying for a little while rather than waiting for the person to leave and return.
        reconnectJob = scope.launch {
            repeat(RECONNECT_ATTEMPTS) { attempt ->
                reload()
                if (!isOffline && pendingExpenses.isEmpty()) return@launch
                if (attempt < RECONNECT_ATTEMPTS - 1) delay(RECONNECT_DELAY_MS)
            }
        }
    }

    suspend fun refreshActor() {
        val participants = group?.participants.orEmpty()
        actorId = if (participants.isEmpty()) {
            null
        } else {
            container.recentGroups.actorId(groupId, participants)
        }
    }

    fun retry() {
        scope.launch { reload() }
    }

    /** The outbox as it is on disk: another screen or an earlier launch may have changed it. */
    private fun refreshPending() {
        pendingExpenses = container.outbox.forGroup(groupId)
    }

    /**
     * Sends whatever was waiting, oldest first.
     *
     * A change the server accepts leaves the outbox; a change the server refuses is dropped
     * rather than retried forever, because the answer will not change; a change that still cannot
     * be sent stops the run, so the next one is not sent out of order.
     */
    private suspend fun flushPendingExpenses(): Boolean {
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
                expensesFailure = error.localizedMessage
            } catch (error: Exception) {
                break
            }
        }
        if (sent) refreshPending()
        return sent
    }

    private suspend fun reload() {
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
        // to be sent is sent now that there is somewhere to send it.
        if (group != null && !isOffline) {
            persistSnapshot()
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
    private fun persistSnapshot() {
        val currentGroup = group ?: return
        // Details are another reader's cache; this write must not drop them.
        val keptDetails = runCatching {
            container.groupCache.load(groupId)?.details.orEmpty()
        }.getOrDefault(emptyMap())
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
    private suspend fun restoreSnapshot(snapshot: CachedGroup) {
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
    suspend fun updateGroup(values: GroupFormValues) {
        val current = client ?: return
        current.call(SpliitApi.updateGroup(groupId, values, actorId))
        // The stored name is what the group list shows, so a rename has to reach it.
        container.recentGroups.remember(
            RecentGroup(
                groupId = groupId,
                groupName = values.name,
                instanceURL = current.baseUrl,
            ),
        )
        loadGroup(current)
        reloadAfterExpenseChangeNow()
    }

    /** The group's expenses as the instance exports them: the web app's export button, in code. */
    suspend fun exportCsv(): String =
        container.clientForGroup(groupId).text(GroupExport.csvPath(groupId))

    /**
     * The same expenses as a Splitwise file.
     *
     * Built here rather than downloaded: the instance's own export is Spliit's shape, and a file
     * another app can open has to be this one. It walks every page, which is the same sweep the
     * category breakdown does, and folds each one into a line.
     */
    suspend fun exportSplitwiseCsv(): String {
        val current = client ?: error("This group is not ready yet.")
        val currentGroup = group ?: error("This group is not ready yet.")
        val all = mutableListOf<ExpenseListItem>()
        var cursor = 0
        while (true) {
            val page = current.call(
                SpliitApi.expenses(groupId, cursor = cursor, limit = SNAPSHOT_PAGE_SIZE),
            )
            all += page.expenses
            if (!page.hasMore) break
            cursor = page.nextCursor
        }
        return SplitwiseCsv.write(currentGroup, all)
    }

    /** What happened to a file full of expenses. */
    data class ImportOutcome(val created: Int, val failed: Int, val firstFailure: String?)

    /**
     * Creates one expense per row, in file order.
     *
     * Sequential on purpose: an import is a handful of writes to somebody's shared ledger, and
     * firing them all at once makes a partial failure impossible to explain. Each row is resolved
     * against this group - the columns name people, and a name the group has is the participant
     * it means.
     */
    suspend fun importExpenses(
        rows: List<SplitwiseCsv.Row>,
        onProgress: (Int) -> Unit = {},
    ): ImportOutcome {
        val current = client ?: return ImportOutcome(0, rows.size, "This group is not ready yet.")
        val currentGroup = group ?: return ImportOutcome(0, rows.size, "This group is not ready yet.")
        var created = 0
        var failed = 0
        var firstFailure: String? = null

        rows.forEachIndexed { index, row ->
            try {
                val matched = row.participants.mapNotNull { name ->
                    SplitwiseCsv.match(name, currentGroup.participants)
                }
                val involved = matched.ifEmpty { currentGroup.participants }

                val paidFor: List<ExpenseFormValues.PaidFor>
                val splitMode: SplitMode
                val payer: String?
                if (row.owed.isNotEmpty()) {
                    // Shares: each column is what that person owes, in money.
                    paidFor = involved.mapNotNull { participant ->
                        val owed = row.owed.entries.firstOrNull {
                            SplitwiseCsv.participantKey(it.key) ==
                                SplitwiseCsv.participantKey(participant.name)
                        }?.value ?: return@mapNotNull null
                        ExpenseFormValues.PaidFor(participant = participant.id, shares = owed)
                    }
                    splitMode = SplitMode.BY_AMOUNT
                    payer = actorId ?: involved.first().id
                } else {
                    // Payments: the biggest payer paid, everyone named was in on it.
                    val byName = row.payer?.let { name ->
                        involved.firstOrNull {
                            SplitwiseCsv.participantKey(it.name) == SplitwiseCsv.participantKey(name)
                        }
                    }
                    paidFor = involved.map {
                        ExpenseFormValues.PaidFor(participant = it.id, shares = 100)
                    }
                    splitMode = SplitMode.EVENLY
                    payer = byName?.id ?: actorId ?: involved.first().id
                }
                if (paidFor.isEmpty()) error("No participant on this row is in the group.")

                val categoryId = categories.firstOrNull {
                    row.category.isNotBlank() &&
                        (it.name.equals(row.category, ignoreCase = true) ||
                            it.grouping.equals(row.category, ignoreCase = true))
                }?.id ?: 0

                current.call(
                    SpliitApi.createExpense(
                        groupId = groupId,
                        values = ExpenseFormValues(
                            title = row.description,
                            expenseDate = CalendarDay.wire(row.date),
                            amount = row.amount,
                            category = categoryId,
                            paidBy = payer,
                            paidFor = paidFor,
                            splitMode = splitMode,
                        ),
                        byParticipantId = actorId,
                    ),
                )
                created++
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failed++
                if (firstFailure == null) firstFailure = error.localizedMessage
            }
            onProgress(index + 1)
        }

        if (created > 0) reloadAfterExpenseChangeNow()
        return ImportOutcome(created, failed, firstFailure)
    }

    fun reloadAfterExpenseChange() {
        scope.launch { reloadAfterExpenseChangeNow() }
    }

    /**
     * The same reload, driven by a pull on the list.
     *
     * Deliberately not [loadNextPage]: paging appends the next page, while a pull asks the
     * question the screen asks when it opens — the first page, and everything that counts it.
     */
    fun refresh() {
        scope.launch {
            isRefreshing = true
            try {
                reloadAfterExpenseChangeNow()
            } finally {
                isRefreshing = false
            }
        }
    }

    private suspend fun reloadAfterExpenseChangeNow() {
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

    private suspend fun loadGroup(client: TrpcClient) {
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
            groupFailure = error.localizedMessage
            if (group != null && isOfflineNetworkFailure(error)) isOffline = true
        }
    }

    private suspend fun loadFirstPage(client: TrpcClient) {
        try {
            val response = client.call(SpliitApi.expenses(groupId, cursor = 0, limit = PAGE_SIZE))
            expenses = withoutPendingDeletion(response.expenses)
            hasMoreExpenses = response.hasMore
            nextCursor = response.nextCursor
            expensesFailure = null
            didLoadExpenses = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            expensesFailure = error.localizedMessage
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
    fun loadNextPage() {
        if (!hasMoreExpenses || isLoadingMore) return
        scope.launch {
            val current = client ?: return@launch
            isLoadingMore = true
            try {
                val response = current.call(
                    SpliitApi.expenses(groupId, cursor = nextCursor, limit = PAGE_SIZE),
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

    private suspend fun loadBalances(client: TrpcClient) {
        try {
            val response = client.call(SpliitApi.balances(groupId))
            balances = response.balances
            reimbursements = response.reimbursements
            balancesFailure = null
            didLoadBalances = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            balancesFailure = error.localizedMessage
            didLoadBalances = true
        }
    }

    private suspend fun loadCategories(client: TrpcClient) {
        if (categories.isNotEmpty()) return
        categories = runCatching { client.call(SpliitApi.categories()).categories }.getOrDefault(emptyList())
    }

    // MARK: - Totals

    var stats by mutableStateOf<GroupStatsResponse?>(null)
        private set

    var isLoadingStats by mutableStateOf(false)
        private set

    var statsFailure by mutableStateOf<String?>(null)
        private set

    /**
     * This instance answers neither name the totals go by — neither `groups.stats.overview` nor
     * the `groups.stats.get` it replaced. Kept apart from a failure because there is nothing to
     * retry and nothing the user did wrong: the screen says so once and offers no button.
     */
    var statsUnavailable by mutableStateOf(false)
        private set

    private var statsParticipantId: String? = null
    private var didRequestStats = false
    /** The figures on screen are this session's own fold, not an answer from the server. */
    private var statsAreLocal = false
    /**
     * The last answer the server (or a cached one) gave, before any queued change was added.
     *
     * Kept apart from [stats] so a queued create is added once, and so an offline visit can
     * fall back to the whole group's total instead of a fold over whatever pages loaded.
     */
    private var statsBase: GroupStatsResponse? = null
    private var statsBaseParticipantId: String? = null

    val didFailToLoadStats: Boolean get() = stats == null && statsFailure != null && !statsUnavailable

    /**
     * Loads the totals for whoever the user says they are, if they aren't already loaded.
     *
     * Lazy on purpose: it is another request on a screen that already makes several, and the only
     * tab that needs it is the totals tab. Keyed on the participant because two of the three
     * numbers are theirs — answering "who are you?" has to ask the question again.
     */
    suspend fun loadStats(participantId: String?) {
        if (didRequestStats && participantId == statsParticipantId) return
        refreshStats(participantId)
    }

    suspend fun refreshStats(participantId: String?) {
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
                if (statsParticipantId == participantId) statsFailure = error.localizedMessage
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
                    statsFailure = error.localizedMessage
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
    private fun localStats(participantId: String?): GroupStatsResponse {
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
    private fun withPendingStats(
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
    private fun offlineStats(participantId: String?): GroupStatsResponse {
        val base = statsBase?.takeIf { statsBaseParticipantId == participantId }
        if (base != null) {
            statsAreLocal = false
            return withPendingStats(base, participantId)
        }
        statsAreLocal = true
        return localStats(participantId)
    }

    /** The same, fired from a button rather than from a suspending effect. */
    fun refreshStatsNow(participantId: String?) {
        scope.launch { refreshStats(participantId) }
    }

    /** Re-runs the totals for whoever they were last run for, if the tab has been opened once. */
    private suspend fun reloadStatsIfRequested(client: TrpcClient) {
        if (!didRequestStats) return
        coroutineScope {
            listOf(
                async { refreshStats(statsParticipantId) },
                async { refreshCategorySpending() },
            ).awaitAll()
        }
    }

    // MARK: - Spending by category

    var categorySpending by mutableStateOf<List<CategorySpending>>(emptyList())
        private set

    var isLoadingCategorySpending by mutableStateOf(false)
        private set

    var categorySpendingFailure by mutableStateOf<String?>(null)
        private set

    private var didLoadCategorySpending = false

    /** Who paid what, summed over every page the sweep walked. Null until it has. */
    private var participantSums: ParticipantSums? = null

    /**
     * True once every expense has been folded in and the result adds up.
     *
     * The stats endpoint and this sweep count the same expenses the same way — reimbursements out
     * of both — so the breakdown must sum to `totalGroupSpendings`. When it doesn't, the sweep was
     * cut short or the group moved underneath it, and a breakdown that doesn't add up to the total
     * printed above it is worse than no breakdown at all.
     */
    val categorySpendingReconciles: Boolean
        get() {
            val total = stats?.totalGroupSpendings ?: return false
            if (categorySpending.isEmpty()) return false
            return categorySpending.sumOf { it.total } == total
        }

    suspend fun loadCategorySpending() {
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
    suspend fun refreshCategorySpending() {
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
            for (page in 0 until CATEGORY_PAGE_LIMIT) {
                val response = current.call(
                    SpliitApi.expenses(groupId, cursor = cursor, limit = CATEGORY_PAGE_SIZE),
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
                categorySpendingFailure = error.localizedMessage
            }
        } finally {
            isLoadingCategorySpending = false
        }
    }

    // MARK: - Activity

    var activities by mutableStateOf<List<Activity>>(emptyList())
        private set

    var hasMoreActivities by mutableStateOf(false)
        private set

    var isLoadingMoreActivities by mutableStateOf(false)
        private set

    var activitiesFailure by mutableStateOf<String?>(null)
        private set

    private var reconnectJob: Job? = null

    private var didLoadActivities = false
    private var nextActivityCursor = 0

    val isLoadingActivities: Boolean
        get() = activities.isEmpty() && (!didLoadActivities || isLoadingGroup)

    val didFailToLoadActivities: Boolean
        get() = activities.isEmpty() && activitiesFailure != null

    /** The log in its own buckets, newest first. */
    val activitySections: List<ActivitySection>
        get() {
            // An activity of a kind this version does not know is dropped rather than drawn:
            // there is no honest sentence to put on the row.
            val describable = activities.filter { it.activityType.isRecognised }
            val days = LinkedHashMap<LocalDate, MutableList<Activity>>()
            for (activity in describable) {
                val day = activity.time.atZone(ZoneId.systemDefault()).toLocalDate()
                days.getOrPut(day) { mutableListOf() } += activity
            }
            return days.entries
                .sortedByDescending { it.key }
                .map { ActivitySection(it.key, it.value) }
        }

    /**
     * Fetches the log the first time its screen is looked at.
     *
     * Deliberately not part of [start]: the log is one screen nothing else on the group needs, so
     * loading it with the rest would put a request every group open pays for behind the three that
     * draw the screen.
     */
    suspend fun loadActivitiesIfNeeded() {
        if (activities.isNotEmpty() || didLoadActivities) return
        loadFirstActivityPage()
    }

    suspend fun retryActivities() {
        // The group may be what failed, and the log cannot name anybody without it.
        val current = client ?: return
        if (group == null) loadGroup(current)
        loadFirstActivityPage()
    }

    suspend fun refreshActivities() {
        loadFirstActivityPage()
    }

    private suspend fun loadFirstActivityPage() {
        val current = client ?: return
        try {
            val response = current.call(
                SpliitApi.activities(groupId, cursor = 0, limit = PAGE_SIZE),
            )
            activities = response.activities
            hasMoreActivities = response.hasMore
            nextActivityCursor = response.nextCursor
            activitiesFailure = null
            didLoadActivities = true
            // The snapshot is what the log is read from with no network, so it has to hold it.
            persistSnapshot()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // The log was in the snapshot; without a network it is still the log.
            val cached = container.groupCache.load(groupId)?.activities.orEmpty()
            if (cached.isNotEmpty() && isOfflineNetworkFailure(error)) {
                activities = cached
                hasMoreActivities = false
                activitiesFailure = null
                didLoadActivities = true
                isOffline = true
                return
            }
            activitiesFailure = error.localizedMessage
            didLoadActivities = true
        }
    }

    fun loadNextActivityPage() {
        if (!hasMoreActivities || isLoadingMoreActivities) return
        scope.launch {
            val current = client ?: return@launch
            isLoadingMoreActivities = true
            try {
                val response = current.call(
                    SpliitApi.activities(groupId, cursor = nextActivityCursor, limit = PAGE_SIZE),
                )
                // The log grows at the top, so a page fetched after something new was recorded
                // repeats a row rather than skipping one. Same guard as the expense list.
                val known = activities.mapTo(HashSet()) { it.id }
                activities = activities + response.activities.filterNot { it.id in known }
                hasMoreActivities = response.hasMore
                nextActivityCursor = response.nextCursor
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                hasMoreActivities = false
            } finally {
                isLoadingMoreActivities = false
            }
        }
    }

    /** Re-reads the log after a change that wrote to it — but only if it has been read once. */
    private suspend fun reloadActivitiesIfLoaded(client: TrpcClient) {
        if (!didLoadActivities) return
        loadFirstActivityPage()
    }

    // MARK: - Search

    /**
     * Search keeps its own results rather than narrowing [expenses], because the search screen and
     * the expenses tab are both alive — filtering the shared list would empty the list behind the
     * search field.
     */
    var searchResults by mutableStateOf<List<ExpenseListItem>>(emptyList())
        private set

    var hasMoreSearchResults by mutableStateOf(false)
        private set

    var isLoadingMoreSearchResults by mutableStateOf(false)
        private set

    var isSearching by mutableStateOf(false)
        private set

    var searchFailure by mutableStateOf<String?>(null)
        private set

    var filter by mutableStateOf<String?>(null)
        private set

    private var didLoadSearch = false
    private var nextSearchCursor = 0

    val didFailToSearch: Boolean
        get() = searchResults.isEmpty() && searchFailure != null && !isSearching

    /** The search came back empty, which is a different sentence from "nothing typed yet". */
    val hasNoMatches: Boolean
        get() = filter != null && searchResults.isEmpty() && didLoadSearch && !isSearching

    /** Search results in the same buckets, so a result sits under the heading it would have had. */
    val searchSections: List<ExpenseSection>
        get() = sectionsOf(searchResults)

    /**
     * Answers [text], after a pause long enough to mean the typing has stopped.
     *
     * Driven by a `LaunchedEffect` keyed on the field, which cancels the previous call on every
     * keystroke — so the delay below is only ever reached by the last one. Cancellation lands in
     * the delay, before any request goes out and before [filter] moves, which keeps a half-typed
     * word from ever being the state the results are showing.
     */
    suspend fun search(text: String) {
        val trimmed = text.trim()
        val requested = trimmed.ifEmpty { null }
        if (requested == filter) return

        try {
            delay(searchDebounceMillis)
        } catch (error: CancellationException) {
            throw error
        }

        filter = requested
        if (requested == null) {
            // An emptied field is not a search for nothing: drop the results and go back to the
            // prompt rather than asking the server for the whole group again.
            searchResults = emptyList()
            hasMoreSearchResults = false
            searchFailure = null
            didLoadSearch = false
            return
        }
        if (isOffline) {
            loadLocalSearch(requested)
            return
        }
        loadFirstSearchPage(requested)
    }

    private suspend fun loadFirstSearchPage(query: String) {
        val current = client ?: return
        isSearching = true
        searchFailure = null
        try {
            val response = current.call(
                SpliitApi.expenses(groupId, cursor = 0, limit = PAGE_SIZE, filter = query),
            )
            // The field may have moved on while this was in flight; a stale page must not become
            // the answer to a question nobody asked.
            if (filter != query) return
            searchResults = withoutPendingDeletion(response.expenses)
            hasMoreSearchResults = response.hasMore
            nextSearchCursor = response.nextCursor
            didLoadSearch = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (filter == query) {
                if (group != null && isOfflineNetworkFailure(error)) {
                    isOffline = true
                    loadLocalSearch(query)
                } else {
                    searchFailure = error.localizedMessage
                }
            }
        } finally {
            if (filter == query) isSearching = false
        }
    }

    /**
     * The same search, answered from the cached expenses.
     *
     * The server matches titles and notes; the list rows carry the title only, so that is what
     * this matches. It is the honest offline answer, not a claim of a full-text search.
     */
    private fun loadLocalSearch(query: String) {
        val needle = query.trim()
        searchResults = displayExpenses.filter { it.title.contains(needle, ignoreCase = true) }
        hasMoreSearchResults = false
        nextSearchCursor = 0
        searchFailure = null
        didLoadSearch = true
        isSearching = false
    }

    fun loadNextSearchPage() {
        val query = filter ?: return
        if (!hasMoreSearchResults || isLoadingMoreSearchResults) return
        scope.launch {
            val current = client ?: return@launch
            isLoadingMoreSearchResults = true
            try {
                val response = current.call(
                    SpliitApi.expenses(
                        groupId,
                        cursor = nextSearchCursor,
                        limit = PAGE_SIZE,
                        filter = query,
                    ),
                )
                if (filter != query) return@launch
                val known = searchResults.mapTo(HashSet()) { it.id }
                searchResults = searchResults + withoutPendingDeletion(response.expenses)
                    .filterNot { it.id in known }
                hasMoreSearchResults = response.hasMore
                nextSearchCursor = response.nextCursor
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                hasMoreSearchResults = false
            } finally {
                isLoadingMoreSearchResults = false
            }
        }
    }

    /** Re-runs the current search, for when an expense was edited or deleted underneath it. */
    private suspend fun reloadSearchIfRequested(client: TrpcClient) {
        val query = filter ?: return
        loadFirstSearchPage(query)
    }

    // MARK: - Deleting, with a way back

    /** An expense that has left the screen but not yet the server. */
    data class PendingDeletion(
        val expense: ExpenseListItem,
        /** Where the row was, so undo puts it back where it was rather than at the top. */
        val index: Int,
        /** Where the row was in the results, when the delete came from the search screen. */
        val searchIndex: Int?,
        /** Who the activity log should credit, captured when the row was swiped. */
        val actorId: String?,
    )

    var pendingDeletion by mutableStateOf<PendingDeletion?>(null)
        private set

    private var deletionJob: Job? = null

    /**
     * Takes the expense off the screen and starts the undo window.
     *
     * The row goes immediately, because a delete that waits five seconds to look deleted is a
     * delete that gets tapped twice. The request is what waits.
     */
    fun requestDelete(expense: ExpenseListItem) {
        // One at a time: deleting a second expense settles the first rather than queueing it,
        // which keeps "Undo" meaning the thing that just disappeared.
        commitPendingDeletion()
        pendingDeletion = PendingDeletion(
            expense = expense,
            index = expenses.indexOfFirst { it.id == expense.id }.coerceAtLeast(0),
            searchIndex = searchResults.indexOfFirst { it.id == expense.id }.takeIf { it >= 0 },
            actorId = actorId,
        )
        expenses = expenses.filterNot { it.id == expense.id }
        searchResults = searchResults.filterNot { it.id == expense.id }

        deletionJob?.cancel()
        deletionJob = scope.launch {
            delay(UNDO_WINDOW)
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
    fun requestDelete(expenseId: String) {
        val item = expenses.firstOrNull { it.id == expenseId }
            ?: searchResults.firstOrNull { it.id == expenseId }
            ?: return
        requestDelete(item)
    }

    /** Puts the row back where it was. Nothing was ever sent, so nothing needs undoing. */
    fun undoDelete() {
        deletionJob?.cancel()
        deletionJob = null
        val pending = pendingDeletion ?: return
        pendingDeletion = null

        val index = pending.index.coerceAtMost(expenses.size)
        expenses = expenses.toMutableList().apply { add(index, pending.expense) }

        val searchIndex = pending.searchIndex
        if (searchIndex != null && searchIndex <= searchResults.size) {
            searchResults = searchResults.toMutableList().apply {
                add(searchIndex, pending.expense)
            }
        }
    }

    /** Sends the delete the undo window was holding back. */
    fun commitPendingDeletion() {
        val pending = pendingDeletion ?: return
        deletionJob?.cancel()
        deletionJob = null
        pendingDeletion = null

        scope.launch {
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
                val index = pending.index.coerceAtMost(expenses.size)
                expenses = expenses.toMutableList().apply { add(index, pending.expense) }
                val searchIndex = pending.searchIndex
                if (searchIndex != null && searchIndex <= searchResults.size) {
                    searchResults = searchResults.toMutableList().apply {
                        add(searchIndex, pending.expense)
                    }
                }
                expensesFailure = error.localizedMessage
            }
        }
    }

    /** Closes the window on the way out of the screen. */
    fun flushPendingDeletion() {
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
    private fun withoutPendingDeletion(items: List<ExpenseListItem>): List<ExpenseListItem> {
        val queued = pendingExpenses.filter { it.kind == PendingExpense.Kind.DELETE }
            .map { it.key }
            .toSet()
        val id = pendingDeletion?.expense?.id
        if (id == null && queued.isEmpty()) return items
        return items.filterNot { it.id == id || it.id in queued }
    }

    private fun sectionsOf(items: List<ExpenseListItem>): List<ExpenseSection> {
        val days = LinkedHashMap<LocalDate, MutableList<ExpenseListItem>>()
        for (expense in items) {
            val day = CalendarDay.dayOf(expense.expenseDate)
            days.getOrPut(day) { mutableListOf() } += expense
        }
        return days.entries
            .sortedByDescending { it.key }
            .map { ExpenseSection(it.key, it.value) }
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val UNDO_WINDOW = 5_000L
        const val SEARCH_DEBOUNCE = 250L

        /** How many expenses one sweep page asks for, and how many pages it will walk. */
        const val SNAPSHOT_PAGE_SIZE = 100
        const val CATEGORY_PAGE_SIZE = 100
        const val CATEGORY_PAGE_LIMIT = 20
    }
}

/** Whether a failure means the instance could not be reached, rather than answered and refused. */
private fun isOfflineNetworkFailure(error: Exception): Boolean =
    error is IOException && (error !is TrpcServerException || error.httpStatus >= 500)

/** One day's expenses, in the order the server sent them. */
data class ExpenseSection(
    val date: LocalDate,
    val expenses: List<ExpenseListItem>,
) {
    /** "NOV 25, 2026 WED". */
    val title: String get() = DateHeading.of(date)
}

/** One day of the activity log, newest first. */
/** The running sums behind [GroupScreenModel.participantTotals]. */
private data class ParticipantSums(
    val paid: Map<String, Int>,
    val share: Map<String, Int>,
)

/** What one person paid into the group, and what they carry of it. */
data class ParticipantTotal(
    val participant: Participant,
    /** Where they sit in the group's list, which is what their colour comes from. */
    val position: Int,
    /** Minor units of the expenses they paid. */
    val paid: Int,
    /** Minor units of the expenses they were part of, as the balances tab charges them. */
    val share: Int,
)

data class ActivitySection(
    val date: LocalDate,
    val activities: List<Activity>,
) {
    val title: String get() = DateHeading.of(date)
}

/** How many times a group that came back into signal asks the server before giving up. */
private const val RECONNECT_ATTEMPTS = 8

/** The pause between those attempts: long enough for a handshake, short enough to feel instant. */
private const val RECONNECT_DELAY_MS = 4_000L
