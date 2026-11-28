package app.spliit.domain

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * A date as a person reads it: "Nov 24, 2026", in the phone's own format.
 *
 * A list's day heading is a different job — a fixed shape for a place in a list — and lives in
 * [DateHeading]. This is one date inside a sentence, so it follows the reader's locale.
 */
fun dateLabel(date: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).format(date)

/** The same label for a moment, in the reader's own time zone. */
fun dateLabel(instant: Instant): String = dateLabel(CalendarDay.dayOf(instant))
