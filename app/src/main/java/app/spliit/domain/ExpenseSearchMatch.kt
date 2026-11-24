package app.spliit.domain

/**
 * Why the server returned an expense for a search.
 *
 * `groups.expenses.list` matches its `filter` against the title or the notes (spliit#660), but
 * the list it answers with leaves the notes out. So a result titled "Dinner" for "lake house"
 * would read as a false positive — unless the row says where the match was. Nothing on the wire
 * says so, and asking for every result's notes would be a request per row; this works it out from
 * the title instead. The server matched one or the other, so a title that does not contain the
 * query means the notes did.
 *
 * An instance older than #660 matches titles alone, so every result reads as [TITLE] there and
 * the rows say nothing they didn't before.
 */
enum class ExpenseSearchMatch {
    TITLE,
    NOTES,
    ;

    companion object {

        /**
         * [query] is what was sent as the filter, already trimmed. The comparison is the server's
         * — `contains` with an insensitive mode, an `ILIKE` — so case is ignored and accents are
         * not: "cafe" does not find "Café" there, and must not claim to here.
         */
        fun of(title: String, query: String): ExpenseSearchMatch =
            if (title.contains(query, ignoreCase = true)) TITLE else NOTES
    }
}
