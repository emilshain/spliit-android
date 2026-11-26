package app.spliit.domain

import app.spliit.data.model.ExpenseListItem
import app.spliit.data.model.Group
import app.spliit.data.model.Participant
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Splitwise's expense CSV: what every other expense app will open, and what this app reads back.
 *
 * The format is one header row and one row per expense:
 *
 * ```
 * Date,Description,Category,Cost,Currency,Alice,Bob
 * 2026-03-04,Groceries,Groceries,42.50,EUR,21.25,21.25
 * ```
 *
 * The columns from the first participant name onwards are amounts: Splitwise writes what each
 * person *paid*, while most exports people hand around carry what each person *owes*. Which one a
 * file is can be read from the row itself — if the person columns add up to the cost, they are
 * shares; if not, they are payments. [parse] decides per file across every row and records it, and
 * [GroupScreenModel] writes the matching kind of expense.
 *
 * No CSV library: the dialect is fixed (comma, double quotes, CRLF) and a dependency that parses
 * more than that would still need this mapping.
 */
object SplitwiseCsv {

    /** One expense from a file, already in minor units. */
    data class Row(
        val date: LocalDate,
        val description: String,
        val category: String,
        /** Total, in minor units. */
        val amount: Int,
        /** The currency the row states, if any: an ISO code when the file used one. */
        val currency: String,
        /** What each participant owes, when the file lists shares rather than payments. */
        val owed: Map<String, Int> = emptyMap(),
        /** What each participant paid, when the file lists payments. */
        val paid: Map<String, Int> = emptyMap(),
    ) {
        /** Names this expense involves, whichever kind of CSV it came from. */
        val participants: List<String>
            get() = (owed.keys + paid.keys).distinct()

        val payer: String? get() = paid.maxByOrNull { it.value }?.key
    }

    /** Raised for a file that is not an expense CSV at all. */
    class NotASplitwiseCsv(message: String) : Exception(message)

    /** Every row [text] holds. Throws [NotASplitwiseCsv] when the header is missing. */
    fun parse(text: String, minorUnitDigits: Int = 2): List<Row> {
        val records = records(text)
        if (records.isEmpty()) throw NotASplitwiseCsv("The file is empty.")

        val header = records.first().map { it.trim() }
        val dateIndex = header.indexOfFirst { it.equals("date", ignoreCase = true) }
        val costIndex = header.indexOfFirst { it.equals("cost", ignoreCase = true) }
        val descriptionIndex = header.indexOfFirst { it.equals("description", ignoreCase = true) }
        if (dateIndex < 0 || costIndex < 0) {
            throw NotASplitwiseCsv("The file has no Date and Cost columns.")
        }
        val categoryIndex = header.indexOfFirst { it.equals("category", ignoreCase = true) }
        val currencyIndex = header.indexOfFirst { it.equals("currency", ignoreCase = true) }
        val firstPerson = maxOf(dateIndex, costIndex, descriptionIndex, categoryIndex, currencyIndex) + 1
        val people = header.drop(firstPerson).filter { it.isNotEmpty() }

        val rows = records.drop(1).mapNotNull { cells ->
            if (cells.size <= costIndex) return@mapNotNull null
            val amount = money(cells[costIndex], minorUnitDigits) ?: return@mapNotNull null
            if (amount == 0) return@mapNotNull null
            val date = date(cells[dateIndex]) ?: return@mapNotNull null
            val perPerson = people.mapIndexed { offset, name ->
                name to (money(cells.getOrNull(firstPerson + offset).orEmpty(), minorUnitDigits) ?: 0)
            }.toMap()

            val stated = perPerson.values.sum()
            // Whole-file decision, per row: shares add up to the cost, payments do not.
            val asShares = stated == amount
            Row(
                date = date,
                description = cells.getOrNull(descriptionIndex).orEmpty().ifBlank { "Imported expense" },
                category = cells.getOrNull(categoryIndex).orEmpty(),
                amount = amount,
                currency = cells.getOrNull(currencyIndex).orEmpty(),
                owed = if (asShares) perPerson.filterValues { it != 0 } else emptyMap(),
                paid = if (asShares) emptyMap() else perPerson.filterValues { it != 0 },
            )
        }
        return rows
    }

    /**
     * The app's expenses in the same shape.
     *
     * The group's participants fix the columns, in the group's own order, so two exports of the
     * same group can be diffed. Each cell is what that person owes on the row, from
     * [ExpenseShares] — the same apportionment the balances tab uses, so the columns add up to the
     * cost exactly.
     */
    fun write(group: Group, expenses: List<ExpenseListItem>): String {
        val digits = MoneyFormatter.minorUnitDigitsFor(group.currencyCode, Locale.getDefault())
        val currency = group.currencyCode ?: group.currency
        val header = buildString {
            append("Date,Description,Category,Cost,Currency")
            group.participants.forEach { append(',').append(escape(it.name)) }
        }

        val body = expenses.joinToString("\r\n") { expense ->
            val shares = ExpenseShares.shares(
                expenseId = expense.id,
                amount = expense.amount,
                splitMode = expense.splitMode,
                paidFor = expense.paidFor,
            )
            buildString {
                append(expense.expenseDate.atZone(java.time.ZoneOffset.UTC).toLocalDate())
                append(',').append(escape(expense.title))
                append(',').append(escape(expense.category?.name.orEmpty()))
                append(',').append(decimal(expense.amount, digits))
                append(',').append(escape(currency))
                group.participants.forEach { participant ->
                    val owed = shares[participant.id] ?: 0
                    append(',').append(if (owed == 0) "" else decimal(owed, digits))
                }
            }
        }
        return if (body.isEmpty()) "$header\r\n" else "$header\r\n$body\r\n"
    }

    /** The name a group participant goes by in a file: trimmed and case-folded. */
    fun participantKey(name: String): String = name.trim().lowercase(Locale.ROOT)

    /** The participant a file's column names, if the group has one. */
    fun match(name: String, participants: List<Participant>): Participant? {
        val key = participantKey(name)
        return participants.firstOrNull { participantKey(it.name) == key }
    }

    // MARK: - Cells

    private val IsoDate = DateTimeFormatter.ISO_LOCAL_DATE
    private val UsDate = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US)

    private fun date(cell: String): LocalDate? {
        val trimmed = cell.trim()
        if (trimmed.isEmpty()) return null
        return runCatching { LocalDate.parse(trimmed, IsoDate) }
            .recoverCatching { LocalDate.parse(trimmed, UsDate) }
            .getOrNull()
    }

    /** A cell such as "42.50", "1,234.50" or "42,50" as minor units. */
    private fun money(cell: String, minorUnitDigits: Int): Int? {
        var text = cell.trim().removePrefix("$").removePrefix("€").removePrefix("£")
        if (text.isEmpty()) return null
        // A lone comma with two digits after it is a decimal comma, not a thousands separator.
        text = when {
            text.contains(',') && !text.contains('.') && text.substringAfterLast(',').length <= 2 ->
                text.replace(',', '.')
            else -> text.replace(",", "")
        }
        val parsed = text.toBigDecimalOrNull() ?: return null
        return runCatching {
            parsed.movePointRight(minorUnitDigits).setScale(0, RoundingMode.HALF_UP).toInt()
        }.getOrNull()
    }

    private fun decimal(minorUnits: Int, digits: Int): String =
        BigDecimal.valueOf(minorUnits.toLong(), digits).toPlainString()

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** The rows of a CSV, each a list of unquoted cells. */
    private fun records(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        val source = text.removePrefix("\uFEFF")

        fun endCell() {
            cells += cell.toString()
            cell.setLength(0)
        }

        fun endRow() {
            endCell()
            if (cells.any { it.isNotBlank() }) rows += cells.toList()
            cells = mutableListOf()
        }

        while (index < source.length) {
            val char = source[index]
            when {
                quoted && char == '"' && source.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index += 2
                    continue
                }

                quoted && char == '"' -> quoted = false
                !quoted && char == '"' -> quoted = true
                !quoted && char == ',' -> endCell()
                !quoted && (char == '\n' || char == '\r') -> {
                    endRow()
                    if (char == '\r' && source.getOrNull(index + 1) == '\n') index++
                }

                else -> cell.append(char)
            }
            index++
        }
        if (cell.isNotEmpty() || cells.isNotEmpty()) endRow()
        return rows
    }
}
