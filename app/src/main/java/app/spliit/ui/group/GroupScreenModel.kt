package app.spliit.ui.group

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.spliit.AppContainer
import app.spliit.data.local.RecentGroup
import app.spliit.data.model.Balance
import app.spliit.data.model.ExpenseCategory
import app.spliit.data.model.ExpenseDetails
import app.spliit.data.model.ExpenseListItem
import app.spliit.data.model.Group
import app.spliit.data.model.Participant
import app.spliit.data.model.Reimbursement
import app.spliit.data.remote.SpliitApi
import app.spliit.data.remote.TrpcClient
import app.spliit.domain.CalendarDay
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
 * A port of the reference's `GroupDetailModel`, narrowed to the three tabs this phase builds:
 * expenses, balances and the identity the balances are read from.
 */
@Stable
class GroupScreenModel(
    val container: AppContainer,
    val groupId: String,
    private val scope: CoroutineScope,
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
        get() {
            val buckets = LinkedHashMap<ExpenseDateGroup, MutableList<ExpenseListItem>>()
            for (expense in expenses) {
                val bucket = ExpenseDateGroup.containing(CalendarDay.dayOf(expense.expenseDate))
                buckets.getOrPut(bucket) { mutableListOf() } += expense
            }
            return buckets.entries
                .sortedBy { it.key }
                .map { ExpenseSection(it.key, it.value) }
        }

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

    /** Refreshes what an expense change can affect. */
    fun reloadAfterExpenseChange() {
        scope.launch {
            val current = client ?: return@launch
            coroutineScope {
                listOf(
                    async { loadFirstPage(current) },
                    async { loadBalances(current) },
                ).awaitAll()
            }
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

    // MARK: - Deleting, with a way back

    /** An expense that has left the screen but not yet the server. */
    data class PendingDeletion(
        val expense: ExpenseListItem,
        /** Where the row was, so undo puts it back where it was rather than at the top. */
        val index: Int,
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
            actorId = actorId,
        )
        expenses = expenses.filterNot { it.id == expense.id }

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
                reloadAfterExpenseChange()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // The server still has it, so the screen should too.
                val index = pending.index.coerceAtMost(expenses.size)
                expenses = expenses.toMutableList().apply { add(index, pending.expense) }
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

    private companion object {
        const val PAGE_SIZE = 20
        const val UNDO_WINDOW = 5_000L
    }
}

/** One date bucket and the expenses in it, in the order the server sent them. */
data class ExpenseSection(
    val group: ExpenseDateGroup,
    val expenses: List<ExpenseListItem>,
)
