package app.spliit.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.spliit.data.model.ExpenseListItem
import app.spliit.domain.ExpenseBalance
import app.spliit.domain.MoneyFormatter
import app.spliit.ui.theme.CategoryIcons
import app.spliit.ui.theme.LocalSpliitColors
import app.spliit.ui.theme.Money
import app.spliit.ui.theme.ParticipantDot
import app.spliit.ui.theme.SpliitType
import kotlin.math.abs

/**
 * One expense, as it appears in a list of them.
 *
 * The card leads with what the expense was and what it cost, and only then with what it does to
 * the reader: the direction they stand in, over the amount that direction is worth. The day it
 * happened is the list's heading rather than the row's, so nothing here repeats it.
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
    val balance = activeParticipantId?.let { ExpenseBalance.of(it, expense) }
    val reading = balance?.takeIf { it != 0 }?.let { ExpenseBalance.reading(it, expense) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryGlyph(expense)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = expense.title,
                    style = SpliitType.RowTitle,
                    fontStyle = if (expense.isReimbursement) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // The whole expense, and who put it in: the figure the group is splitting, as
                // opposed to the reader's own part on the right.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ParticipantDot(position = payerPosition, size = 7.dp)
                    Text(
                        text = "${expense.paidBy.name} paid ${formatter.string(expense.amount)}",
                        style = SpliitType.Secondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (expense.documentCount > 0) {
                        Icon(
                            Icons.Filled.AttachFile,
                            contentDescription = "${expense.documentCount} documents attached",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (reading != null && balance != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = readingLabel(reading),
                        style = SpliitType.Secondary,
                        color = if (balance > 0) {
                            LocalSpliitColors.current.moneyPositive
                        } else {
                            LocalSpliitColors.current.moneyNegative
                        },
                    )
                    Money(value = formatter.string(abs(balance)))
                }
            }
        }
    }
}

@Composable
private fun CategoryGlyph(expense: ExpenseListItem) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(13.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            CategoryIcons.icon(expense.category),
            contentDescription = expense.category?.name,
            modifier = Modifier.size(21.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What the row says above the reader's share of the expense. */
private fun readingLabel(reading: ExpenseBalance.Reading): String = when (reading) {
    ExpenseBalance.Reading.LENT -> "You lent"
    ExpenseBalance.Reading.BORROWED -> "You borrowed"
    ExpenseBalance.Reading.PAID_BACK -> "You paid back"
    ExpenseBalance.Reading.WAS_PAID_BACK -> "You were paid back"
    ExpenseBalance.Reading.IS_OWED -> "You are owed"
    ExpenseBalance.Reading.OWES -> "You owe"
}
