package app.spliit.ui.group

// What is done to the group rather than inside it: renaming it, moving its expenses in and
// out, and handing it on. Part of [GroupScreenModel].

import app.spliit.data.local.RecentGroup
import app.spliit.data.model.ExpenseListItem
import app.spliit.data.model.SplitMode
import app.spliit.data.remote.ExpenseFormValues
import app.spliit.data.remote.GroupFormValues
import app.spliit.data.remote.SpliitApi
import app.spliit.domain.CalendarDay
import app.spliit.domain.Failures
import app.spliit.domain.GroupExport
import app.spliit.domain.SplitwiseCsv
import kotlinx.coroutines.CancellationException

suspend fun GroupScreenModel.updateGroup(values: GroupFormValues) {
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
suspend fun GroupScreenModel.exportCsv(): String =
    container.clientForGroup(groupId).text(GroupExport.csvPath(groupId))

/**
 * The same expenses as a Splitwise file.
 *
 * Built here rather than downloaded: the instance's own export is Spliit's shape, and a file
 * another app can open has to be this one. It walks every page, which is the same sweep the
 * category breakdown does, and folds each one into a line.
 */
suspend fun GroupScreenModel.exportSplitwiseCsv(): String {
    val current = client ?: error("This group is not ready yet.")
    val currentGroup = group ?: error("This group is not ready yet.")
    val all = mutableListOf<ExpenseListItem>()
    var cursor = 0
    while (true) {
        val page = current.call(
            SpliitApi.expenses(groupId, cursor = cursor, limit = GroupScreenModel.SNAPSHOT_PAGE_SIZE),
        )
        all += page.expenses
        if (!page.hasMore) break
        cursor = page.nextCursor
    }
    return SplitwiseCsv.write(currentGroup, all)
}

/**
 * Creates one expense per row, in file order.
 *
 * Sequential on purpose: an import is a handful of writes to somebody's shared ledger, and
 * firing them all at once makes a partial failure impossible to explain. Each row is resolved
 * against this group - the columns name people, and a name the group has is the participant
 * it means.
 */
suspend fun GroupScreenModel.importExpenses(
    rows: List<SplitwiseCsv.Row>,
    onProgress: (Int) -> Unit = {},
): GroupScreenModel.ImportOutcome {
    val current = client ?: return GroupScreenModel.ImportOutcome(0, rows.size, "This group is not ready yet.")
    val currentGroup = group ?: return GroupScreenModel.ImportOutcome(0, rows.size, "This group is not ready yet.")
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
            if (firstFailure == null) firstFailure = Failures.message(error)
        }
        onProgress(index + 1)
    }

    if (created > 0) reloadAfterExpenseChangeNow()
    return GroupScreenModel.ImportOutcome(created, failed, firstFailure)
}
