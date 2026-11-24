package app.spliit.domain

import app.spliit.data.model.ExpenseListItem

/**
 * Where a single expense leaves one participant: what they paid, less what their share of it
 * comes to.
 *
 * The share comes from [ExpenseShares], so these figures add up, expense by expense, to the
 * participant's total on the balances tab.
 */
object ExpenseBalance {

    /**
     * In the group's minor units: positive when the expense leaves the participant owed,
     * negative when it leaves them owing, zero when it leaves them even — they paid exactly
     * their own share, or their share came to nothing. Null when they neither paid it nor were
     * in it — not involved, which is a different thing from even.
     */
    fun of(participantId: String, expense: ExpenseListItem): Int? {
        val shares = ExpenseShares.shares(
            expenseId = expense.id,
            amount = expense.amount,
            splitMode = expense.splitMode,
            paidFor = expense.paidFor,
        )
        val share = shares[participantId]
        val didPay = expense.paidBy.id == participantId
        if (!didPay && share == null) return null
        return (if (didPay) expense.amount else 0) - (share ?: 0)
    }

    /** What a balance that is not zero means, which depends on what the expense was as much as on its sign. */
    enum class Reading {
        /** A spend you paid for others. */
        LENT,

        /** A spend somebody else paid for you. */
        BORROWED,

        /** A reimbursement you paid: it settles a debt rather than creating one. */
        PAID_BACK,

        /** A reimbursement somebody paid you. */
        WAS_PAID_BACK,

        /** An income: whoever received it holds the others' shares, and has lent nobody anything. */
        IS_OWED,

        /** An income somebody else received. */
        OWES,
    }

    fun reading(balance: Int, expense: ExpenseListItem): Reading {
        val isOwed = balance > 0
        return when {
            expense.isReimbursement -> if (isOwed) Reading.PAID_BACK else Reading.WAS_PAID_BACK
            expense.amount < 0 -> if (isOwed) Reading.IS_OWED else Reading.OWES
            else -> if (isOwed) Reading.LENT else Reading.BORROWED
        }
    }
}
