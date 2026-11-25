package app.spliit.ui.totals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.spliit.data.model.Participant
import app.spliit.domain.CategorySpending
import app.spliit.ui.group.GroupScreenModel
import app.spliit.ui.theme.CategoryIcons
import app.spliit.ui.theme.EmptyState
import app.spliit.ui.theme.EmptyStateArt
import app.spliit.ui.theme.Money
import app.spliit.ui.theme.MoneySize
import app.spliit.ui.theme.SpliitMotion
import app.spliit.ui.theme.SpliitType
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the group has spent, how much of it is yours, and where it went.
 *
 * Three figures, and they are the only three the API can answer: the stats procedure sums the
 * expenses themselves, which is what makes it the one endpoint that knows what anybody actually
 * paid. The balances tab next door looks like it knows and does not — its `paid` and `paidFor`
 * come from the suggested payments, so one of the pair is always zero.
 *
 * Two of the three are a participant's, so the tab is worth one number until somebody says who
 * they are — which is why the invitation to answer is on this screen too, below the total it
 * unlocks rather than in front of it.
 */
@Composable
fun TotalsTab(
    model: GroupScreenModel,
    identity: Participant?,
    identityIsNobody: Boolean,
    onIdentify: () -> Unit,
) {
    // Lazy, and keyed on who is asking: the tab's request is another one on a screen that already
    // makes several, and two of the three numbers are the participant's.
    LaunchedEffect(model.group?.id, identity?.id) {
        if (model.group == null) return@LaunchedEffect
        model.loadStats(identity?.id)
    }
    // Its own effect, keyed on the group alone: what the group spent on food does not change with
    // who is asking, so this must not be re-swept every time somebody answers the picker.
    LaunchedEffect(model.group?.id) {
        if (model.group == null) return@LaunchedEffect
        model.loadCategorySpending()
    }

    when {
        model.statsUnavailable -> EmptyState(
            art = EmptyStateArt.Icon(Icons.Filled.BarChart),
            title = "No totals on this server",
            description = "This Spliit instance doesn’t offer them. Everything else in the group works as usual.",
        )

        model.stats == null && (model.isLoadingGroup || !model.didFailToLoadStats) -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        model.didFailToLoad || model.didFailToLoadStats -> EmptyState(
            art = EmptyStateArt.Icon(Icons.Filled.WifiOff),
            title = if (model.didFailToLoad) {
                "Couldn’t load this group"
            } else {
                "Couldn’t load the totals"
            },
            description = model.statsFailure ?: model.loadFailure ?: "The server didn’t respond.",
        ) {
            // Whichever one failed is the one to try again.
            Button(
                onClick = {
                    if (model.didFailToLoad) {
                        model.retry()
                    } else {
                        model.refreshStatsNow(identity?.id)
                    }
                },
            ) {
                Text("Try again")
            }
        }

        else -> TotalsContent(
            model = model,
            identity = identity,
            identityIsNobody = identityIsNobody,
            onIdentify = onIdentify,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TotalsContent(
    model: GroupScreenModel,
    identity: Participant?,
    identityIsNobody: Boolean,
    onIdentify: () -> Unit,
) {
    val stats = model.stats ?: return
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                try {
                    model.refreshStats(identity?.id)
                    model.refreshCategorySpending()
                } finally {
                    refreshing = false
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "group") {
                SectionHeader("The group")
                TotalsCard {
                    TotalFigure(
                        label = if (stats.totalGroupSpendings < 0) {
                            "Total group earnings"
                        } else {
                            "Total group spending"
                        },
                        minorUnits = stats.totalGroupSpendings,
                        size = MoneySize.HERO,
                        formatter = model.moneyFormatter,
                    )
                }
                SectionFooter(
                    "Settling up is not spending, so reimbursements are left out of every " +
                        "figure here.",
                )
            }

            item(key = "you") {
                SectionHeader("You")
                val spendings = stats.totalParticipantSpendings
                val share = stats.totalParticipantShare?.takeIf { it.isFinite() }?.roundToInt()

                TotalsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (identity != null && spendings != null) {
                            TotalFigure(
                                label = if (spendings < 0) "Your earnings" else "Your spending",
                                minorUnits = spendings,
                                size = MoneySize.LEAD,
                                formatter = model.moneyFormatter,
                                fraction = groupSlice(spendings, stats.totalGroupSpendings),
                            )
                        }
                        if (identity != null && share != null) {
                            TotalFigure(
                                label = "Your share",
                                minorUnits = share,
                                size = MoneySize.LEAD,
                                formatter = model.moneyFormatter,
                                fraction = groupSlice(share, stats.totalGroupSpendings),
                            )
                        }
                        if (identity == null) {
                            Text(
                                text = "Say who you are and this card also says what you have " +
                                    "paid, and what your share of the group came to.",
                                style = SpliitType.Secondary,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IdentityRow(identity, identityIsNobody, onIdentify)
                    }
                }
                SectionFooter(
                    if (identity == null) {
                        "Pick yourself from the card above; the totals are already here."
                    } else {
                        "What you paid, against what was spent on you. The difference is your balance."
                    },
                )
            }

            if (model.categorySpendingReconciles) {
                item(key = "categories-header") { SectionHeader("By category") }
                model.categorySpending.forEach { spending ->
                    item(key = "category-${spending.categoryId}") {
                        TotalsCard {
                            CategoryRow(
                                spending = spending,
                                groupTotal = stats.totalGroupSpendings,
                                formatter = model.moneyFormatter,
                            )
                        }
                    }
                }
                item(key = "categories-footer") {
                    SectionFooter(
                        "Every bar on this screen is a slice of the same total, so the lengths " +
                            "compare straight down the page.",
                    )
                }
            } else if (model.isLoadingCategorySpending) {
                // A placeholder rather than nothing: this sweep reads every expense in the group,
                // so it can land after the three figures above, and a section appearing
                // unannounced moves the page under a thumb already on it.
                item(key = "categories-loading") {
                    SectionHeader("By category")
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            } else if (model.categorySpendingFailure != null) {
                item(key = "categories-failure") {
                    SectionHeader("By category")
                    Text(
                        text = model.categorySpendingFailure.orEmpty(),
                        style = SpliitType.Secondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }

            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** One block of figures, on a card like the expenses and the balances use. */
@Composable
private fun TotalsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(modifier = Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun TotalFigure(
    label: String,
    minorUnits: Int,
    size: MoneySize,
    formatter: app.spliit.domain.MoneyFormatter,
    fraction: Double? = null,
) {
    // Unsigned, like the balance headline next door and for the same reason: the caption above
    // already says which way it goes, and "Total group earnings −$40.00" says it twice while
    // contradicting itself.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = SpliitType.Secondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Money(value = formatter.string(abs(minorUnits)), size = size)
        if (fraction != null) {
            val percent = (fraction * 100).roundToInt()
            SliceBar(fraction.toFloat())
            Text(
                text = "$percent% of the group",
                style = SpliitType.Secondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IdentityRow(identity: Participant?, identityIsNobody: Boolean, onIdentify: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("You are", style = SpliitType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** One category, its spend, and how much of the group that is. */
@Composable
private fun CategoryRow(
    spending: CategorySpending,
    groupTotal: Int,
    formatter: app.spliit.domain.MoneyFormatter,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                CategoryIcons.icon(
                    app.spliit.data.model.ExpenseCategory(
                        id = spending.categoryId,
                        grouping = spending.grouping,
                        name = spending.name,
                    ),
                ),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = spending.name,
                    style = SpliitType.RowTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                // Signed, unlike the three figures above: nothing here says which way a category
                // went, so the minus has to.
                Text(
                    text = formatter.string(spending.total),
                    style = SpliitType.RowTitle,
                )
            }
            groupSlice(spending.total, groupTotal)?.let { SliceBar(it.toFloat()) }
        }
    }
}

/**
 * A slice of the group's spending.
 *
 * One denominator on this screen: the two personal figures and every category bar are all a
 * slice of the group's total, which is what lets their lengths be compared straight down the
 * page. Clamped, because neither end is impossible — a group whose expenses net out below what
 * one person paid would put a bar past its own track, and a category that came to a refund would
 * put one behind the start of it.
 */
@Composable
private fun SliceBar(fraction: Float) {
    // The bar grows to its length instead of appearing at it: the figures above are read first,
    // and a bar that arrives with them reads as the same fact told twice rather than a second one.
    val target = fraction.coerceIn(0f, 1f)
    val grown by animateFloatAsState(
        targetValue = target,
        animationSpec = SpliitMotion.spatialFast(),
        label = "slice",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)),
    ) {
        if (grown > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(grown)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
            )
        }
    }
}

/** How much of the group a figure is; null when the question has no answer. */
private fun groupSlice(value: Int, groupTotal: Int): Double? {
    if (groupTotal <= 0) return null
    return (value.toDouble() / groupTotal).coerceIn(0.0, 1.0)
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
