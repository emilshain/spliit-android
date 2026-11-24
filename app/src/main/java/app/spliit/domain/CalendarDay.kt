package app.spliit.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * An expense's date is a calendar day, not a moment.
 *
 * The server keeps `expenseDate` in a Postgres `date` column: it stores the UTC day of whatever
 * instant it is sent, and sends that day back as midnight UTC. The app works with the day
 * itself, which is what the date picker and the date buckets read. This converts between the
 * two at the edge, so nothing past it has to know.
 *
 * Without that, west of UTC every expense shows as the day before, and one saved in the evening
 * is stored as tomorrow. The web app does the same conversion since spliit#433.
 */
object CalendarDay {

    /** Midnight UTC of [day]: what the server stores as that day. */
    fun wire(day: LocalDate): Instant = day.atStartOfDay(ZoneOffset.UTC).toInstant()

    /**
     * The day the server sent as midnight UTC.
     *
     * Deliberately read in UTC, not the device zone: the instant *is* the day, so reading it in
     * a negative offset would move every stored day back one.
     */
    fun dayOf(instant: Instant): LocalDate = instant.atZone(ZoneOffset.UTC).toLocalDate()

    /** Today, as the phone counts it, for a new expense. */
    fun today(zone: ZoneId = ZoneId.systemDefault()): LocalDate = LocalDate.now(zone)

    /** What a new expense's `expenseDate` should be sent as. */
    fun wireToday(zone: ZoneId = ZoneId.systemDefault()): Instant = wire(today(zone))
}
