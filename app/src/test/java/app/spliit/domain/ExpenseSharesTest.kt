package app.spliit.domain

import app.spliit.data.model.ExpenseDetails
import app.spliit.data.model.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cases are the web app's own (`src/lib/shares.test.ts`) and the iOS port's
 * (`ExpenseSharesTests.swift`), so that a split here, there and on the balances tab agree to the
 * minor unit — that is the whole point of the port.
 */
class ExpenseSharesTest {

    private fun row(id: String, shares: Int) = ExpenseDetails.PaidFor(participantId = id, shares = shares)

    private fun shares(
        id: String? = "e1",
        amount: Int,
        splitMode: SplitMode = SplitMode.EVENLY,
        rows: List<Pair<String, Int>>,
    ): Map<String, Int> = ExpenseShares.shares(
        expenseId = id,
        amount = amount,
        splitMode = splitMode,
        paidFor = rows.map { row(it.first, it.second) },
    )

    @Test
    fun `the whole amount is split and nothing more, whatever the mode`() {
        for (mode in SplitMode.entries) {
            val split = shares(amount = 9500, splitMode = mode, rows = listOf("alice" to 1, "bob" to 1, "carol" to 1))
            assertEquals("mode $mode", 9500, split.values.sum())
        }
    }

    @Test
    fun `an even split leaves no stranded minor unit`() {
        val split = shares(amount = 9500, rows = listOf("alice" to 1, "bob" to 1, "carol" to 1))
        assertEquals(listOf(3166, 3167, 3167), split.values.sorted())
    }

    @Test
    fun `the stored shares are ignored when splitting evenly`() {
        val split = shares(amount = 100, rows = listOf("alice" to 7, "bob" to 1))
        assertEquals(mapOf("alice" to 50, "bob" to 50), split)
    }

    @Test
    fun `shares split proportionally`() {
        val split = shares(amount = 100, splitMode = SplitMode.BY_SHARES, rows = listOf("alice" to 1, "bob" to 2))
        assertEquals(mapOf("alice" to 33, "bob" to 67), split)
    }

    @Test
    fun `amounts that already add up are left exactly as they are`() {
        val split = shares(
            amount = 1000,
            splitMode = SplitMode.BY_AMOUNT,
            rows = listOf("alice" to 333, "bob" to 333, "carol" to 334),
        )
        assertEquals(mapOf("alice" to 333, "bob" to 333, "carol" to 334), split)
    }

    @Test
    fun `percentages that do not add up to 100 are taken as a ratio`() {
        val split = shares(
            amount = 100,
            splitMode = SplitMode.BY_PERCENTAGE,
            rows = listOf("alice" to 6000, "bob" to 2000),
        )
        assertEquals(mapOf("alice" to 75, "bob" to 25), split)
    }

    @Test
    fun `amounts that do not add up to the total are taken as a ratio`() {
        val split = shares(amount = 100, splitMode = SplitMode.BY_AMOUNT, rows = listOf("alice" to 30, "bob" to 30))
        assertEquals(100, split.values.sum())
    }

    @Test
    fun `an income splits without losing a minor unit`() {
        val split = shares(amount = -9500, rows = listOf("alice" to 1, "bob" to 1, "carol" to 1))
        assertEquals(-9500, split.values.sum())
        assertEquals(listOf(-3167, -3167, -3166), split.values.sorted())
    }

    @Test
    fun `shares adding up to zero give everyone nothing`() {
        val split = shares(amount = 100, splitMode = SplitMode.BY_SHARES, rows = listOf("alice" to 0, "bob" to 0))
        assertEquals(mapOf("alice" to 0, "bob" to 0), split)
    }

    @Test
    fun `an expense nobody was paid for has no shares`() {
        assertTrue(shares(amount = 100, rows = emptyList()).isEmpty())
    }

    @Test
    fun `the order the rows come back in makes no difference`() {
        val rows = listOf("alice" to 1, "bob" to 1, "carol" to 1)
        assertEquals(shares(amount = 100, rows = rows), shares(amount = 100, rows = rows.reversed()))
    }

    @Test
    fun `the leftover minor unit does not always go to the same participant`() {
        val participants = listOf("alice", "bob", "carol")
        val receivers = mutableSetOf<String>()

        for (index in 0 until 50) {
            val split = shares(id = "expense-$index", amount = 100, rows = participants.map { it to 1 })
            participants.filter { split[it] == 34 }.forEach(receivers::add)
        }

        assertEquals(participants.toSet(), receivers)
    }

    @Test
    fun `without an ID the rotation starts at the first participant`() {
        val split = shares(id = null, amount = 100, rows = listOf("bob" to 1, "alice" to 1, "carol" to 1))
        assertEquals(34, split["alice"])
        assertEquals(100, split.values.sum())
    }

    @Test
    fun `an empty ID is no ID`() {
        val split = shares(id = "", amount = 100, rows = listOf("alice" to 1, "bob" to 1, "carol" to 1))
        assertEquals(mapOf("alice" to 34, "bob" to 33, "carol" to 33), split)
    }

    /**
     * Who exactly gets the odd cent, for three IDs that hash to the three possible rotations,
     * with the answers taken from the web app's `getExpenseShares`.
     */
    @Test
    fun `the odd cent lands where the web app lands it`() {
        val cases = listOf(
            Triple("e1", 100, mapOf("alice" to 33, "bob" to 33, "carol" to 34)),
            Triple("e2", 100, mapOf("alice" to 33, "bob" to 34, "carol" to 33)),
            Triple("e3", 100, mapOf("alice" to 34, "bob" to 33, "carol" to 33)),
            Triple("e1", 9500, mapOf("alice" to 3167, "bob" to 3166, "carol" to 3167)),
            Triple("e2", 9500, mapOf("alice" to 3166, "bob" to 3167, "carol" to 3167)),
            Triple("e3", 9500, mapOf("alice" to 3167, "bob" to 3167, "carol" to 3166)),
        )

        for ((id, amount, expected) in cases) {
            assertEquals(
                "id=$id amount=$amount",
                expected,
                shares(id = id, amount = amount, rows = listOf("alice" to 1, "bob" to 1, "carol" to 1)),
            )
        }
    }

    /**
     * The form recomputes this on every keystroke, so a share typed with too many zeros must
     * come out as a number, not an overflow — the sum as much as the product.
     *
     * The iOS test uses 5×10^18 because Swift's `Int` is 64-bit; the wire field is a Prisma
     * `Int`, which is 32-bit, so `Int.MAX_VALUE` is the largest value that can reach here.
     */
    @Test
    fun `enormous shares add up and multiply out without overflowing`() {
        val huge = Int.MAX_VALUE
        val split = shares(amount = 100, splitMode = SplitMode.BY_SHARES, rows = listOf("alice" to huge, "bob" to huge))
        assertEquals(mapOf("alice" to 50, "bob" to 50), split)
    }

    /** FNV-1a's published test vectors, and the web app's own ID shape. */
    @Test
    fun `the hash is FNV-1a over UTF-16 code units`() {
        assertEquals(0x811c9dc5u, ExpenseShares.fnv1a(""))
        assertEquals(0xe40c292cu, ExpenseShares.fnv1a("a"))
        assertEquals(0xbf9cf968u, ExpenseShares.fnv1a("foobar"))
        assertEquals(0x5216378fu, ExpenseShares.fnv1a("clx0v3kqf0001l3086v6y8t4c"))
    }

    @Test
    fun `an id hashing to a given rotation picks that participant`() {
        // Sanity check on the offset: e1..e3 must produce three distinct rotations.
        val rotations = (1..3).map { index ->
            val id = "e$index"
            val split = shares(id = id, amount = 100, rows = listOf("alice" to 1, "bob" to 1, "carol" to 1))
            split.entries.first { it.value == 34 }.key
        }
        assertEquals(3, rotations.toSet().size)
    }
}
