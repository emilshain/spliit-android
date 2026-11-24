package app.spliit.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The buckets the activity log is divided into, newest first.
 *
 * Finer than [ExpenseDateGroup], and deliberately so. An expense is dated by the day it happened
 * and a group collects a handful a week, so "This week" is a useful heading; an activity is
 * stamped to the second and most of a log is from the last day or two, where the same heading
 * would be the whole screen. These are the web app's buckets, so both products cut the same log in
 * the same places.
 */
enum class ActivityDateGroup {
    TODAY,
    YESTERDAY,
    EARLIER_THIS_WEEK,
    LAST_WEEK,
    EARLIER_THIS_MONTH,
    LAST_MONTH,
    EARLIER_THIS_YEAR,
    LAST_YEAR,
    OLDER,
    ;

    /** Section heading. */
    val title: String
        get() = when (this) {
            TODAY -> "Today"
            YESTERDAY -> "Yesterday"
            EARLIER_THIS_WEEK -> "Earlier this week"
            LAST_WEEK -> "Last week"
            EARLIER_THIS_MONTH -> "Earlier this month"
            LAST_MONTH -> "Last month"
            EARLIER_THIS_YEAR -> "Earlier this year"
            LAST_YEAR -> "Last year"
            OLDER -> "Older"
        }

    /**
     * Whether a row in this bucket has to spell its date out. "Today" and "Yesterday" have
     * already said which day it was; every other heading names a span, so the time alone would
     * not place the row within it.
     */
    val needsDate: Boolean get() = this != TODAY && this != YESTERDAY

    companion object {

        /** Which bucket [date] falls into, as of [today]. */
        fun containing(
            date: Instant,
            today: Instant = Instant.now(),
            zone: ZoneId = ZoneId.systemDefault(),
            weekFields: WeekFields = WeekFields.of(Locale.getDefault()),
        ): ActivityDateGroup {
            val day = date.atZone(zone).toLocalDate()
            val todayDay = today.atZone(zone).toLocalDate()

            // Nothing records an activity in the future, but a phone whose clock disagrees with
            // the server's can still read one — and it belongs at the top of the log rather than
            // in whichever bucket the arithmetic below happens to reach first.
            if (!day.isBefore(todayDay)) return TODAY
            if (day == todayDay.minusDays(1)) return YESTERDAY

            if (day.get(weekFields.weekBasedYear()) == todayDay.get(weekFields.weekBasedYear()) &&
                day.get(weekFields.weekOfWeekBasedYear()) ==
                todayDay.get(weekFields.weekOfWeekBasedYear())
            ) {
                return EARLIER_THIS_WEEK
            }

            val lastWeekDay = todayDay.minusWeeks(1)
            if (day.get(weekFields.weekBasedYear()) ==
                lastWeekDay.get(weekFields.weekBasedYear()) &&
                day.get(weekFields.weekOfWeekBasedYear()) ==
                lastWeekDay.get(weekFields.weekOfWeekBasedYear())
            ) {
                return LAST_WEEK
            }

            if (YearMonth.from(day) == YearMonth.from(todayDay)) return EARLIER_THIS_MONTH
            if (YearMonth.from(day) == YearMonth.from(todayDay).minusMonths(1)) return LAST_MONTH

            if (day.year == todayDay.year) return EARLIER_THIS_YEAR
            if (day.year == todayDay.year - 1) return LAST_YEAR

            return OLDER
        }

        /** Convenience for callers that already have a calendar day. */
        fun containing(
            day: LocalDate,
            today: LocalDate = LocalDate.now(),
            weekFields: WeekFields = WeekFields.of(Locale.getDefault()),
        ): ActivityDateGroup = containing(
            date = day.atStartOfDay(ZoneId.systemDefault()).toInstant(),
            today = today.atStartOfDay(ZoneId.systemDefault()).toInstant(),
            zone = ZoneId.systemDefault(),
            weekFields = weekFields,
        )
    }
}
