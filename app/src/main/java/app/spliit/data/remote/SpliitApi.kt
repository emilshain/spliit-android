package app.spliit.data.remote

import app.spliit.data.model.ActivitiesListResponse
import app.spliit.data.model.BalancesResponse
import app.spliit.data.model.CategoriesResponse
import app.spliit.data.model.CreateGroupResponse
import app.spliit.data.model.ExpenseDetails
import app.spliit.data.model.ExpenseIdResponse
import app.spliit.data.model.ExpenseResponse
import app.spliit.data.model.ExpensesListResponse
import app.spliit.data.model.GroupDetailsResponse
import app.spliit.data.model.GroupResponse
import app.spliit.data.model.GroupStatsResponse
import app.spliit.data.model.GroupsListResponse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The Spliit router, as procedures the client can call.
 *
 * ```
 * val response = client.call(SpliitApi.group(id = "abc"))
 * ```
 */
object SpliitApi {

    // MARK: - Groups

    /** Fetches the groups behind a set of IDs. Unknown IDs are silently absent. */
    fun groups(ids: List<String>): TrpcProcedure<GroupsListResponse> =
        query("groups.list", GroupsListInput(ids), GroupsListInput.serializer(), GroupsListResponse.serializer())

    fun group(id: String): TrpcProcedure<GroupResponse> =
        query("groups.get", GroupIdInput(id), GroupIdInput.serializer(), GroupResponse.serializer())

    /**
     * The group plus the participants who appear on at least one expense, and so can't be
     * removed.
     */
    fun groupDetails(id: String): TrpcProcedure<GroupDetailsResponse> =
        query(
            "groups.getDetails",
            GroupIdInput(id),
            GroupIdInput.serializer(),
            GroupDetailsResponse.serializer(),
        )

    fun createGroup(values: GroupFormValues): TrpcProcedure<CreateGroupResponse> =
        mutation(
            "groups.create",
            CreateGroupInput(values),
            CreateGroupInput.serializer(),
            CreateGroupResponse.serializer(),
        )

    fun updateGroup(
        id: String,
        values: GroupFormValues,
        byParticipantId: String? = null,
    ): TrpcProcedure<Unit> =
        mutation(
            "groups.update",
            UpdateGroupInput(id, values, byParticipantId),
            UpdateGroupInput.serializer(),
            TrpcVoidSerializer,
        )

    /**
     * Schedules the group for deletion. The server keeps it for a while first, which is what
     * makes the web app's delete a thing you can change your mind about.
     */
    fun deleteGroup(input: DeleteGroupInput): TrpcProcedure<Unit> =
        mutation(
            "groups.delete",
            input,
            DeleteGroupInput.serializer(),
            TrpcVoidSerializer,
        )

    // MARK: - Expenses

    fun expenses(
        groupId: String,
        cursor: Int = 0,
        limit: Int = 20,
        filter: String? = null,
    ): TrpcProcedure<ExpensesListResponse> =
        query(
            "groups.expenses.list",
            ExpensesListInput(groupId = groupId, cursor = cursor, limit = limit, filter = filter),
            ExpensesListInput.serializer(),
            ExpensesListResponse.serializer(),
        )

    fun expense(groupId: String, expenseId: String): TrpcProcedure<ExpenseResponse> =
        query(
            "groups.expenses.get",
            ExpenseIdInput(groupId, expenseId),
            ExpenseIdInput.serializer(),
            ExpenseResponse.serializer(),
        )

    fun createExpense(
        groupId: String,
        values: ExpenseFormValues,
        byParticipantId: String? = null,
        expenseId: String? = null,
    ): TrpcProcedure<ExpenseIdResponse> =
        mutation(
            "groups.expenses.create",
            CreateExpenseInput(groupId, values.toJson(), byParticipantId, expenseId),
            CreateExpenseInput.serializer(),
            ExpenseIdResponse.serializer(),
        )

    /**
     * The same create, from a request an earlier session encoded and kept in the outbox: the
     * JSON is sent as it was written, so a waiting expense is created exactly as it was typed.
     */
    fun createExpenseRaw(
        groupId: String,
        values: JsonElement,
        byParticipantId: String? = null,
        expenseId: String? = null,
    ): TrpcProcedure<ExpenseIdResponse> =
        mutation(
            "groups.expenses.create",
            CreateExpenseInput(groupId, values, byParticipantId, expenseId),
            CreateExpenseInput.serializer(),
            ExpenseIdResponse.serializer(),
        )

    fun updateExpenseRaw(
        groupId: String,
        expenseId: String,
        values: JsonElement,
        byParticipantId: String? = null,
    ): TrpcProcedure<ExpenseIdResponse> =
        mutation(
            "groups.expenses.update",
            UpdateExpenseInput(groupId, expenseId, values, byParticipantId),
            UpdateExpenseInput.serializer(),
            ExpenseIdResponse.serializer(),
        )

    fun deleteExpenseRaw(
        groupId: String,
        expenseId: String,
        byParticipantId: String? = null,
    ): TrpcProcedure<Unit> =
        mutation(
            "groups.expenses.delete",
            DeleteExpenseInput(groupId, expenseId, byParticipantId),
            DeleteExpenseInput.serializer(),
            TrpcVoidSerializer,
        )

    fun updateExpense(
        groupId: String,
        expenseId: String,
        values: ExpenseFormValues,
        byParticipantId: String? = null,
    ): TrpcProcedure<ExpenseIdResponse> =
        mutation(
            "groups.expenses.update",
            UpdateExpenseInput(groupId, expenseId, values.toJson(), byParticipantId),
            UpdateExpenseInput.serializer(),
            ExpenseIdResponse.serializer(),
        )

    fun deleteExpense(
        groupId: String,
        expenseId: String,
        byParticipantId: String? = null,
    ): TrpcProcedure<Unit> =
        mutation(
            "groups.expenses.delete",
            DeleteExpenseInput(groupId, expenseId, byParticipantId),
            DeleteExpenseInput.serializer(),
            TrpcVoidSerializer,
        )

    // MARK: - Balances

    fun balances(groupId: String): TrpcProcedure<BalancesResponse> =
        query(
            "groups.balances.list",
            GroupIdInput(groupId),
            GroupIdInput.serializer(),
            BalancesResponse.serializer(),
        )

    // MARK: - Stats

    /**
     * The totals from an instance that has folded the whole stats page into one loader.
     * `spliit.app` serves this; the old `groups.stats.get` was removed rather than kept beside
     * it, so this is asked first and [groupStats] falls back.
     */
    fun statsOverview(groupId: String, participantId: String? = null): TrpcProcedure<GroupStatsResponse> =
        query(
            "groups.stats.overview",
            GroupStatsInput(groupId, participantId),
            GroupStatsInput.serializer(),
            GroupStatsResponse.serializer(),
        )

    /** The same totals from an instance that predates `groups.stats.overview`. */
    fun stats(groupId: String, participantId: String? = null): TrpcProcedure<GroupStatsResponse> =
        query(
            "groups.stats.get",
            GroupStatsInput(groupId, participantId),
            GroupStatsInput.serializer(),
            GroupStatsResponse.serializer(),
        )

    // MARK: - Activity

    fun activities(
        groupId: String,
        cursor: Int = 0,
        limit: Int = 20,
    ): TrpcProcedure<ActivitiesListResponse> =
        query(
            "groups.activities.list",
            ActivitiesListInput(groupId, cursor, limit),
            ActivitiesListInput.serializer(),
            ActivitiesListResponse.serializer(),
        )

    // MARK: - Categories

    fun categories(): TrpcProcedure<CategoriesResponse> =
        TrpcProcedure(
            path = "categories.list",
            kind = TrpcProcedure.Kind.QUERY,
            input = null,
            deserializer = CategoriesResponse.serializer(),
        )

    // MARK: - Helpers

    private fun <I, O> query(
        path: String,
        input: I,
        inputSerializer: SerializationStrategy<I>,
        output: KSerializer<O>,
    ): TrpcProcedure<O> = TrpcProcedure(
        path = path,
        kind = TrpcProcedure.Kind.QUERY,
        // The server reads a superjson envelope, not a bare payload — the same wrapping the
        // iOS client does in `TRPCClient.makeRequest`.
        input = SuperJson.envelope(SuperJson.json.encodeToJsonElement(inputSerializer, input)),
        deserializer = output,
    )

    private fun <I, O> mutation(
        path: String,
        input: I,
        inputSerializer: SerializationStrategy<I>,
        output: KSerializer<O>,
    ): TrpcProcedure<O> = TrpcProcedure(
        path = path,
        kind = TrpcProcedure.Kind.MUTATION,
        input = SuperJson.envelope(SuperJson.json.encodeToJsonElement(inputSerializer, input)),
        deserializer = output,
    )
}

/**
 * The group's totals, from whichever of the two stats procedures this instance answers.
 *
 * `groups.stats.overview` first, because the instance most people are on no longer has the
 * other one. An instance that predates the change answers `NOT_FOUND`, which is the signal to
 * ask the old name — and one that answers neither throws that second `NOT_FOUND` on to the
 * caller, which is how the totals tab knows to say the server has none.
 */
suspend fun TrpcClient.groupStats(
    groupId: String,
    participantId: String? = null,
): GroupStatsResponse =
    try {
        call(SpliitApi.statsOverview(groupId, participantId))
    } catch (error: TrpcServerException) {
        if (!error.isUnknownProcedure) throw error
        call(SpliitApi.stats(groupId, participantId))
    }

