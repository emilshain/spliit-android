package app.spliit.ui.settlements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.spliit.data.model.Participant
import app.spliit.data.model.Reimbursement
import app.spliit.ui.group.GroupScreenModel
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.Monogram
import app.spliit.ui.theme.Money
import app.spliit.ui.theme.MoneySize
import app.spliit.ui.theme.SpliitDimens
import app.spliit.ui.theme.SpliitType

/**
 * The payments that would settle this person up, on a page of their own.
 *
 * The balances tab can only say what the total is; this says who it is with, one card per
 * payment, with the way to record it. It reads the same suggested payments the tab does, so a
 * settlement recorded here disappears from both at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementsScreen(
    model: GroupScreenModel,
    identity: Participant?,
    onBack: () -> Unit,
    onSettle: (Reimbursement) -> Unit,
) {
    val mine = if (identity == null) {
        emptyList()
    } else {
        model.reimbursements.filter { it.from == identity.id || it.to == identity.id }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Column {
                        Text("My settlements", style = SpliitType.RowTitle)
                        if (identity != null && mine.isNotEmpty()) {
                            Text(
                                text = if (mine.size == 1) "1 payment left" else "${mine.size} payments left",
                                style = SpliitType.Secondary,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                identity == null -> EmptyState(
                    art = EmptyStateArt.Icon(Icons.Outlined.CheckCircle),
                    title = "Say who you are",
                    description = "Pick your name on the balances tab.",
                )

                mine.isEmpty() -> EmptyState(
                    art = EmptyStateArt.Icon(Icons.Outlined.CheckCircle),
                    title = "You’re settled up",
                    description = "Nothing is left to pay or collect in this group.",
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(SpliitDimens.ScreenGutter),
                    verticalArrangement = Arrangement.spacedBy(SpliitDimens.CardSpacing),
                ) {
                    items(mine) { reimbursement ->
                        SettlementCard(
                            reimbursement = reimbursement,
                            identity = identity,
                            model = model,
                            onSettle = { onSettle(reimbursement) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettlementCard(
    reimbursement: Reimbursement,
    identity: Participant,
    model: GroupScreenModel,
    onSettle: () -> Unit,
) {
    val from = model.participant(reimbursement.from)
    val to = model.participant(reimbursement.to)
    val youPay = reimbursement.from == identity.id

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SpliitDimens.CardCorner),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(SpliitDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(SpliitDimens.CardSpacing),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Monogram(
                    name = from?.name.orEmpty(),
                    colorIndex = model.participantPosition(reimbursement.from),
                    size = 28.dp,
                )
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 6.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Monogram(
                    name = to?.name.orEmpty(),
                    colorIndex = model.participantPosition(reimbursement.to),
                    size = 28.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (youPay) {
                            "You pay ${to?.name ?: "someone"}"
                        } else {
                            "${from?.name ?: "Someone"} pays you"
                        },
                        style = SpliitType.RowTitle,
                    )
                    Text(
                        text = if (youPay) "You owe this to settle up." else "Owed to you.",
                        style = SpliitType.Secondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Money(
                    value = model.moneyFormatter.string(reimbursement.amount),
                    size = MoneySize.LEAD,
                )
            }

            OutlinedButton(onClick = onSettle) { Text("Mark as paid") }
        }
    }
}
