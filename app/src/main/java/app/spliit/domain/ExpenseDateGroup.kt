package app.spliit.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The buckets the expense list is divided into, newest first.
 *
 * This reproduces the rules the React Native app used, so an upgrading user sees their expenses
 * sectioned exactly as before. Declaration order is display order.
 */
enum class ExpenseDateGroup {
    UPCOMING,
    THIS_WEEK,
    EARLIER_THIS_MONTH,
    LAST_MONTH,
    EARLIER_THIS_YEAR,
    LAST_YEAR,
    OLDER,
    ;

    /** Section heading, matching the wording of the React Native app. */
    val title: String
        get() = when (this) {
            UPCOMING -> "Upcoming"
            THIS_WEEK -> "This week"
            EARLIER_THIS_MONTH -> "Earlier this month"
            LAST_MONTH -> "Last month"
            EARLIER_THIS_YEAR -> "Earlier this year"
            LAST_YEAR -> "Last year"
            OLDER -> "Older"
        }

    companion object {

        /** Which bucket [date] falls into, as of [today]. */
        fun containing(
            date: LocalDate,
            today: LocalDate = LocalDate.now(),
            weekFields: WeekFields = WeekFields.of(Locale.getDefault()),
        ): ExpenseDateGroup {
            if (today < date) return UPCOMING

            val sameWeek = date.get(weekFields.weekOfWeekBasedYear()) ==
                today.get(weekFields.weekOfWeekBasedYear()) &&
                date.get(weekFields.weekBasedYear()) == today.get(weekFields.weekBasedYear())
            if (sameWeek) return THIS_WEEK

            if (YearMonth.from(date) == YearMonth.from(today)) return EARLIER_THIS_MONTH
            if (YearMonth.from(date) == YearMonth.from(today).minusMonths(1)) return LAST_MONTH

            if (date.year == today.year) return EARLIER_THIS_YEAR
            if (date.year == today.year - 1) return LAST_YEAR

            return OLDER
        }
    }
}
