package app.spliit.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.spliit.data.model.ExpenseListItem
import app.spliit.domain.CalendarDay
import app.spliit.domain.ExpenseBalance
import app.spliit.domain.MoneyFormatter
import app.spliit.ui.theme.CategoryIcons
import app.spliit.ui.theme.LocalSpliitColors
import app.spliit.ui.theme.Money
import app.spliit.ui.theme.MoneySign
import app.spliit.ui.theme.MoneySize
import app.spliit.ui.theme.ParticipantDot
import app.spliit.ui.theme.SpliitType
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * One expense, as it appears in a list of them.
 *
 * Shared by the expenses tab and by search results, so a matched expense reads exactly as it does
 * in the list it came from — same columns, same money treatment, same category glyph.
 */
@Composable
fun ExpenseRow(
    expense: ExpenseListItem,
    payerPosition: Int,
    formatter: MoneyFormatter,
    activeParticipantId: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CategoryGlyph(expense)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = expense.title,
                style = SpliitType.RowTitle,
                fontStyle = if (expense.isReimbursement) FontStyle.Italic else FontStyle.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Who paid, in the colour they have on the balances screen.
                ParticipantDot(position = payerPosition, size = 7.dp)
                PaidByLine(expense, formatter, activeParticipantId)
            }
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Money(
                value = formatter.string(expense.amount),
                sign = MoneySign.of(expense.amount),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (expense.documentCount > 0) {
                    Icon(
                        Icons.Filled.AttachFile,
                        contentDescription = "${expense.documentCount} documents attached",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = dateLabel(expense),
                    style = SpliitType.Secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryGlyph(expense: ExpenseListItem) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            CategoryIcons.icon(expense.category),
            contentDescription = expense.category?.name,
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Who paid, and — once the user has said who they are — what the expense does to their balance.
 *
 * The balance takes the place of the "for …" names rather than a line of its own, so a row keeps
 * its height.
 */
@Composable
private fun PaidByLine(
    expense: ExpenseListItem,
    formatter: MoneyFormatter,
    activeParticipantId: String?,
) {
    val names = expense.paidFor.joinToString(", ") { it.participant.name }
    val you = activeParticipantId

    if (you != null) {
        val balance = ExpenseBalance.of(you, expense)
        if (balance == null) {
            Text(
                text = "Not involved",
                style = SpliitType.Secondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        if (balance != 0) {
            BalanceSentence(balance, expense, formatter)
            return
        }
    }

    Text(
        text = "Paid by ${expense.paidBy.name} for $names",
        style = SpliitType.Secondary,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "You lent €10.00", tinted like a balance because it has a direction. */
@Composable
private fun BalanceSentence(balance: Int, expense: ExpenseListItem, formatter: MoneyFormatter) {
    val reading = ExpenseBalance.reading(balance, expense)
    val amount = formatter.string(kotlin.math.abs(balance))
    val sentence = when (reading) {
        ExpenseBalance.Reading.LENT -> "You lent $amount"
        ExpenseBalance.Reading.BORROWED -> "You borrowed $amount"
        ExpenseBalance.Reading.PAID_BACK -> "You paid back $amount"
        ExpenseBalance.Reading.WAS_PAID_BACK -> "You were paid back $amount"
        ExpenseBalance.Reading.IS_OWED -> "You are owed $amount"
        ExpenseBalance.Reading.OWES -> "You owe $amount"
    }

    Text(
        text = "$sentence · ${expense.paidBy.name} paid",
        style = SpliitType.Secondary,
        color = if (balance > 0) {
            LocalSpliitColors.current.moneyPositive
        } else {
            LocalSpliitColors.current.moneyNegative
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun dateLabel(expense: ExpenseListItem): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(CalendarDay.dayOf(expense.expenseDate))
