package app.spliit.ui.group

// The rows a list draws, grouped by the day they belong to, and the shapes the screens
// read. Part of [GroupScreenModel].

import app.spliit.data.model.Activity
import app.spliit.data.model.ExpenseListItem
import app.spliit.data.model.Participant
import app.spliit.data.remote.TrpcServerException
import app.spliit.domain.CalendarDay
import app.spliit.domain.DateHeading
import java.io.IOException
import java.time.LocalDate

internal fun GroupScreenModel.sectionsOf(items: List<ExpenseListItem>): List<ExpenseSection> {
    val days = LinkedHashMap<LocalDate, MutableList<ExpenseListItem>>()
    for (expense in items) {
        val day = CalendarDay.dayOf(expense.expenseDate)
        days.getOrPut(day) { mutableListOf() } += expense
    }
    return days.entries
        .sortedByDescending { it.key }
        .map { ExpenseSection(it.key, it.value) }
}

/** Whether a failure means the instance could not be reached, rather than answered and refused. */
internal fun isOfflineNetworkFailure(error: Exception): Boolean =
    error is IOException && (error !is TrpcServerException || error.httpStatus >= 500)

/** One day's expenses, in the order the server sent them. */
data class ExpenseSection(
    val date: LocalDate,
    val expenses: List<ExpenseListItem>,
) {
    /** "NOV 25, 2026 WED". */
    val title: String get() = DateHeading.of(date)
}

/** One day of the activity log, newest first. */
/** The running sums behind [GroupScreenModel.participantTotals]. */
internal data class ParticipantSums(
    val paid: Map<String, Int>,
    val share: Map<String, Int>,
)

/** What one person paid into the group, and what they carry of it. */
data class ParticipantTotal(
    val participant: Participant,
    /** Where they sit in the group's list, which is what their colour comes from. */
    val position: Int,
    /** Minor units of the expenses they paid. */
    val paid: Int,
    /** Minor units of the expenses they were part of, as the balances tab charges them. */
    val share: Int,
)

data class ActivitySection(
    val date: LocalDate,
    val activities: List<Activity>,
) {
    val title: String get() = DateHeading.of(date)
}

/** How many times a group that came back into signal asks the server before giving up. */
internal const val RECONNECT_ATTEMPTS = 8

/** The pause between those attempts: long enough for a handshake, short enough to feel instant. */
internal const val RECONNECT_DELAY_MS = 4_000L
