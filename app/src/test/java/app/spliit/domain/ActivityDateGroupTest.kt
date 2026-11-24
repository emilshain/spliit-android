package app.spliit.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * The log's own buckets, which are finer than the expense list's — a log is mostly the last day or
 * two, where "This week" would be the whole screen. A fixed "today" keeps these from drifting with
 * the wall clock.
 */
class ActivityDateGroupTest {

    /** Thursday 13 August 2026. Weeks below run Sunday to Saturday, so "this week" is 9–15. */
    private val today = LocalDate.of(2026, 8, 13)

    private val weekFields = WeekFields.of(DayOfWeek.SUNDAY, 1)

    private fun bucket(daysFromToday: Int): ActivityDateGroup =
        ActivityDateGroup.containing(today.plusDays(daysFromToday.toLong()), today, weekFields)

    private fun bucketMonths(monthsFromToday: Long): ActivityDateGroup =
        ActivityDateGroup.containing(today.plusMonths(monthsFromToday), today, weekFields)

    @Test
    fun `any day that is today is today`() {
        assertEquals(ActivityDateGroup.TODAY, bucket(0))
    }

    /**
     * Nothing records an activity in the future, but a phone whose clock runs fast reads one, and
     * it belongs at the top of the log rather than wherever the arithmetic lands it.
     */
    @Test
    fun `an activity stamped in the future is shown as today`() {
        assertEquals(ActivityDateGroup.TODAY, bucket(1))
        assertEquals(ActivityDateGroup.TODAY, bucket(3))
    }

    @Test
    fun `the previous calendar day is yesterday`() {
        assertEquals(ActivityDateGroup.YESTERDAY, bucket(-1))
    }

    @Test
    fun `two days back, in the same week, is earlier this week`() {
        // Tuesday the 11th and Monday the 10th, both after the Sunday this week began on.
        assertEquals(ActivityDateGroup.EARLIER_THIS_WEEK, bucket(-2))
        assertEquals(ActivityDateGroup.EARLIER_THIS_WEEK, bucket(-3))
    }

    @Test
    fun `the previous calendar week is last week`() {
        // Saturday the 8th closed the previous week; Sunday the 2nd opened it.
        assertEquals(ActivityDateGroup.LAST_WEEK, bucket(-5))
        assertEquals(ActivityDateGroup.LAST_WEEK, bucket(-11))
    }

    @Test
    fun `earlier in the same month, but two weeks back, is earlier this month`() {
        // Saturday 1 August: two weeks back, and still August.
        assertEquals(ActivityDateGroup.EARLIER_THIS_MONTH, bucket(-12))
    }

    @Test
    fun `the previous calendar month is last month`() {
        assertEquals(ActivityDateGroup.LAST_MONTH, bucketMonths(-1))
    }

    @Test
    fun `earlier in the same year is earlier this year`() {
        assertEquals(ActivityDateGroup.EARLIER_THIS_YEAR, bucketMonths(-3))
        assertEquals(ActivityDateGroup.EARLIER_THIS_YEAR, bucketMonths(-7))
    }

    @Test
    fun `the previous calendar year is last year`() {
        assertEquals(ActivityDateGroup.LAST_YEAR, bucketMonths(-12))
        assertEquals(ActivityDateGroup.LAST_YEAR, bucketMonths(-19))
    }

    @Test
    fun `anything older falls into the last bucket`() {
        assertEquals(ActivityDateGroup.OLDER, bucketMonths(-25))
        assertEquals(ActivityDateGroup.OLDER, bucketMonths(-60))
    }

    @Test
    fun `buckets sort newest first`() {
        val shuffled = listOf(
            ActivityDateGroup.OLDER,
            ActivityDateGroup.LAST_WEEK,
            ActivityDateGroup.TODAY,
            ActivityDateGroup.LAST_YEAR,
            ActivityDateGroup.YESTERDAY,
        )

        assertEquals(
            listOf(
                ActivityDateGroup.TODAY,
                ActivityDateGroup.YESTERDAY,
                ActivityDateGroup.LAST_WEEK,
                ActivityDateGroup.LAST_YEAR,
                ActivityDateGroup.OLDER,
            ),
            shuffled.sorted(),
        )
    }

    /**
     * The heading says which day it was for the first two buckets and names a span for the rest,
     * so only the rest need the date spelled out on every row.
     */
    @Test
    fun `only the buckets that name a span make their rows carry a date`() {
        assertEquals(false, ActivityDateGroup.TODAY.needsDate)
        assertEquals(false, ActivityDateGroup.YESTERDAY.needsDate)
        val spans = ActivityDateGroup.entries.filter {
            it != ActivityDateGroup.TODAY && it != ActivityDateGroup.YESTERDAY
        }
        assertEquals(true, spans.all { it.needsDate })
    }
}
