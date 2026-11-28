package app.spliit.data.local

import android.content.Context
import app.spliit.data.model.Activity
import app.spliit.data.model.Balance
import app.spliit.data.model.ExpenseCategory
import app.spliit.data.model.ExpenseDetails
import app.spliit.data.model.ExpenseListItem
import app.spliit.data.model.GroupStatsResponse
import app.spliit.data.model.Group
import app.spliit.data.model.GroupSummary
import app.spliit.data.model.Reimbursement
import app.spliit.domain.CategorySpending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The last good read of a group, kept on disk so its page still opens without a network.
 *
 * One JSON file per group in the cache directory, overwritten by every successful load. It is a
 * cache and not a database: the server is still the truth, the file is only what the app can
 * honestly say while the server cannot be reached, and the screen says so when it is showing it.
 *
 * `cacheDir` rather than `filesDir` because the system may clear it under storage pressure — the
 * cost of that is a group that needs the network again, never data the user typed.
 *
 * The home list's own reads — each group's date and participant count — live here too, in one
 * file. They are what lets the list keep saying "3 participants" with no signal instead of
 * replacing the line with an apology.
 */
class GroupCache(context: Context) {

    private val directory = File(context.cacheDir, "groups")
    private val summariesFile = File(context.cacheDir, "group-summaries.json")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowStructuredMapKeys = false
    }

    private val summariesSerializer =
        MapSerializer(String.serializer(), GroupSummary.serializer())

    /**
     * The snapshot for [groupId], or null when it has never loaded or the file is unusable.
     *
     * Every call reads and parses a file of the whole group, so it is dispatched to the IO pool:
     * on a large group a main-thread read is a visible stall on the frame it happens in.
     */
    suspend fun load(groupId: String): CachedGroup? = withContext(Dispatchers.IO) {
        runCatching {
            val file = fileFor(groupId)
            if (!file.exists()) null
            else json.decodeFromString(CachedGroup.serializer(), file.readText())
        }.getOrNull()
    }

    suspend fun save(snapshot: CachedGroup) = withContext(Dispatchers.IO) {
        runCatching {
            directory.mkdirs()
            val file = fileFor(snapshot.group.id)
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(json.encodeToString(CachedGroup.serializer(), snapshot))
            // Rename rather than write in place: a half-written snapshot is worse than an old one.
            if (!temporary.renameTo(file)) {
                file.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }

    /**
     * Remembers one expense's full detail, beside the snapshot.
     *
     * The list rows carry only what a row needs, so an expense opened with no network had
     * nothing to be drawn from. A detail that has been read once is kept here — read back out of
     * the snapshot rather than held in memory, so the file stays the one copy.
     */
    suspend fun saveDetail(groupId: String, detail: ExpenseDetails) {
        val snapshot = load(groupId) ?: return
        save(snapshot.copy(details = snapshot.details + (detail.id to detail)))
    }

    suspend fun forget(groupId: String) = withContext(Dispatchers.IO) {
        runCatching { fileFor(groupId).delete() }
    }

    /** What the group list knew the last time an instance answered. */
    suspend fun loadSummaries(): Map<String, GroupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            if (!summariesFile.exists()) emptyMap()
            else json.decodeFromString(summariesSerializer, summariesFile.readText())
        }.getOrDefault(emptyMap())
    }

    suspend fun saveSummaries(summaries: Map<String, GroupSummary>) =
        withContext(Dispatchers.IO) {
            runCatching {
                summariesFile.parentFile?.mkdirs()
                summariesFile.writeText(json.encodeToString(summariesSerializer, summaries))
            }
        }

    private fun fileFor(groupId: String): File =
        File(directory, groupId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")
}

/** Everything a group's screen needs, as it stood when it was last loaded. */
@Serializable
data class CachedGroup(
    val group: Group,
    val expenses: List<ExpenseListItem> = emptyList(),
    val balances: Map<String, Balance> = emptyMap(),
    val reimbursements: List<Reimbursement> = emptyList(),
    val categories: List<ExpenseCategory> = emptyList(),
    /** When the server answered, so the screen can say how old what it shows is. */
    val savedAtEpochMillis: Long = 0L,
    /** A page was in flight beyond the first when this was taken. */
    val hasMoreExpenses: Boolean = false,
    val nextCursor: Int = 0,
    /** The log as last read, so the activity tab is not blank without a network. */
    val activities: List<Activity> = emptyList(),
    /** Totals as last answered, so the totals tab opens with figures rather than a skeleton. */
    val stats: GroupStatsResponse? = null,
    /** Who those totals were answered for; another person's figures are not shown under a name. */
    val statsParticipantId: String? = null,
    /** The category breakdown as last folded. */
    val categorySpending: List<CategorySpending> = emptyList(),
    /** Expenses whose detail has been opened, so their pages open without a network too. */
    val details: Map<String, ExpenseDetails> = emptyMap(),
)
