package app.spliit.domain

import app.spliit.data.model.ExpenseShareRow
import app.spliit.data.model.SplitMode
import java.math.BigInteger

/**
 * What each participant's share of an expense comes to, in whole minor units.
 *
 * A port of the web app's `getExpenseShares` (`src/lib/shares.ts`), which is the one definition
 * there of who owes what on an expense: the balances tab, the stats and the CSV export all go
 * through it, and so does the amount printed beside each participant in its expense form. The
 * numbers here have to be those numbers, to the minor unit, or the app would show a share the
 * server never charges anyone.
 *
 * The shares always add up to exactly the amount, whatever the split mode. Every participant
 * gets the floor of their exact share first, and the units left over go to the largest
 * remainders — Hamilton apportionment. An even split leaves every remainder equal, so ties are
 * broken by rotating the starting position with a hash of the expense's ID: the extra cent then
 * lands on somebody different from one expense to the next, and on the same somebody every time
 * the same expense is asked about.
 */
object ExpenseShares {

    private val INT_MIN = BigInteger.valueOf(Int.MIN_VALUE.toLong())
    private val INT_MAX = BigInteger.valueOf(Int.MAX_VALUE.toLong())

    /**
     * The share per participant ID. A participant the expense was not paid for is absent.
     *
     * @param expenseId seeds the rotation deciding who is offered the leftover minor unit.
     *   Nil starts the rotation at the first participant, as an empty ID does in the web app.
     * @param amount the expense total, in the group's minor units.
     * @param paidFor who it was for, and their stored shares. [SplitMode.BY_PERCENTAGE] and
     *   [SplitMode.BY_AMOUNT] shares are taken as a ratio rather than literally, so a row that
     *   does not add up still divides the whole amount and nothing leaks.
     */
    fun shares(
        expenseId: String?,
        amount: Int,
        splitMode: SplitMode,
        paidFor: List<ExpenseShareRow>,
    ): Map<String, Int> {
        // The server fetches `paidFor` without an `orderBy`, so the rows can come back in any
        // order. The web app sorts by participant ID before splitting so the result does not
        // depend on it; so does this, on the same key — UTF-16 code units, which is what a
        // JavaScript `<` compares.
        val rows = paidFor.sortedWith(compareBy { it.participantId })

        val weights: List<Int> = when (splitMode) {
            SplitMode.EVENLY -> rows.map { 1 }
            SplitMode.BY_SHARES, SplitMode.BY_PERCENTAGE, SplitMode.BY_AMOUNT ->
                rows.map { it.shares }
        }

        // An empty ID counts as none, as it does in JavaScript — `expense.id && …` is false for "".
        val offset = if (expenseId.isNullOrEmpty() || rows.isEmpty()) {
            0
        } else {
            (fnv1a(expenseId) % rows.size.toUInt()).toInt()
        }
        val amounts = apportion(amount, weights, offset)

        val result = LinkedHashMap<String, Int>(rows.size)
        rows.forEachIndexed { index, row ->
            result.putIfAbsent(row.participantId, amounts[index])
        }
        return result
    }

    /**
     * Splits [amount] over [weights] so that the parts add up to exactly [amount], in the order
     * the weights were given.
     *
     * @param offset where the tie-break between equal remainders starts, so an even split does
     *   not hand the extra unit to the same participants every time.
     */
    internal fun apportion(amount: Int, weights: List<Int>, offset: Int): List<Int> {
        val count = weights.size
        if (count == 0) return emptyList()

        // Wide enough that a share count somebody typed with too many zeros adds up and
        // multiplies out rather than overflowing. Shares are validated as positive on write, but
        // a legacy or directly written row is not guaranteed to be, so a total of zero is
        // handled rather than divided by.
        val total = weights.fold(BigInteger.ZERO) { acc, weight ->
            acc + BigInteger.valueOf(weight.toLong())
        }
        if (total.signum() == 0) return weights.map { 0 }

        val amountWide = BigInteger.valueOf(amount.toLong())
        val amounts = IntArray(count)
        val remainders = ArrayList<Remainder>(count)
        var distributed = 0L

        weights.forEachIndexed { index, weight ->
            val exact = amountWide.multiply(BigInteger.valueOf(weight.toLong()))
            val base = flooredDivide(exact, total)
            val clamped = base.coerceIn(INT_MIN, INT_MAX).toInt()
            amounts[index] = clamped
            distributed += clamped.toLong()
            remainders += Remainder(index, exact - base.multiply(total))
        }

        val remaining = amount.toLong() - distributed
        if (remaining <= 0) return amounts.toList()

        // Largest remainder first; among equals, the one nearest after `offset` going round.
        val rotate = { index: Int -> (index - offset + count) % count }
        remainders.sortWith(
            Comparator { left, right ->
                val byRemainder = right.remainder.compareTo(left.remainder)
                if (byRemainder != 0) byRemainder else rotate(left.index).compareTo(rotate(right.index))
            },
        )
        for (slot in 0 until minOf(remaining, count.toLong()).toInt()) {
            amounts[remainders[slot].index] += 1
        }
        return amounts.toList()
    }

    /**
     * FNV-1a, 32-bit, over UTF-16 code units — bit for bit the web app's `hashString`, which
     * walks `charCodeAt`. Any other hash would give the leftover cent to a different person than
     * the balances tab charges it to.
     */
    fun fnv1a(value: String): UInt {
        var hash = 0x811c9dc5u
        for (unit in value) {
            hash = hash xor unit.code.toUInt()
            hash *= 0x01000193u
        }
        return hash
    }

    private data class Remainder(val index: Int, val remainder: BigInteger)

    /** Division rounding toward negative infinity, like JavaScript's `Math.floor(a / b)`. */
    private fun flooredDivide(dividend: BigInteger, divisor: BigInteger): BigInteger {
        val quotient = dividend.divide(divisor)
        val hasRemainder = dividend.remainder(divisor).signum() != 0
        val signsDiffer = (dividend.signum() < 0) != (divisor.signum() < 0)
        return if (hasRemainder && signsDiffer) quotient - BigInteger.ONE else quotient
    }
}
