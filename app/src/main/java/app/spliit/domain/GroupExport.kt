package app.spliit.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The instance's own CSV export of a group's expenses.
 *
 * Spliit serves that file already — one row per expense, one column per participant, with the
 * same apportionment the balances tab uses — so the app downloads it rather than rebuilding it
 * from pages of expenses. That is also why no CSV library is involved: the quoting, the BOM for
 * spreadsheets and the formula-injection escaping all happen where the web app's export button
 * gets them from, and the app gets exactly the same bytes.
 */
object GroupExport {

    /** The path the web app's export button points at, relative to the instance root. */
    fun csvPath(groupId: String): String = "groups/$groupId/expenses/export/csv"

    /** The name the web app gives the download: the group, and the day it was taken. */
    fun fileName(groupName: String, date: LocalDate = LocalDate.now()): String =
        "Spliit Export - $groupName - ${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}.csv"
}
