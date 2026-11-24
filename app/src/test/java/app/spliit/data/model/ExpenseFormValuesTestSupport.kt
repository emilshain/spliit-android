package app.spliit.data.model

import app.spliit.data.remote.ExpenseFormValues
import java.time.Instant

/** Builders for form payloads in tests, kept out of production code. */
object ExpenseFormValuesTestSupport {

    fun simple(
        title: String = "Airport taxi",
        expenseDate: Instant = Instant.EPOCH,
        amount: Int = 4250,
        paidBy: String = "ana",
        splitMode: SplitMode = SplitMode.EVENLY,
        paidFor: List<ExpenseFormValues.PaidFor> = listOf(
            ExpenseFormValues.PaidFor("ana", 100),
            ExpenseFormValues.PaidFor("bruno", 100),
            ExpenseFormValues.PaidFor("chloe", 100),
        ),
    ) = ExpenseFormValues(
        title = title,
        expenseDate = expenseDate,
        amount = amount,
        paidBy = paidBy,
        paidFor = paidFor,
        splitMode = splitMode,
    )
}
