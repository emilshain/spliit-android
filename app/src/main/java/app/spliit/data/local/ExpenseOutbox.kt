package app.spliit.data.local

import android.content.Context
import app.spliit.data.model.ExpenseCategory
import app.spliit.data.model.ExpenseListItem
import app.spliit.data.model.Group
import app.spliit.data.model.Participant
import app.spliit.data.model.SplitMode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * An expense change that has not reached the server yet.
 *
 * Spliit has no offline API, so the only honest thing the app can do when a save cannot be sent
 * is to keep the change here, show it as waiting, and send it the next time the instance answers.
 * The record holds everything needed both to draw the row and to send the request later: the
 * request as the JSON the create/update procedure takes, and enough of the form to render a
 * pending row without asking the network for anything.
 *
 * *filesDir*, not *cacheDir*: this is data the person entered, and the system must not clear it
 * under storage pressure the way it may clear a snapshot of data the server still has.
 */
@Serializable
data class PendingExpense(
    /** The expense ID the request will use; also the row's key while it waits. */
    val key: String,
    val groupId: String,
    val kind: Kind,
    /** The serialized `ExpenseFormValues`, for a create or an update. */
    val valuesJson: String? = null,
    val actorId: String? = null,
    val title: String = "",
    val amount: Int = 0,
    val expenseDateEpochMillis: Long = 0L,
    val categoryId: Int = 0,
    val categoryName: String? = null,
    val categoryGrouping: String? = null,
    val paidById: String = "",
    val paidFor: List<PaidFor> = emptyList(),
    val isReimbursement: Boolean = false,
    val splitMode: SplitMode = SplitMode.EVENLY,
    val queuedAtEpochMillis: Long = 0L,
) {
    enum class Kind { CREATE, UPDATE, DELETE }

    @Serializable
    data class PaidFor(val participantId: String, val shares: Int)

    /** The row this change draws while it waits, for a create that has never been on the server. */
    fun toListItem(group: Group?): ExpenseListItem? {
        if (kind != Kind.CREATE) return null
        val payer = group?.participants?.firstOrNull { it.id == paidById }
            ?: Participant(id = paidById, name = "Someone")
        val category = categoryId.takeIf { it != 0 }?.let { id ->
            ExpenseCategory(
                id = id,
                grouping = categoryGrouping ?: "Uncategorized",
                name = categoryName ?: "General",
            )
        }
        return ExpenseListItem(
            id = key,
            title = title,
            amount = amount,
            createdAt = Instant.ofEpochMilli(queuedAtEpochMillis),
            expenseDate = Instant.ofEpochMilli(expenseDateEpochMillis),
            isReimbursement = isReimbursement,
            splitMode = splitMode,
            category = category,
            paidBy = payer,
            paidFor = paidFor.map { share ->
                ExpenseListItem.PaidFor(
                    participant = group?.participants?.firstOrNull {
                        it.id == share.participantId
                    } ?: Participant(id = share.participantId, name = "Someone"),
                    shares = share.shares,
                )
            },
        )
    }
}

/**
 * Every expense change still waiting to be sent, across every group.
 *
 * One JSON file in `filesDir`, rewritten on every change. The list is short — a handful of edits
 * made while a phone was out of signal — so reading and writing all of it costs nothing, and a
 * single file cannot half-apply the way many small ones could.
 */
class ExpenseOutbox(context: Context) {

    private val file = File(context.applicationContext.filesDir, "expense-outbox.json")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val lock = Any()

    fun all(): List<PendingExpense> = synchronized(lock) {
        runCatching {
            if (!file.exists()) return emptyList()
            json.decodeFromString(serializer, file.readText())
        }.getOrDefault(emptyList())
    }

    fun forGroup(groupId: String): List<PendingExpense> =
        all().filter { it.groupId == groupId }

    /** Adds or replaces the change for one expense: the newest version of it is the one to send. */
    fun put(pending: PendingExpense) = synchronized(lock) {
        val rows = all().toMutableList()
        rows.removeAll { it.groupId == pending.groupId && it.key == pending.key }
        rows += pending
        write(rows)
    }

    fun remove(pending: PendingExpense) = synchronized(lock) {
        write(all().filterNot { it.groupId == pending.groupId && it.key == pending.key })
    }

    fun forget(groupId: String) = synchronized(lock) {
        write(all().filterNot { it.groupId == groupId })
    }

    private fun write(rows: List<PendingExpense>) {
        runCatching {
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(json.encodeToString(serializer, rows))
            if (!temporary.renameTo(file)) {
                file.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }

    private companion object {
        /** The file is always the same shape, whatever Kotlin infers from the call site. */
        val serializer: KSerializer<List<PendingExpense>> =
            ListSerializer(PendingExpense.serializer())
    }
}
