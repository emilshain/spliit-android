package app.spliit.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "NOV 25, 2026 WED": the day a run of expenses happened.
 *
 * The list used to head its sections with spans ("Earlier this year"), which say how long ago
 * something was but never when it was. A date does both, and the weekday is what people actually
 * remember an expense by.
 */
object DateHeading {
    private val format = DateTimeFormatter.ofPattern("MMM d, yyyy EEE", Locale.ENGLISH)

    fun of(date: LocalDate): String = date.format(format).uppercase(Locale.ENGLISH)
}
