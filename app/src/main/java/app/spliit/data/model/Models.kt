package app.spliit.data.model

import app.spliit.data.remote.ActivityTypeSerializer
import app.spliit.data.remote.InstantSerializer
import app.spliit.data.remote.LenientDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

// Money crosses the wire as an integer count of minor units: amount == 1234 is 12.34.
// This holds for expense amounts, balances and reimbursements alike.

@Serializable
data class ExpenseCategory(
    val id: Int,
    /** The heading this category sits under in the picker, e.g. "Food and Drink". */
    val grouping: String,
    val name: String,
)

@Serializable
data class Participant(
    val id: String,
    val name: String,
)

@Serializable
data class Group(
    val id: String,
    val name: String,
    val information: String? = null,
    /** A free-text symbol such as "$" or "CHF" — not an ISO code. See [currencyCode]. */
    val currency: String,
    /** ISO-4217, when the group has one. Older groups only have a symbol. */
    val currencyCode: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    val participants: List<Participant> = emptyList(),
)

@Serializable
data class ParticipantCount(val participants: Int)

/** A group as `groups.list` returns it: no participants, only how many. */
@Serializable
data class GroupSummary(
    val id: String,
    val name: String,
    val currency: String,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("_count") val count: ParticipantCount,
) {
    val participantCount: Int get() = count.participants
}

@Serializable
enum class SplitMode {
    @SerialName("EVENLY") EVENLY,
    @SerialName("BY_SHARES") BY_SHARES,
    @SerialName("BY_PERCENTAGE") BY_PERCENTAGE,
    @SerialName("BY_AMOUNT") BY_AMOUNT,
}

@Serializable
enum class RecurrenceRule {
    @SerialName("NONE") NONE,
    @SerialName("DAILY") DAILY,
    @SerialName("WEEKLY") WEEKLY,
    @SerialName("MONTHLY") MONTHLY,
    @SerialName("YEARLY") YEARLY,
}

@Serializable
data class ExpenseDocument(
    val id: String,
    val url: String,
    val width: Int,
    val height: Int,
)

/** A `Prisma.Decimal` crosses superjson as a string; be lenient in case an instance sends a number. */
@Serializable(with = LenientDecimalSerializer::class)
data class LenientDecimal(val value: BigDecimal)

@Serializable
data class DocumentCount(val documents: Int)

/** One participant's row in a split, as the server stores it. */
interface ExpenseShareRow {
    val participantId: String
    val shares: Int
}

/** An expense as it appears in a group's list. Carries only what the row needs. */
@Serializable
data class ExpenseListItem(
    val id: String,
    val title: String,
    /** Minor units. */
    val amount: Int,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    /**
     * A calendar day, sent by the server as midnight UTC. See `CalendarDay`.
     */
    @Serializable(with = InstantSerializer::class) val expenseDate: Instant,
    val isReimbursement: Boolean,
    val splitMode: SplitMode,
    val recurrenceRule: RecurrenceRule? = null,
    val category: ExpenseCategory? = null,
    val paidBy: Participant,
    val paidFor: List<PaidFor> = emptyList(),
    @SerialName("_count") val counts: DocumentCount? = null,
) {
    val documentCount: Int get() = counts?.documents ?: 0

    @Serializable
    data class PaidFor(
        val participant: Participant,
        override val shares: Int,
    ) : ExpenseShareRow {
        override val participantId: String get() = participant.id
    }
}

/** Everything needed to render and edit a single expense. */
@Serializable
data class ExpenseDetails(
    val id: String,
    val groupId: String,
    val title: String,
    /** Minor units. */
    val amount: Int,
    val categoryId: Int,
    val category: ExpenseCategory? = null,
    @Serializable(with = InstantSerializer::class) val expenseDate: Instant,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    val paidById: String,
    val paidBy: Participant,
    val paidFor: List<PaidFor> = emptyList(),
    val isReimbursement: Boolean,
    val splitMode: SplitMode,
    val notes: String? = null,
    val documents: List<ExpenseDocument> = emptyList(),
    val recurrenceRule: RecurrenceRule? = null,
    val originalAmount: Int? = null,
    val originalCurrency: String? = null,
    val conversionRate: LenientDecimal? = null,
) {
    @Serializable
    data class PaidFor(
        override val participantId: String,
        /**
         * For EVENLY, BY_SHARES and BY_PERCENTAGE this is the share value ×100.
         * For BY_AMOUNT it is a raw minor-unit amount.
         */
        override val shares: Int,
    ) : ExpenseShareRow
}

@Serializable
data class Balance(
    /** Minor units. */
    val paid: Int,
    /** Minor units. */
    val paidFor: Int,
    /** `paid - paidFor`, in minor units. Negative means this participant owes. */
    val total: Int,
)

@Serializable
data class Reimbursement(
    /** Participant ID of whoever owes. */
    val from: String,
    /** Participant ID of whoever is owed. */
    val to: String,
    /** Minor units. */
    val amount: Int,
)

// MARK: - Activity

/**
 * What a recorded activity was.
 *
 * Unknown values decode instead of throwing, which [SplitMode] deliberately does not: a split
 * mode this client cannot read is money it would divide wrongly, while an activity it cannot
 * read is one line of prose.
 */
sealed interface ActivityType {
    data object UpdateGroup : ActivityType
    data object CreateExpense : ActivityType
    data object UpdateExpense : ActivityType
    data object DeleteExpense : ActivityType
    data class Unknown(val raw: String) : ActivityType

    val isRecognised: Boolean get() = this !is Unknown
}

@Serializable
data class Activity(
    val id: String,
    val groupId: String,
    @Serializable(with = InstantSerializer::class) val time: Instant,
    @Serializable(with = ActivityTypeSerializer::class) val activityType: ActivityType,
    /** Only present when the client that did it said so. */
    val participantId: String? = null,
    val expenseId: String? = null,
    /** The expense's title **as it was** when this was recorded. */
    @SerialName("data") val title: String? = null,
    val expense: ExpenseReference? = null,
) {
    val expenseStillExists: Boolean get() = expense != null

    @Serializable
    data class ExpenseReference(val id: String)
}

// MARK: - Responses

@Serializable
data class GroupsListResponse(val groups: List<GroupSummary> = emptyList())

@Serializable
data class GroupResponse(val group: Group? = null)

@Serializable
data class GroupDetailsResponse(val group: Group, val participantsWithExpenses: List<String> = emptyList())

@Serializable
data class CreateGroupResponse(val groupId: String)

@Serializable
data class ExpensesListResponse(
    val expenses: List<ExpenseListItem> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: Int = 0,
)

@Serializable
data class ExpenseResponse(val expense: ExpenseDetails)

@Serializable
data class ExpenseIdResponse(val expenseId: String)

@Serializable
data class BalancesResponse(
    val balances: Map<String, Balance> = emptyMap(),
    val reimbursements: List<Reimbursement> = emptyList(),
)

/**
 * The only endpoint that knows what anybody actually paid.
 *
 * `totalParticipantShare` stays `Double`: an instance older than the web app's *Shares* change
 * sums floating-point thirds and sends `1416.67`, which `Int` cannot decode.
 */
@Serializable
data class GroupStatsResponse(
    val totalGroupSpendings: Int,
    val totalParticipantSpendings: Int? = null,
    val totalParticipantShare: Double? = null,
)

@Serializable
data class ActivitiesListResponse(
    val activities: List<Activity> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: Int = 0,
)

@Serializable
data class CategoriesResponse(val categories: List<ExpenseCategory> = emptyList())
