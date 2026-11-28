package app.spliit.ui.group

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.spliit.AppContainer
import app.spliit.data.local.PendingExpense
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
import app.spliit.domain.CategorySpending
import app.spliit.domain.ExpenseShares
import app.spliit.domain.MoneyFormatter
import app.spliit.domain.ServerInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything one group's screens need, loaded once and shared by the tabs, so adding an expense
 * updates the balances without either tab knowing about the other.
 *
 * A port of the reference's `GroupDetailModel`: the expenses, balances and identity of Phase 3,
 * and the totals, the category sweep, the activity log and the search of Phase 4.
 *
 * The class is the screen's whole view of a group, and the state it holds lives here; what is
 * done with that state is kept one concern per file beside it — loading and snapshots, the
 * group's own settings and exports, the figures, the activity log, search, and deleting. Screens
 * see one object either way.
 */
@Stable
class GroupScreenModel(
    val container: AppContainer,
    val groupId: String,
    internal val scope: CoroutineScope,
    /** How long a pause in typing counts as "done typing". Shortened by tests. */
    internal val searchDebounceMillis: Long = SEARCH_DEBOUNCE,
) {

    var group by mutableStateOf<Group?>(null)
        internal set
    var expenses by mutableStateOf<List<ExpenseListItem>>(emptyList())
        internal set

    /**
     * Changes still waiting to reach the server, read back from the outbox on disk.
     *
     * They are part of the list, not a separate section: a person who saved an expense expects
     * to see it where every other expense is, with a mark saying it has not been sent yet.
     */
    var pendingExpenses by mutableStateOf<List<PendingExpense>>(emptyList())
        internal set
    var balances by mutableStateOf<Map<String, Balance>>(emptyMap())
        internal set
    var reimbursements by mutableStateOf<List<Reimbursement>>(emptyList())
        internal set
    var categories by mutableStateOf<List<ExpenseCategory>>(emptyList())
        internal set

    var hasMoreExpenses by mutableStateOf(false)
        internal set
    var isLoadingMore by mutableStateOf(false)
        internal set

    /** A pull on the list, which reloads rather than pages. */
    var isRefreshing by mutableStateOf(false)
        internal set

    /** One per request, because they run concurrently and finish in any order. */
    var groupFailure by mutableStateOf<String?>(null)
        internal set
    var expensesFailure by mutableStateOf<String?>(null)
        internal set
    var balancesFailure by mutableStateOf<String?>(null)
        internal set

    /**
     * Showing a snapshot rather than the server's answer.
     *
     * Set when a load failed and the cache had the group, cleared by the next load that reaches
     * the server. The screen says so beside the group's status: what is on it is true as of
     * [cachedAt], and nothing new has been read.
     */
    var isOffline by mutableStateOf(false)
        internal set

    /** When the snapshot on screen was taken, when [isOffline]. */
    var cachedAt by mutableStateOf<java.time.Instant?>(null)
        internal set

    internal var didLoadExpenses = false
    internal var didLoadBalances = false
    internal var nextCursor = 0
    internal var client: TrpcClient? = null

    /** Who the local store says the person holding this phone is in this group. */
    var actorId by mutableStateOf<String?>(null)
        internal set

        /** The instance this group is on, once the screen knows. */
    var instanceUrl by mutableStateOf<String?>(null)
        internal set

    /**
     * Whether the instance behind this group stores receipt photographs.
     *
     * Spliit makes expense documents opt-in, and an instance without an S3 bucket simply refuses
     * the upload. Rather than offering a button that can only fail, the form is told before it is
     * opened; until the instance has answered the feature stays as it was, so a slow probe never
     * removes something the person was about to use.
     */
    var receiptsSupported by mutableStateOf(true)
        internal set

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
    // Derived rather than recomputed: a fold over every expense is not something to run again
    // each time the composition reads the property, and the snapshot system can tell the reader
    // that the answer has not changed. It recomputes only when the group, its expenses or the
    // sweep's sums actually do.
    val participantTotals: List<ParticipantTotal> by derivedStateOf {
        val currentGroup = group
        if (currentGroup == null) {
            emptyList()
        } else {
            val sums = participantSums
            if (sums != null) {
                // The sweep over every page fills these; until it has, the rows on hand answer.
                currentGroup.participants
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
            } else {
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
                currentGroup.participants
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
        }
    }

    /** Expenses grouped into the sections the list shows, newest bucket first. */
    val sections: List<ExpenseSection> by derivedStateOf { sectionsOf(displayExpenses) }

    /** Whether a row is a change that has not reached the server yet. */
    fun isPending(expenseId: String): Boolean = pendingExpenses.any { it.key == expenseId }

    /**
     * The server's rows with the waiting changes folded in: a queued create is drawn as itself,
     * and an expense with a queued edit or delete keeps the server's row (the newest version is
     * what will be sent).
     */
    internal val displayExpenses: List<ExpenseListItem> by derivedStateOf {
        val queued = pendingExpenses
        if (queued.isEmpty()) {
            expenses
        } else {
            // A queued delete hides the row; a queued edit keeps it, because the server's
            // version is still the one on screen and the newest version is what will be sent.
            val replaced = queued.filter { it.kind == PendingExpense.Kind.DELETE }
                .map { it.key }
                .toSet()
            val local = queued.filter { it.kind == PendingExpense.Kind.CREATE }
                .mapNotNull { it.toListItem(group) }
            local + expenses.filterNot { it.id in replaced }
        }
    }

    /** One formatter per group, not one per read: every row on screen asks for it. */
    val moneyFormatter: MoneyFormatter by derivedStateOf {
        MoneyFormatter(group?.currency.orEmpty(), group?.currencyCode)
    }

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
        val queued = pendingDetail(id)
        val offlineCopy = { queued ?: cached ?: listDetail(id) }
        val current = client ?: return offlineCopy()
        if (isOffline) return offlineCopy()
        val loaded = runCatching {
            current.call(SpliitApi.expense(groupId, id)).expense
        }.getOrNull()
        if (loaded != null) {
            knownDetails = (knownDetails ?: emptyMap()) + (loaded.id to loaded)
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
    internal fun listDetail(id: String): ExpenseDetails? {
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
    internal suspend fun pendingDetail(id: String): ExpenseDetails? {
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
            scope.launch {
                // spliit.app is the hosted instance, and it runs with document storage off; a
                // group there never gets the upload, so it is not asked for one.
                receiptsSupported = if (ServerInstance.isHosted(instance)) {
                    false
                } else {
                    runCatching {
                        container.client(instance).supportsDocuments()
                    }.getOrDefault(true)
                }
            }
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
        if (reconnectJob?.isActive == true) return
        // The network being back does not mean the server answers on the first try: a Wi-Fi
        // handshake, a warm TLS session and a group that was being read all take a moment.
        // Keep trying for a little while rather than waiting for the person to leave and return.
        reconnectJob = scope.launch {
            // Reading the outbox is a file, so it happens in here rather than before the launch.
            refreshPending()
            if (!isOffline && pendingExpenses.isEmpty()) return@launch
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

    /**
     * The details in the snapshot, read once and then kept.
     *
     * They belong to another screen, so a snapshot write has to preserve them rather than
     * replace them with what this one loaded — but reading the file back on every write made
     * each save a read of the whole group as well.
     */
    internal var knownDetails: Map<String, ExpenseDetails>? = null

    /** What happened to a file full of expenses. */
    data class ImportOutcome(val created: Int, val failed: Int, val firstFailure: String?)

    var stats by mutableStateOf<GroupStatsResponse?>(null)
        internal set

    var isLoadingStats by mutableStateOf(false)
        internal set

    var statsFailure by mutableStateOf<String?>(null)
        internal set

    /**
     * This instance answers neither name the totals go by — neither `groups.stats.overview` nor
     * the `groups.stats.get` it replaced. Kept apart from a failure because there is nothing to
     * retry and nothing the user did wrong: the screen says so once and offers no button.
     */
    var statsUnavailable by mutableStateOf(false)
        internal set

    internal var statsParticipantId: String? = null
    internal var didRequestStats = false
    /** The figures on screen are this session's own fold, not an answer from the server. */
    internal var statsAreLocal = false
    /**
     * The last answer the server (or a cached one) gave, before any queued change was added.
     *
     * Kept apart from [stats] so a queued create is added once, and so an offline visit can
     * fall back to the whole group's total instead of a fold over whatever pages loaded.
     */
    internal var statsBase: GroupStatsResponse? = null
    internal var statsBaseParticipantId: String? = null

    val didFailToLoadStats: Boolean get() = stats == null && statsFailure != null && !statsUnavailable

    var categorySpending by mutableStateOf<List<CategorySpending>>(emptyList())
        internal set

    var isLoadingCategorySpending by mutableStateOf(false)
        internal set

    var categorySpendingFailure by mutableStateOf<String?>(null)
        internal set

    internal var didLoadCategorySpending = false

    /** Who paid what, summed over every page the sweep walked. Null until it has. */
    internal var participantSums: ParticipantSums? = null

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

    var activities by mutableStateOf<List<Activity>>(emptyList())
        internal set

    var hasMoreActivities by mutableStateOf(false)
        internal set

    var isLoadingMoreActivities by mutableStateOf(false)
        internal set

    var activitiesFailure by mutableStateOf<String?>(null)
        internal set

    internal var reconnectJob: Job? = null

    internal var didLoadActivities = false
    internal var nextActivityCursor = 0

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
     * Search keeps its own results rather than narrowing [expenses], because the search screen and
     * the expenses tab are both alive — filtering the shared list would empty the list behind the
     * search field.
     */
    var searchResults by mutableStateOf<List<ExpenseListItem>>(emptyList())
        internal set

    var hasMoreSearchResults by mutableStateOf(false)
        internal set

    var isLoadingMoreSearchResults by mutableStateOf(false)
        internal set

    var isSearching by mutableStateOf(false)
        internal set

    var searchFailure by mutableStateOf<String?>(null)
        internal set

    var filter by mutableStateOf<String?>(null)
        internal set

    internal var didLoadSearch = false
    internal var nextSearchCursor = 0

    val didFailToSearch: Boolean
        get() = searchResults.isEmpty() && searchFailure != null && !isSearching

    /** The search came back empty, which is a different sentence from "nothing typed yet". */
    val hasNoMatches: Boolean
        get() = filter != null && searchResults.isEmpty() && didLoadSearch && !isSearching

    /** Search results in the same buckets, so a result sits under the heading it would have had. */
    val searchSections: List<ExpenseSection>
        get() = sectionsOf(searchResults)

    /** An expense that has left the screen but not yet the server. */
    data class PendingDeletion(
        val expense: ExpenseListItem,
        /**
         * Where the row was, so undo puts it back where it was rather than at the top.
         *
         * Null when the row was not in the list at all — a search result is the case that
         * matters: putting it back here would add an expense to the list that the list never
         * had, under a search's name.
         */
        val index: Int?,
        /** Where the row was in the results, when the delete came from the search screen. */
        val searchIndex: Int?,
        /** Who the activity log should credit, captured when the row was swiped. */
        val actorId: String?,
    )

    var pendingDeletion by mutableStateOf<PendingDeletion?>(null)
        internal set

    internal var deletionJob: Job? = null

    internal companion object {
        const val PAGE_SIZE = 20
        const val UNDO_WINDOW = 5_000L
        const val SEARCH_DEBOUNCE = 250L

        /** How many expenses one sweep page asks for, and how many pages it will walk. */
        const val SNAPSHOT_PAGE_SIZE = 100
        const val CATEGORY_PAGE_SIZE = 100
        const val CATEGORY_PAGE_LIMIT = 20
    }
}
