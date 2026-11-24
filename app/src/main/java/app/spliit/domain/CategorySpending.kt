package app.spliit.domain

import app.spliit.data.model.ExpenseListItem

/**
 * What a group spent, broken down by the category each expense was filed under.
 *
 * Folded on the client, from the expenses themselves. `groups.stats.get` answers three totals and
 * no more, and every published image still serves that one — so a tab that only read a breakdown
 * off the wire would have nothing to show on a self-hosted instance.
 *
 * This is deliberately the *same* fold as the web app's `getSpendingByCategory`, down to the
 * zero-total filter and the descending sort.
 */
data class CategorySpending(
    /**
     * The server's category ID. Zero stands for both the "General" category and an expense filed
     * under nothing at all, which is the same conflation the web app makes — and harmless,
     * because an uncategorised expense *is* a general one.
     */
    val categoryId: Int,
    /** The server's own English words, untranslated. */
    val grouping: String,
    val name: String,
    /** Minor units, in the group's currency. Can be negative: a refund is an expense too. */
    val total: Int,
) {

    /**
     * Sums [expenses] into one entry per category, biggest spend first.
     *
     * Reimbursements are left out, which is what makes this add up to the `totalGroupSpendings`
     * the stats endpoint reports — settling up is not spending.
     */
    companion object {

        fun breakdownOf(expenses: List<ExpenseListItem>): List<CategorySpending> =
            Fold().apply { add(expenses) }.breakdown
    }

    /**
     * The same sum, a page at a time.
     *
     * For a caller paging through an expense list it has no reason to keep: the running totals
     * are a handful of integers per category, so a group of two thousand expenses only ever
     * holds the hundred currently being folded.
     */
    class Fold {

        private val totals = LinkedHashMap<Int, CategorySpending>()

        fun add(expenses: List<ExpenseListItem>) {
            for (expense in expenses) {
                if (expense.isReimbursement) continue
                val category = expense.category
                val id = category?.id ?: 0
                val running = totals[id]?.total ?: 0
                totals[id] = CategorySpending(
                    categoryId = id,
                    grouping = category?.grouping ?: "Uncategorized",
                    name = category?.name ?: "General",
                    total = running + expense.amount,
                )
            }
        }

        /**
         * Categories that net to exactly zero are dropped rather than drawn as a row with
         * nothing in it. They contribute nothing to the sum either way, so the total still
         * reconciles.
         *
         * Descending by spend, then by ID: without a tiebreak two categories on the same amount
         * could swap places on a refresh that changed neither.
         */
        val breakdown: List<CategorySpending>
            get() = totals.values
                .filter { it.total != 0 }
                .sortedWith(compareByDescending<CategorySpending> { it.total }.thenBy { it.categoryId })
    }
}
