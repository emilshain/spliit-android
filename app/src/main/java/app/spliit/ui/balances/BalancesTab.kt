package app.spliit.ui.balances

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
 */
@Composable
fun BalancesTab(
    model: GroupScreenModel,
    identity: Participant?,
    identityIsNobody: Boolean,
    onIdentify: () -> Unit,
    onSettle: (Reimbursement) -> Unit,
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
) {
    val largest = maxOf(model.balances.values.maxOfOrNull { abs(it.total) } ?: 0, 1)

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "you-header") {
            SectionHeader("You")
            if (identity != null) {
                YouSummary(model.balances[identity.id] ?: Balance(0, 0, 0), model)
            }
            IdentityRow(identity, identityIsNobody, onIdentify)
            if (identity == null) {
                SectionFooter(
                    "Pick yourself once and this group is read from where you stand: your own " +
                        "balance first, and your name already filled in on a new expense.",
                )
            }
        }

        item(key = "balances-header") { SectionHeader("Balances") }
        items(group.participants, key = { it.id }) { participant ->
            BalanceRow(
                participant = participant,
                position = group.participants.indexOfFirst { it.id == participant.id },
                balance = model.balances[participant.id] ?: Balance(0, 0, 0),
                largest = largest,
                isYou = identity?.id == participant.id,
                formatter = model.moneyFormatter,
            )
        }
        item(key = "balances-footer") {
            SectionFooter("Who is up and who is down, once every expense has been counted.")
        }

        item(key = "settle-header") { SectionHeader("Suggested payments") }
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
}

/** A `items` overload for reimbursements, which have no identity of their own beyond position. */
private fun androidx.compose.foundation.lazy.LazyListScope.reimbursementItems(
    model: GroupScreenModel,
    group: Group,
    onSettle: (Reimbursement) -> Unit,
) {
    items(model.reimbursements) { reimbursement ->
        ReimbursementRow(
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

@Composable
private fun YouSummary(balance: Balance, model: GroupScreenModel) {
    val direction = when {
        balance.total > 0 -> "You are owed"
        balance.total < 0 -> "You owe"
        else -> "You’re settled up"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = direction,
            style = SpliitType.Secondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Money(
            value = model.moneyFormatter.string(abs(balance.total)),
            size = MoneySize.HERO,
            sign = MoneySign.of(balance.total),
        )
    }
}

@Composable
private fun IdentityRow(identity: Participant?, identityIsNobody: Boolean, onIdentify: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("You", style = SpliitType.RowTitle)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onIdentify) {
            Text(
                when {
                    identity != null -> identity.name
                    identityIsNobody -> "Nobody"
                    else -> "Say who you are"
                },
            )
        }
    }
}

/**
 * A diverging bar: owed to the right of centre, owing to the left.
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
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

@Composable
private fun ReimbursementRow(
    reimbursement: Reimbursement,
    from: Participant?,
    fromPosition: Int,
    to: Participant?,
    toPosition: Int,
    amount: String,
    onSettle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = SpliitType.SectionHeader,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionFooter(text: String) {
    Text(
        text = text,
        style = SpliitType.Secondary,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}
