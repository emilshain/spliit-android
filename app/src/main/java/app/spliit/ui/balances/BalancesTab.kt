package app.spliit.ui.balances

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.spliit.data.model.Balance
import app.spliit.data.model.Group
import app.spliit.data.model.Participant
import app.spliit.data.model.Reimbursement
import app.spliit.ui.group.GroupScreenModel
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.ExpandChevron
import app.spliit.ui.theme.SpliitDimens
import app.spliit.ui.theme.SpliitMotion
import app.spliit.ui.theme.LocalSpliitColors
import app.spliit.ui.theme.Money
import app.spliit.ui.theme.MoneySign
import app.spliit.ui.theme.MoneySize
import app.spliit.ui.theme.Monogram
import app.spliit.ui.theme.SkeletonCard
import app.spliit.ui.theme.SkeletonRow
import app.spliit.ui.theme.SkeletonSection
import app.spliit.ui.theme.SpliitType
import kotlin.math.abs

/**
 * Who is up and who is down, and the shortest set of payments that settles it.
 *
 * One card per person and one per payment, opening on the reader's own balance: that is the
 * figure they came for, and it leads to the page that says who it is with.
 */
@Composable
fun BalancesTab(
    model: GroupScreenModel,
    identity: Participant?,
    identityIsNobody: Boolean,
    onIdentify: () -> Unit,
    onSettle: (Reimbursement) -> Unit,
    onOpenSettlements: () -> Unit,
) {
    val group = model.group

    when {
        model.isLoadingGroup || model.isLoadingBalances -> BalancesSkeleton()

        model.didFailToLoad || model.didFailToLoadBalances -> EmptyState(
            art = EmptyStateArt.Icon(Icons.Filled.WifiOff),
            title = if (model.didFailToLoad) {
                "Couldn’t load this group"
            } else {
                "Couldn’t load the balances"
            },
            description = model.loadFailure ?: "The server didn’t respond.",
        ) {
            Button(onClick = { model.retry() }) { Text("Try again") }
        }

        group == null -> Unit

        else -> BalancesContent(
            model = model,
            group = group,
            identity = identity,
            identityIsNobody = identityIsNobody,
            onIdentify = onIdentify,
            onSettle = onSettle,
            onOpenSettlements = onOpenSettlements,
        )
    }
}

@Composable
private fun BalancesContent(
    model: GroupScreenModel,
    group: Group,
    identity: Participant?,
    identityIsNobody: Boolean,
    onIdentify: () -> Unit,
    onSettle: (Reimbursement) -> Unit,
    onOpenSettlements: () -> Unit,
) {
    val largest = maxOf(model.balances.values.maxOfOrNull { abs(it.total) } ?: 0, 1)
    var suggestedOpen by remember { mutableStateOf(false) }

    LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
        item(key = "you-header") { SectionHeader("You") }
        item(key = "you-card") {
            YouCard(
                balance = identity?.let { model.balances[it.id] } ?: Balance(0, 0, 0),
                hasIdentity = identity != null,
                model = model,
                onClick = if (identity != null) onOpenSettlements else onIdentify,
            )
        }
        item(key = "balances-header") { SectionHeader("Balances") }
        // One card per person, stacked like the home list: a shared side takes the small
        // corner, so a section reads as one group of cards instead of a ledger of hairlines.
        itemsIndexed(
            items = group.participants,
            key = { _, participant -> "balance-${participant.id}" },
        ) { index, participant ->
            BalanceCard(
                participant = participant,
                position = index,
                balance = model.balances[participant.id] ?: Balance(0, 0, 0),
                largest = largest,
                isYou = identity?.id == participant.id,
                formatter = model.moneyFormatter,
                first = index == 0,
                last = index == group.participants.lastIndex,
            )
        }
        // One card, header and rows together: the button and what it opens are the same
        // container, so the list it unfolds cannot read as a separate section that happens to
        // start where the button ends.
        item(key = "suggested") {
            SuggestedPaymentsCard(
                expanded = suggestedOpen,
                remaining = model.reimbursements.size,
                onToggle = { suggestedOpen = !suggestedOpen },
                model = model,
                group = group,
                onSettle = onSettle,
            )
        }

        item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * "Suggested payments" and the payments it suggests, in one card.
 *
 * The header is the card's first row and the payments are its body, so opening it grows the card
 * instead of appending cards that merely sit near it. The rows inside take the card's own width
 * minus a small inset, which is the grouped-list shape the sections above already use.
 */
@Composable
private fun SuggestedPaymentsCard(
    expanded: Boolean,
    remaining: Int,
    onToggle: () -> Unit,
    model: GroupScreenModel,
    group: Group,
    onSettle: (Reimbursement) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpliitDimens.ScreenGutter)
            .padding(top = 4.dp, bottom = 2.dp),
        shape = RoundedCornerShape(SpliitDimens.CardCorner),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = SpliitDimens.CardPadding, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Suggested payments", style = SpliitType.RowTitle)
                    Text(
                        text = if (remaining == 0) {
                            "Nothing left to pay"
                        } else if (remaining == 1) {
                            "1 payment would settle everyone"
                        } else {
                            "$remaining payments would settle everyone"
                        },
                        style = SpliitType.Secondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ExpandChevron(expanded = expanded)
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(SpliitMotion.spatialDefault()) +
                    fadeIn(SpliitMotion.effectsDefault()),
                exit = shrinkVertically(SpliitMotion.spatialDefault()) +
                    fadeOut(SpliitMotion.effectsFast()),
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(SpliitDimens.CardGap),
                ) {
                    if (model.reimbursements.isEmpty()) {
                        Text(
                            text = "Everyone is settled up.",
                            style = SpliitType.Secondary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                    model.reimbursements.forEachIndexed { index, reimbursement ->
                        ReimbursementCard(
                            reimbursement = reimbursement,
                            from = model.participant(reimbursement.from),
                            fromPosition = model.participantPosition(reimbursement.from),
                            to = model.participant(reimbursement.to),
                            toPosition = model.participantPosition(reimbursement.to),
                            amount = model.moneyFormatter.string(reimbursement.amount),
                            shape = stackedShape(
                                first = index == 0,
                                last = index == model.reimbursements.lastIndex,
                            ),
                            horizontalPadding = 0.dp,
                            onSettle = { onSettle(reimbursement) },
                        )
                    }
                }
            }
        }
    }
}

/** The two corners a stacked card owns, and the two it shares with the card beside it. */
private fun stackedShape(first: Boolean, last: Boolean): RoundedCornerShape = RoundedCornerShape(
    topStart = if (first) CardCorner else AdjacentCorner,
    topEnd = if (first) CardCorner else AdjacentCorner,
    bottomStart = if (last) CardCorner else AdjacentCorner,
    bottomEnd = if (last) CardCorner else AdjacentCorner,
)

private val CardCorner = 22.dp
private val AdjacentCorner = 10.dp

/**
 * The reader's own balance, and the way into the payments that would settle it.
 *
 * The chevron is the promise the card makes: there is a page behind it, one payment per card,
 * which is where acting on this number actually happens.
 */
@Composable
private fun YouCard(
    balance: Balance,
    hasIdentity: Boolean,
    model: GroupScreenModel,
    onClick: () -> Unit,
) {
    val direction = when {
        !hasIdentity -> "Your balance"
        balance.total > 0 -> "You are owed"
        balance.total < 0 -> "You owe"
        else -> "You’re settled up"
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(CardCorner),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = direction,
                    style = SpliitType.Secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasIdentity) {
                    Money(
                        value = model.moneyFormatter.string(abs(balance.total)),
                        size = MoneySize.HERO,
                        sign = MoneySign.of(balance.total),
                    )
                    Text(
                        text = if (balance.total == 0) {
                            "Nothing left to settle"
                        } else {
                            "See your settlements"
                        },
                        style = SpliitType.Secondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Say who you are to see it",
                        style = SpliitType.RowTitle,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "My settlements",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One person's card: their line in the balances, on a surface of their own. */
@Composable
private fun BalanceCard(
    participant: Participant,
    position: Int,
    balance: Balance,
    largest: Int,
    isYou: Boolean,
    formatter: app.spliit.domain.MoneyFormatter,
    first: Boolean,
    last: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = if (first) 4.dp else 0.dp, bottom = 2.dp),
        shape = stackedShape(first = first, last = last),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        BalanceRow(
            participant = participant,
            position = position,
            balance = balance,
            largest = largest,
            isYou = isYou,
            formatter = formatter,
        )
    }
}

/**
 * One person's line in the balances card: a diverging bar, owed to the right of centre and owing
 * to the left.
 */
@Composable
private fun BalanceRow(
    participant: Participant,
    position: Int,
    balance: Balance,
    largest: Int,
    isYou: Boolean,
    formatter: app.spliit.domain.MoneyFormatter,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        // Centred rather than top-aligned: the name and the bar make the row taller than the
        // monogram, and an avatar pinned to the top leaves a strip of empty card underneath it.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(name = participant.name, colorIndex = position, size = 26.dp)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = participant.name, style = SpliitType.RowTitle)
                if (isYou) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = "You",
                            style = SpliitType.Secondary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Money(
                    value = formatter.string(balance.total),
                    sign = MoneySign.of(balance.total),
                )
            }

            DivergingBar(fraction = abs(balance.total).toFloat() / largest, total = balance.total)
        }
    }
}

@Composable
private fun DivergingBar(fraction: Float, total: Int) {
    val tint = when {
        total > 0 -> LocalSpliitColors.current.moneyPositive
        total < 0 -> LocalSpliitColors.current.moneyNegative
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(modifier = Modifier.fillMaxWidth().height(8.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        if (total != 0) {
            Box(
                modifier = Modifier
                    .align(if (total > 0) Alignment.CenterEnd else Alignment.CenterStart)
                    // Half the width is one direction's whole budget; the bar's length is the
                    // share of the biggest balance in the group.
                    .fillMaxWidth(fraction = (fraction * 0.5f).coerceIn(0.01f, 0.5f))
                    .fillMaxHeight()
                    .background(tint, RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
private fun ReimbursementCard(
    reimbursement: Reimbursement,
    from: Participant?,
    fromPosition: Int,
    to: Participant?,
    toPosition: Int,
    amount: String,
    shape: RoundedCornerShape,
    horizontalPadding: Dp = SpliitDimens.ScreenGutter,
    onSettle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(top = 4.dp, bottom = 2.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Monogram(name = from?.name.orEmpty(), colorIndex = fromPosition, size = 24.dp)
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 4.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Monogram(name = to?.name.orEmpty(), colorIndex = toPosition, size = 24.dp)

                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${from?.name ?: "—"} owes ${to?.name ?: "—"}",
                    modifier = Modifier.weight(1f),
                )
                Money(value = amount, size = MoneySize.LEAD)
            }

            OutlinedButton(onClick = onSettle) { Text("Mark as paid") }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = SpliitType.SectionHeader,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
    )
}


/** Your balance card, then the ledger, in the shape they will have. */
@Composable
private fun BalancesSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SkeletonCard(height = 132.dp, shape = RoundedCornerShape(24.dp))
        SkeletonSection(modifier = Modifier.padding(top = 10.dp))
        repeat(3) {
            SkeletonRow(trailingWidth = 64.dp)
        }
    }
}
