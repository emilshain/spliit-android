package app.spliit.data.remote

import app.spliit.data.model.ExpenseDocument
import app.spliit.data.model.RecurrenceRule
import app.spliit.data.model.SplitMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.time.Instant

@Serializable
data class GroupsListInput(val groupIds: List<String>)

@Serializable
data class GroupIdInput(val groupId: String)

@Serializable
data class GroupFormValues(
    val name: String,
    val information: String? = null,
    val currency: String,
    val currencyCode: String? = null,
    val participants: List<ParticipantInput> = emptyList(),
) {
    @Serializable
    data class ParticipantInput(
        /** Omitted for participants being added. */
        val id: String? = null,
        val name: String,
    )
}

@Serializable
data class CreateGroupInput(val groupFormValues: GroupFormValues)

@Serializable
data class UpdateGroupInput(
    val groupId: String,
    val groupFormValues: GroupFormValues,
    val participantId: String? = null,
)

/** `groups.delete` schedules the group for deletion; the name is what the notice says. */
@Serializable
data class DeleteGroupInput(
    val groupId: String,
    val groupName: String,
    /** Who asked, for the activity log; omitted when this phone has not said. */
    val participantId: String? = null,
)

@Serializable
data class ExpensesListInput(
    val groupId: String,
    val cursor: Int? = null,
    val limit: Int? = null,
    /** Case-insensitive substring match on the expense title. */
    val filter: String? = null,
)

@Serializable
data class ExpenseIdInput(val groupId: String, val expenseId: String)

@Serializable
data class CreateExpenseInput(
    val groupId: String,
    val expenseFormValues: JsonElement,
    val participantId: String? = null,
    /** Omitted when null, and then the server mints one. */
    val expenseId: String? = null,
)

@Serializable
data class UpdateExpenseInput(
    val groupId: String,
    val expenseId: String,
    val expenseFormValues: JsonElement,
    val participantId: String? = null,
)

@Serializable
data class DeleteExpenseInput(
    val groupId: String,
    val expenseId: String,
    val participantId: String? = null,
)

@Serializable
data class GroupStatsInput(
    val groupId: String,
    /** Whose spending and share to answer for. Left out, the server answers neither. */
    val participantId: String? = null,
)

@Serializable
data class ActivitiesListInput(val groupId: String, val cursor: Int? = null, val limit: Int? = null)

@Serializable
data class NoInput(val unused: Boolean = false)

/**
 * The expense payload, encoded exactly as the iOS app encodes it.
 *
 * `originalCurrency` is always written, as JSON null when the expense is in the group's own
 * currency: a missing key reaches the server as `undefined` and tells Prisma to leave the
 * column alone, so an expense moved back to the group's currency would keep claiming to have
 * been paid in another. `originalAmount` and `conversionRate` are omitted instead, because the
 * server's schema rejects null for them.
 */
data class ExpenseFormValues(
    val title: String,
    val expenseDate: Instant,
    /** Minor units. */
    val amount: Int,
    /** ExpenseCategory ID; 0 is "General". */
    val category: Int = 0,
    val paidBy: String,
    val paidFor: List<PaidFor>,
    val splitMode: SplitMode = SplitMode.EVENLY,
    val saveDefaultSplittingOptions: Boolean = false,
    val isReimbursement: Boolean = false,
    val documents: List<ExpenseDocument> = emptyList(),
    val notes: String? = null,
    val recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
    val originalAmount: Int? = null,
    val originalCurrency: String? = null,
    val conversionRate: BigDecimal? = null,
) {
    @Serializable
    data class PaidFor(val participant: String, val shares: Int)

    @Serializable
    private data class Wire(
        val title: String,
        @Serializable(with = InstantSerializer::class) val expenseDate: Instant,
        val amount: Int,
        val category: Int,
        val paidBy: String,
        val paidFor: List<PaidFor>,
        val splitMode: SplitMode,
        val saveDefaultSplittingOptions: Boolean,
        val isReimbursement: Boolean,
        val documents: List<ExpenseDocument>,
        val notes: String?,
        val recurrenceRule: RecurrenceRule,
        val originalAmount: Int?,
        val originalCurrency: String?,
        val conversionRate: String?,
    )

    fun toJson(): JsonObject {
        val encoded = SuperJson.json.encodeToJsonElement(
            Wire.serializer(),
            Wire(
                title = title,
                expenseDate = expenseDate,
                amount = amount,
                category = category,
                paidBy = paidBy,
                paidFor = paidFor,
                splitMode = splitMode,
                saveDefaultSplittingOptions = saveDefaultSplittingOptions,
                isReimbursement = isReimbursement,
                documents = documents,
                notes = notes,
                recurrenceRule = recurrenceRule,
                originalAmount = originalAmount,
                originalCurrency = originalCurrency,
                // A numeric string: the server's schema accepts a number, a numeric string or
                // '', and a string cannot lose precision on the way.
                conversionRate = conversionRate?.toPlainString(),
            ),
        )
        return if (encoded is JsonObject && "originalCurrency" !in encoded) {
            buildJsonObject {
                encoded.forEach { (key, value) -> put(key, value) }
                put("originalCurrency", JsonNull)
            }
        } else {
            encoded as JsonObject
        }
    }
}

