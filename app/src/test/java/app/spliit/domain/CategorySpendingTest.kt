package app.spliit.domain

import app.spliit.data.model.ExpenseCategory
import app.spliit.data.model.ExpenseListItem
import app.spliit.data.model.Participant
import app.spliit.data.model.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Folding a group's expenses into what it spent per category.
 *
 * The fold is deliberately the web app's `getSpendingByCategory`, so these pin the behaviours that
 * would otherwise drift: reimbursements excluded, uncategorised counted as General, empty
 * categories dropped, biggest first.
 */
class CategorySpendingTest {

    private val groceries = ExpenseCategory(id = 5, grouping = "Food and Drink", name = "Groceries")
    private val taxi = ExpenseCategory(id = 12, grouping = "Transportation", name = "Taxi")

    @Test
    fun `expenses are summed per category, biggest spend first`() {
        val breakdown = CategorySpending.breakdownOf(
            listOf(
                expense("Market", 2000, groceries),
                expense("Airport", 4250, taxi),
                expense("Corner shop", 1500, groceries),
            ),
        )

        // Taxi's single 4250 outranks the two shopping trips that came to 3500 between them.
        assertEquals(listOf(12, 5), breakdown.map { it.categoryId })
        assertEquals(listOf(4250, 3500), breakdown.map { it.total })
        assertEquals("Groceries", breakdown.last().name)
        assertEquals("Food and Drink", breakdown.last().grouping)
    }

    /** The invariant the screen leans on: it only draws when it adds up to the reported total. */
    @Test
    fun `reimbursements are not spending and are left out`() {
        val breakdown = CategorySpending.breakdownOf(
            listOf(
                expense("Hotel", 10000, groceries),
                expense("Paying Ana back", 5000, taxi, isReimbursement = true),
            ),
        )

        assertEquals(1, breakdown.size)
        assertEquals(10000, breakdown.sumOf { it.total })
    }

    @Test
    fun `an expense with no category counts as General`() {
        val general = ExpenseCategory(id = 0, grouping = "Uncategorized", name = "General")
        val breakdown = CategorySpending.breakdownOf(
            listOf(
                expense("Something", 800),
                expense("Something else", 200, general),
            ),
        )

        assertEquals(1, breakdown.size)
        assertEquals(0, breakdown.first().categoryId)
        assertEquals("General", breakdown.first().name)
        assertEquals(1000, breakdown.first().total)
    }

    /** A refund can cancel a category out entirely. A row reading zero is not worth a line. */
    @Test
    fun `a category that nets to nothing is dropped, and the rest still add up`() {
        val breakdown = CategorySpending.breakdownOf(
            listOf(
                expense("Deposit", 5000, taxi),
                expense("Deposit returned", -5000, taxi),
                expense("Market", 2000, groceries),
            ),
        )

        assertEquals(listOf(5), breakdown.map { it.categoryId })
        assertEquals(2000, breakdown.sumOf { it.total })
    }

    /** Negative totals sort below positive ones rather than being treated as large. */
    @Test
    fun `a category that came to a refund sorts last and keeps its sign`() {
        val breakdown = CategorySpending.breakdownOf(
            listOf(
                expense("Refund", -1500, taxi),
                expense("Market", 2000, groceries),
            ),
        )

        assertEquals(listOf(2000, -1500), breakdown.map { it.total })
    }

    /** Two categories on the same amount need something else to order them, or they swap places. */
    @Test
    fun `categories on equal amounts keep a fixed order`() {
        val expenses = listOf(
            expense("Market", 2000, groceries),
            expense("Airport", 2000, taxi),
        )

        val first = CategorySpending.breakdownOf(expenses).map { it.categoryId }
        val second = CategorySpending.breakdownOf(expenses.reversed()).map { it.categoryId }

        assertEquals(listOf(5, 12), first)
        assertEquals(first, second)
    }

    /** The screen folds a page at a time so it never holds a whole group's expenses in memory. */
    @Test
    fun `folding page by page gives the same breakdown as folding all at once`() {
        val expenses = listOf(
            expense("Market", 2000, groceries),
            expense("Airport", 4250, taxi),
            expense("Corner shop", 1500, groceries),
            expense("Settling up", 900, taxi, isReimbursement = true),
            expense("Something", 300),
        )

        val fold = CategorySpending.Fold()
        fold.add(expenses.subList(0, 2))
        fold.add(expenses.subList(2, 4))
        fold.add(expenses.subList(4, 5))

        assertEquals(CategorySpending.breakdownOf(expenses), fold.breakdown)
        assertEquals(2000 + 4250 + 1500 + 300, fold.breakdown.sumOf { it.total })
    }

    @Test
    fun `a group with nothing but reimbursements has no breakdown at all`() {
        assertTrue(CategorySpending.breakdownOf(emptyList()).isEmpty())
        assertTrue(
            CategorySpending.breakdownOf(
                listOf(expense("Settling up", 500, taxi, isReimbursement = true)),
            ).isEmpty(),
        )
    }

    private fun expense(
        title: String,
        amount: Int,
        category: ExpenseCategory? = null,
        isReimbursement: Boolean = false,
    ): ExpenseListItem {
        val at = Instant.parse("2026-08-13T09:00:00Z")
        return ExpenseListItem(
            id = title,
            title = title,
            amount = amount,
            createdAt = at,
            expenseDate = at,
            isReimbursement = isReimbursement,
            splitMode = SplitMode.EVENLY,
            category = category,
            paidBy = Participant(id = "p1", name = "Ana"),
        )
    }
}
