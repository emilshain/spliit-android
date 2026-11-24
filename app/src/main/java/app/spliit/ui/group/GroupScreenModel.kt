package app.spliit.ui.group

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.spliit.AppContainer
import app.spliit.data.local.RecentGroup
import app.spliit.data.model.Activity
import app.spliit.data.model.Balance
import app.spliit.data.model.ExpenseCategory
import app.spliit.data.model.ExpenseDetails
import app.spliit.data.model.ExpenseListItem
import app.spliit.data.model.Group
import app.spliit.data.model.GroupStatsResponse
import app.spliit.data.model.Participant
import app.spliit.data.model.Reimbursement
import app.spliit.data.remote.SpliitApi
import app.spliit.data.remote.TrpcClient
import app.spliit.data.remote.TrpcServerException
import app.spliit.data.remote.groupStats
import app.spliit.domain.ActivityDateGroup
import app.spliit.domain.CalendarDay
import app.spliit.domain.CategorySpending
import app.spliit.domain.ExpenseDateGroup
import app.spliit.domain.MoneyFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    /** Expenses grouped into the sections the list shows, newest bucket first. */
    val sections: List<ExpenseSection>
        get() = sectionsOf(expenses)

    val moneyFormatter: MoneyFormatter
        get() = MoneyFormatter(group?.currency.orEmpty(), group?.currencyCode)

    fun participant(id: String): Participant? = group?.participants?.firstOrNull { it.id == id }

    /** One expense, for the edit form. Null when it has gone or the server refused. */
    suspend fun expense(id: String): ExpenseDetails? =
        runCatching { client?.call(SpliitApi.expense(groupId, id))?.expense }.getOrNull()

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

    private suspend fun reload() {
        val current = client ?: return
        coroutineScope {
            listOf(
                async { loadGroup(current) },
                async { loadFirstPage(current) },
                async { loadBalances(current) },
                async { loadCategories(current) },
            ).awaitAll()
        }
    }

    /**
     * Refreshes what an expense change can affect — including an open search, whose results are a
     * view of the same expenses and would otherwise still show the old title, and the activity
     * log, which has just gained the line describing the change.
     */
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
        coroutineScope {
            listOf(
                async { loadFirstPage(current) },
                async { loadBalances(current) },
                async { reloadSearchIfRequested(current) },
                async { reloadStatsIfRequested(current) },
                async { reloadActivitiesIfLoaded(current) },
            ).awaitAll()
        }
    }

    private suspend fun loadGroup(client: TrpcClient) {
        try {
            val loaded = client.call(SpliitApi.group(groupId)).group
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

        try {
            val response = current.groupStats(groupId, participantId)
            // The picker may have moved on while this was in flight; somebody else's share must
            // not become the answer under your name.
            if (statsParticipantId != participantId) return
            stats = response
            statsUnavailable = false
        } catch (error: CancellationException) {
            throw error
        } catch (error: TrpcServerException) {
            if (!error.isUnknownProcedure) {
                if (statsParticipantId == participantId) statsFailure = error.localizedMessage
                return
            }
            if (statsParticipantId != participantId) return
            statsUnavailable = true
            statsFailure = null
        } catch (error: Exception) {
            if (statsParticipantId == participantId) statsFailure = error.localizedMessage
        } finally {
            if (statsParticipantId == participantId) isLoadingStats = false
        }
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

        val fold = CategorySpending.Fold()
        var cursor = 0
        try {
            for (page in 0 until CATEGORY_PAGE_LIMIT) {
                val response = current.call(
                    SpliitApi.expenses(groupId, cursor = cursor, limit = CATEGORY_PAGE_SIZE),
                )
                fold.add(response.expenses)
                cursor = response.nextCursor
                if (!response.hasMore) break
            }
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
            categorySpendingFailure = error.localizedMessage
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
            val buckets = LinkedHashMap<ActivityDateGroup, MutableList<Activity>>()
            for (activity in describable) {
                buckets.getOrPut(ActivityDateGroup.containing(activity.time)) { mutableListOf() } +=
                    activity
            }
            return buckets.entries
                .sortedBy { it.key }
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
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
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
            if (filter == query) searchFailure = error.localizedMessage
        } finally {
            if (filter == query) isSearching = false
        }
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
        val id = pendingDeletion?.expense?.id ?: return items
        return items.filterNot { it.id == id }
    }

    private fun sectionsOf(items: List<ExpenseListItem>): List<ExpenseSection> {
        val buckets = LinkedHashMap<ExpenseDateGroup, MutableList<ExpenseListItem>>()
        for (expense in items) {
            val bucket = ExpenseDateGroup.containing(CalendarDay.dayOf(expense.expenseDate))
            buckets.getOrPut(bucket) { mutableListOf() } += expense
        }
        return buckets.entries
            .sortedBy { it.key }
            .map { ExpenseSection(it.key, it.value) }
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val UNDO_WINDOW = 5_000L
        const val SEARCH_DEBOUNCE = 250L

        /** How many expenses one sweep page asks for, and how many pages it will walk. */
        const val CATEGORY_PAGE_SIZE = 100
        const val CATEGORY_PAGE_LIMIT = 20
    }
}

/** One date bucket and the expenses in it, in the order the server sent them. */
data class ExpenseSection(
    val group: ExpenseDateGroup,
    val expenses: List<ExpenseListItem>,
)

/** One bucket of the activity log and the entries in it, newest first. */
data class ActivitySection(
    val group: ActivityDateGroup,
    val activities: List<Activity>,
)
