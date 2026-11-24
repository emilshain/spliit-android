package app.spliit.domain

import app.spliit.data.model.ExpenseListItem
import app.spliit.data.model.Participant
import app.spliit.data.model.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Ported one-for-one from `ExpenseBalanceTests.swift`. */
class ExpenseBalanceTest {

    private val ana = Participant("ana", "Ana")
    private val bruno = Participant("bruno", "Bruno")
    private val chloe = Participant("chloe", "Chloé")
    private val dan = Participant("dan", "Dan")

    private fun expense(
        id: String = "e1",
        amount: Int,
        splitMode: SplitMode = SplitMode.EVENLY,
        isReimbursement: Boolean = false,
        paidBy: Participant,
        paidFor: List<Pair<Participant, Int>>,
    ) = ExpenseListItem(
        id = id,
        title = "",
        amount = amount,
        createdAt = Instant.EPOCH,
        expenseDate = Instant.EPOCH,
        isReimbursement = isReimbursement,
        splitMode = splitMode,
        paidBy = paidBy,
        paidFor = paidFor.map { ExpenseListItem.PaidFor(it.first, it.second) },
    )

    @Test
    fun `whoever paid is owed everyone else's share, and everyone else owes their own`() {
        val dinner = expense(
            amount = 12_000,
            paidBy = ana,
            paidFor = listOf(ana to 100, bruno to 100, chloe to 100, dan to 100),
        )
        assertEquals(9_000, ExpenseBalance.of("ana", dinner))
        assertEquals(-3_000, ExpenseBalance.of("bruno", dinner))
        assertEquals(-3_000, ExpenseBalance.of("dan", dinner))
    }

    @Test
    fun `someone the expense was neither paid by nor for is not involved`() {
        val taxi = expense(amount = 4_000, paidBy = ana, paidFor = listOf(ana to 100, bruno to 100))
        assertNull(ExpenseBalance.of("chloe", taxi))
    }

    @Test
    fun `a payer left out of the split is owed the whole amount`() {
        val gift = expense(amount = 5_000, paidBy = ana, paidFor = listOf(bruno to 100, chloe to 100))
        assertEquals(5_000, ExpenseBalance.of("ana", gift))
    }

    @Test
    fun `paying only for yourself leaves you even, and still involved`() {
        val coffee = expense(amount = 450, paidBy = ana, paidFor = listOf(ana to 100))
        assertEquals(0, ExpenseBalance.of("ana", coffee))
    }

    @Test
    fun `an uneven split nets to zero across the group`() {
        for (id in listOf("e1", "e2", "e3", "x")) {
            val museum = expense(
                id = id,
                amount = 10_000,
                paidBy = chloe,
                paidFor = listOf(ana to 100, bruno to 100, chloe to 100),
            )
            val total = listOf(ana, bruno, chloe, dan)
                .mapNotNull { ExpenseBalance.of(it.id, museum) }
                .sum()
            assertEquals("id=$id", 0, total)
        }
    }

    @Test
    fun `each share follows the split mode`() {
        val airbnb = expense(
            amount = 48_000,
            splitMode = SplitMode.BY_SHARES,
            paidBy = ana,
            paidFor = listOf(ana to 200, bruno to 100, chloe to 100),
        )
        assertEquals(24_000, ExpenseBalance.of("ana", airbnb))
        assertEquals(-12_000, ExpenseBalance.of("bruno", airbnb))

        val tickets = expense(
            amount = 8_800,
            splitMode = SplitMode.BY_AMOUNT,
            paidBy = bruno,
            paidFor = listOf(ana to 3_000, bruno to 5_800),
        )
        assertEquals(-3_000, ExpenseBalance.of("ana", tickets))
        assertEquals(3_000, ExpenseBalance.of("bruno", tickets))
    }

    @Test
    fun `paying someone back moves both balances by the whole amount`() {
        val payback = expense(
            amount = 6_769,
            isReimbursement = true,
            paidBy = bruno,
            paidFor = listOf(ana to 100),
        )
        assertEquals(6_769, ExpenseBalance.of("bruno", payback))
        assertEquals(-6_769, ExpenseBalance.of("ana", payback))
        assertNull(ExpenseBalance.of("chloe", payback))
    }

    @Test
    fun `an income leaves whoever received it owing the others their share`() {
        val refund = expense(
            amount = -3_000,
            paidBy = ana,
            paidFor = listOf(ana to 100, bruno to 100, chloe to 100),
        )
        assertEquals(-2_000, ExpenseBalance.of("ana", refund))
        assertEquals(1_000, ExpenseBalance.of("bruno", refund))
    }

    @Test
    fun `a share that comes to nothing leaves you even, not uninvolved`() {
        val snack = expense(
            amount = 1_000,
            splitMode = SplitMode.BY_SHARES,
            paidBy = bruno,
            paidFor = listOf(ana to 0, bruno to 100),
        )
        assertEquals(0, ExpenseBalance.of("ana", snack))
    }

    @Test
    fun `a balance reads by what the expense was, not only by its sign`() {
        val dinner = expense(amount = 9_000, paidBy = ana, paidFor = listOf(ana to 100, bruno to 100))
        assertEquals(ExpenseBalance.Reading.LENT, ExpenseBalance.reading(4_500, dinner))
        assertEquals(ExpenseBalance.Reading.BORROWED, ExpenseBalance.reading(-4_500, dinner))

        val payback = expense(
            amount = 6_769,
            isReimbursement = true,
            paidBy = bruno,
            paidFor = listOf(ana to 100),
        )
        assertEquals(ExpenseBalance.Reading.PAID_BACK, ExpenseBalance.reading(6_769, payback))
        assertEquals(ExpenseBalance.Reading.WAS_PAID_BACK, ExpenseBalance.reading(-6_769, payback))

        val refund = expense(
            amount = -3_000,
            paidBy = ana,
            paidFor = listOf(ana to 100, bruno to 100, chloe to 100),
        )
        assertEquals(ExpenseBalance.Reading.OWES, ExpenseBalance.reading(-2_000, refund))
        assertEquals(ExpenseBalance.Reading.IS_OWED, ExpenseBalance.reading(1_000, refund))
    }
}
