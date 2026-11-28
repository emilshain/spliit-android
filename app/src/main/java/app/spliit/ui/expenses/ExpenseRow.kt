package app.spliit.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.spliit.data.model.ExpenseListItem
import app.spliit.domain.ExpenseBalance
import app.spliit.domain.MoneyFormatter
import app.spliit.ui.theme.CategoryIcons
import app.spliit.ui.theme.LocalSpliitColors
import app.spliit.ui.theme.MonogramPalette
import app.spliit.ui.theme.Money
import app.spliit.ui.theme.pressScale
import app.spliit.ui.theme.SpliitDimens
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
 *
 * When [onClick] is given the card itself is the button: the ripple is the card's own, clipped to
 * its corners, rather than a full-width rectangle behind it.
 */
@Composable
fun ExpenseRow(
    expense: ExpenseListItem,
    payerPosition: Int,
    formatter: MoneyFormatter,
    activeParticipantId: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(SpliitDimens.CardCorner),
    /** A change saved here that has not reached the server yet. */
    pending: Boolean = false,
    onClick: (() -> Unit)? = null,
    /** Shared with the press animation, so the card dips while it is touched. */
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val balance = activeParticipantId?.let { ExpenseBalance.of(it, expense) }
    val reading = balance?.takeIf { it != 0 }?.let { ExpenseBalance.reading(it, expense) }

    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = SpliitDimens.RowPaddingHorizontal,
                vertical = SpliitDimens.RowPaddingVertical,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryGlyph(expense, payerPosition)

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
                // opposed to the reader's own part on the right. Their colour is already on the
                // glyph to the left, so it is not repeated as a dot here.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${expense.paidBy.name} paid ${formatter.string(expense.amount)}",
                        style = SpliitType.Secondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (expense.documentCount > 0) {
                        Icon(
                            Icons.Filled.Receipt,
                            contentDescription = "${expense.documentCount} receipts attached",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // A waiting expense is still an expense: it sits in the list, at the amount it
                // will be saved with, and says plainly that it has not gone yet.
                if (pending) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Waiting to send",
                            style = SpliitType.Secondary,
                            color = MaterialTheme.colorScheme.primary,
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

    val container = modifier
        .fillMaxWidth()
        // The gutter only: the gap to the row above is the list's, applied through
        // `stackedCardPadding`, so every stack in the app spaces its rows the same way.
        .padding(horizontal = SpliitDimens.ScreenGutter)
        .then(
            if (onClick != null) Modifier.pressScale(interactionSource, pressedScale = 0.98f)
            else Modifier,
        )

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = container,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            interactionSource = interactionSource,
        ) {
            body()
        }
    } else {
        Surface(
            modifier = container,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            body()
        }
    }
}

@Composable
private fun CategoryGlyph(expense: ExpenseListItem, payerPosition: Int) {
    // The tile is tinted with the colour of whoever paid: the row is about them, and the dot that
    // used to say so beside the line is now this. The ink, not the fill — deep on a light card,
    // light on a dark one — so the glyph stays legible in both modes.
    val tint = LocalSpliitColors.current.monogramInk(
        MonogramPalette.indexAtPosition(payerPosition),
    )
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(SpliitDimens.TileCorner),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            CategoryIcons.icon(expense.category),
            contentDescription = expense.category?.name,
            modifier = Modifier.size(21.dp),
            tint = tint,
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
