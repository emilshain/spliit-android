package app.spliit.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * Buckets must land exactly where the React Native app put them, so the list looks unchanged to
 * someone who just updated. A fixed "today" keeps these from drifting with the wall clock.
 *
 * The Swift suite pins today at Wednesday 12 August 2026, midday UTC, with weeks starting on
 * Sunday — the same calendar this uses.
 */
class ExpenseDateGroupTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 12)

    private val calendar: WeekFields = WeekFields.of(DayOfWeek.SUNDAY, 1)

    private fun bucketDays(days: Long): ExpenseDateGroup =
        ExpenseDateGroup.containing(today.plusDays(days), today, calendar)

    private fun bucketMonths(months: Long): ExpenseDateGroup =
        ExpenseDateGroup.containing(today.plusMonths(months), today, calendar)

    @Test
    fun `a future expense is upcoming`() {
        assertEquals(ExpenseDateGroup.UPCOMING, bucketDays(1))
        assertEquals(ExpenseDateGroup.UPCOMING, bucketDays(30))
    }

    @Test
    fun `earlier today and earlier this week land in this week`() {
        assertEquals(ExpenseDateGroup.THIS_WEEK, bucketDays(-1))
        assertEquals(ExpenseDateGroup.THIS_WEEK, bucketDays(-2))
    }

    @Test
    fun `earlier in the same month, but a previous week, is earlier this month`() {
        // 12 August 2026 is a Wednesday; 3 August is the previous week but the same month.
        assertEquals(ExpenseDateGroup.EARLIER_THIS_MONTH, bucketDays(-9))
    }

    @Test
    fun `the previous calendar month is last month`() {
        assertEquals(ExpenseDateGroup.LAST_MONTH, bucketMonths(-1))
    }

    @Test
    fun `earlier in the same year is earlier this year`() {
        assertEquals(ExpenseDateGroup.EARLIER_THIS_YEAR, bucketMonths(-3))
        assertEquals(ExpenseDateGroup.EARLIER_THIS_YEAR, bucketMonths(-7))
    }

    @Test
    fun `the previous calendar year is last year`() {
        assertEquals(ExpenseDateGroup.LAST_YEAR, bucketMonths(-12))
        assertEquals(ExpenseDateGroup.LAST_YEAR, bucketMonths(-19))
    }

    @Test
    fun `anything older falls into the last bucket`() {
        assertEquals(ExpenseDateGroup.OLDER, bucketMonths(-25))
        assertEquals(ExpenseDateGroup.OLDER, bucketMonths(-60))
    }

    @Test
    fun `buckets sort newest first`() {
        val shuffled = listOf(
            ExpenseDateGroup.OLDER,
            ExpenseDateGroup.THIS_WEEK,
            ExpenseDateGroup.LAST_YEAR,
            ExpenseDateGroup.UPCOMING,
        )

        assertEquals(
            listOf(
                ExpenseDateGroup.UPCOMING,
                ExpenseDateGroup.THIS_WEEK,
                ExpenseDateGroup.LAST_YEAR,
                ExpenseDateGroup.OLDER,
            ),
            shuffled.sorted(),
        )
    }

    @Test
    fun `a day earlier in the same week still counts as this week`() {
        // 10 August is the Monday of the week beginning Sunday 9 August, which contains today.
        assertEquals(ExpenseDateGroup.THIS_WEEK, bucketDays(-2))
    }
}
