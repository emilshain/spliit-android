package app.spliit.data.local

import android.content.Context
import app.spliit.data.model.Activity
import app.spliit.data.model.Balance
import app.spliit.data.model.ExpenseCategory
import app.spliit.data.model.ExpenseListItem
import app.spliit.data.model.Group
import app.spliit.data.model.GroupSummary
import app.spliit.data.model.Reimbursement
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

    /** The snapshot for [groupId], or null when it has never loaded or the file is unusable. */
    fun load(groupId: String): CachedGroup? = runCatching {
        val file = fileFor(groupId)
        if (!file.exists()) return null
        json.decodeFromString(CachedGroup.serializer(), file.readText())
    }.getOrNull()

    fun save(snapshot: CachedGroup) {
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

    fun forget(groupId: String) {
        runCatching { fileFor(groupId).delete() }
    }

    /** What the group list knew the last time an instance answered. */
    fun loadSummaries(): Map<String, GroupSummary> = runCatching {
        if (!summariesFile.exists()) return emptyMap()
        json.decodeFromString(summariesSerializer, summariesFile.readText())
    }.getOrDefault(emptyMap())

    fun saveSummaries(summaries: Map<String, GroupSummary>) {
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
)
