package app.spliit.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.spliit.data.model.ExpenseDetails
import app.spliit.data.model.Participant
import app.spliit.data.model.SplitMode
import app.spliit.domain.CalendarDay
import app.spliit.domain.ExpenseShares
import app.spliit.domain.MoneyFormatter
import app.spliit.ui.theme.CategoryIcons
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.ExpressiveMenu
import app.spliit.ui.theme.ExpressiveMenuItem
import app.spliit.ui.theme.Monogram
import app.spliit.ui.theme.MonogramPalette
import app.spliit.ui.theme.SpliitType
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * One expense, read rather than edited.
 *
 * The list answers "what happened lately"; this answers "what is this one", in the order the
 * questions come: what it was, who paid and how much, how it was split, and what each person
 * carries. Editing is a button, not the default — a mis-tap on a list row used to open a form
 * whose only safe exit was back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    expense: ExpenseDetails?,
    isLoading: Boolean,
    failure: String?,
    formatter: MoneyFormatter,
    actorId: String?,
    participants: List<Participant>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("Expense", style = SpliitType.RowTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                // Two things are done to an expense — changing it and being rid of it — and
                // both are menu items rather than buttons: neither is what the screen is for.
                actions = {
                    if (expense != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "Expense actions",
                                )
                            }
                            ExpressiveMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                ExpressiveMenuItem(
                                    text = "Edit",
                                    leadingIcon = Icons.Filled.Edit,
                                    onClick = {
                                        menuOpen = false
                                        onEdit()
                                    },
                                )
                                ExpressiveMenuItem(
                                    text = "Delete",
                                    leadingIcon = Icons.Filled.Delete,
                                    destructive = true,
                                    onClick = {
                                        menuOpen = false
                                        onDelete()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                expense != null -> Receipt(
                    expense = expense,
                    formatter = formatter,
                    actorId = actorId,
                    participants = participants,
                )

                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                else -> EmptyState(
                    art = EmptyStateArt.Icon(Icons.Filled.Warning),
                    title = "Couldn’t load the expense",
                    description = failure ?: "The server didn’t respond.",
                ) {
                    Button(onClick = onRetry) { Text("Try again") }
                }
            }
        }
    }
}

@Composable
private fun Receipt(
    expense: ExpenseDetails,
    formatter: MoneyFormatter,
    actorId: String?,
    participants: List<Participant>,
) {
    // Exactly the numbers the balances tab charges, from the one definition of a share.
    val shares = remember(expense.id, expense.amount, expense.splitMode) {
        ExpenseShares.shares(expense.id, expense.amount, expense.splitMode, expense.paidFor)
    }
    val payerPosition = participants.indexOfFirst { it.id == expense.paidBy.id }.coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                    Text(
                        text = expense.title,
                        style = SpliitType.Title,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = dateLabel(expense),
                        style = SpliitType.Secondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Overline("Paid by")
                            Spacer(Modifier.size(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Monogram(
                                    name = expense.paidBy.name,
                                    colorIndex = MonogramPalette.indexAtPosition(payerPosition),
                                    size = 28.dp,
                                )
                                Text(
                                    text = expense.paidBy.name,
                                    style = SpliitType.RowTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Overline("Total amount")
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = formatter.string(expense.amount),
                                style = SpliitType.Title,
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Overline("Split type")
                            Spacer(Modifier.size(8.dp))
                            Chip(text = splitLabel(expense.splitMode))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Overline("Category")
                            Spacer(Modifier.size(8.dp))
                            Chip(
                                text = expense.category?.name ?: "Uncategorised",
                                icon = CategoryIcons.icon(expense.category),
                            )
                        }
                    }

                    if (expense.documentCountOrZero() > 0) {
                        Spacer(Modifier.size(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = attachmentsLabel(expense.documentCountOrZero()),
                                style = SpliitType.Secondary,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    expense.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Spacer(Modifier.size(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(Modifier.size(12.dp))
                        Text(text = notes, style = SpliitType.Secondary)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // What the reader of the receipt actually owes, at the foot of it.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Payable",
                        style = SpliitType.RowTitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = actorId?.let { shares[it] }?.let { formatter.string(it) } ?: "—",
                        style = SpliitType.Title,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Participants (${expense.paidFor.size})",
                style = SpliitType.SectionHeader,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )

            expense.paidFor.forEach { share ->
                val participant = participants.firstOrNull { it.id == share.participantId }
                val name = participant?.name ?: "Someone"
                val position = participants.indexOfFirst { it.id == share.participantId }
                    .coerceAtLeast(0)

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Monogram(
                            name = name,
                            colorIndex = MonogramPalette.indexAtPosition(position),
                            size = 36.dp,
                        )
                        Text(
                            text = if (share.participantId == actorId) "$name (You)" else name,
                            style = SpliitType.RowTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = formatter.string(shares[share.participantId] ?: 0),
                            style = SpliitType.RowTitle,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun Overline(text: String) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = SpliitType.Overline,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Chip(text: String, icon: ImageVector? = null) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = text,
                style = SpliitType.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun splitLabel(mode: SplitMode): String = when (mode) {
    SplitMode.EVENLY -> "Equally"
    SplitMode.BY_SHARES -> "By shares"
    SplitMode.BY_PERCENTAGE -> "By percentage"
    SplitMode.BY_AMOUNT -> "By amount"
}

private fun attachmentsLabel(count: Int): String =
    if (count == 1) "1 attachment" else "$count attachments"

/** The documents are optional on the wire; an expense with none has none. */
private fun ExpenseDetails.documentCountOrZero(): Int = documents.size

private fun dateLabel(expense: ExpenseDetails): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(CalendarDay.dayOf(expense.expenseDate))
