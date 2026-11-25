package app.spliit.ui.balances

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import app.spliit.data.model.Balance
import app.spliit.data.model.Group
import app.spliit.data.model.Participant
import app.spliit.data.model.Reimbursement
import app.spliit.ui.group.GroupScreenModel
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.LocalSpliitColors
import app.spliit.ui.theme.Monogram
import app.spliit.ui.theme.Money
import app.spliit.ui.theme.MoneySign
import app.spliit.ui.theme.MoneySize
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
        model.isLoadingGroup || model.isLoadingBalances -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

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
            // Clear of the floating tab bar, which the list scrolls behind.
            contentPadding = PaddingValues(top = 60.dp),
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
        item(key = "balances-card") {
            BalancesCard(
                participants = group.participants,
                balances = model.balances,
                largest = largest,
                identityId = identity?.id,
                formatter = model.moneyFormatter,
            )
        }
        item(key = "settle-header") {
            SuggestedHeader(
                expanded = suggestedOpen,
                remaining = model.reimbursements.size,
                onToggle = { suggestedOpen = !suggestedOpen },
            )
        }
        if (suggestedOpen) {
            if (model.reimbursements.isEmpty()) {
                item(key = "settled") {
                    Text(
                        text = "Everyone is settled up.",
                        style = SpliitType.Secondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            } else {
                reimbursementItems(model, group, onSettle)
            }
        }

        item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
    }
}

/** A `items` overload for reimbursements, which have no identity of their own beyond position. */
private fun androidx.compose.foundation.lazy.LazyListScope.reimbursementItems(
    model: GroupScreenModel,
    group: Group,
    onSettle: (Reimbursement) -> Unit,
) {
    items(model.reimbursements) { reimbursement ->
        ReimbursementCard(
            reimbursement = reimbursement,
            from = model.participant(reimbursement.from),
            fromPosition = model.participantPosition(reimbursement.from),
            to = model.participant(reimbursement.to),
            toPosition = model.participantPosition(reimbursement.to),
            amount = model.moneyFormatter.string(reimbursement.amount),
            onSettle = { onSettle(reimbursement) },
        )
    }
}

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
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
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

/**
 * Everyone's balance, in one card: a row per person, split by hairlines, so the set reads as a
 * single ledger rather than a pile of separate cards.
 */
@Composable
private fun BalancesCard(
    participants: List<Participant>,
    balances: Map<String, Balance>,
    largest: Int,
    identityId: String?,
    formatter: app.spliit.domain.MoneyFormatter,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            participants.forEachIndexed { index, participant ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 50.dp, end = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                BalanceRow(
                    participant = participant,
                    position = index,
                    balance = balances[participant.id] ?: Balance(0, 0, 0),
                    largest = largest,
                    isYou = identityId == participant.id,
                    formatter = formatter,
                )
            }
        }
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
        verticalAlignment = Alignment.Top,
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
                        color = MaterialTheme.colorScheme.surfaceVariant,
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

/** "Suggested payments", and the whole section folded into it. */
@Composable
private fun SuggestedHeader(expanded: Boolean, remaining: Int, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
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
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowUp
                } else {
                    Icons.Filled.KeyboardArrowDown
                },
                contentDescription = if (expanded) {
                    "Collapse suggested payments"
                } else {
                    "Expand suggested payments"
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onSettle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
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

