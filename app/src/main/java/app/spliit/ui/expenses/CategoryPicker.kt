package app.spliit.ui.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.spliit.data.model.ExpenseCategory
import app.spliit.ui.theme.CategoryIcons
import app.spliit.ui.theme.ExpressiveMenu
import app.spliit.ui.theme.ExpressiveMenuItem
import app.spliit.ui.theme.ExpandChevron
import app.spliit.ui.theme.SpliitType

/**
 * Choosing what an expense was for.
 *
 * The two halves are picked the way they are read: the heading is a visible row of chips, so the
 * whole shape of the taxonomy is on screen and moving between headings is one tap rather than
 * opening a menu, and the category under it is a menu, because a heading can hold a dozen and a
 * list is the right control for "one of many". Both show the category's own glyph, so a pizza or
 * a taxi is recognised before its name is read.
 *
 * Selecting a heading commits its first category straight away: a category is what the server
 * stores, so there is always one chosen, and every heading has a default without anybody having
 * to know which name it is.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryPicker(
    categories: List<ExpenseCategory>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (categories.isEmpty()) return

    val grouped = remember(categories) { categories.groupBy { it.grouping } }
    val selected = categories.firstOrNull { it.id == selectedId }
    val grouping = selected?.grouping ?: grouped.keys.first()
    val entries = grouped[grouping].orEmpty()

    var menuOpen by remember { mutableStateOf(false) }

    // A category to start from: the first under the heading, when the expense has none this
    // instance knows.
    LaunchedEffect(categories, selectedId) {
        if (selected == null) entries.firstOrNull()?.let { onSelect(it.id) }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Category",
            style = SpliitType.Secondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            grouped.forEach { (name, list) ->
                FilterChip(
                    selected = name == grouping,
                    onClick = {
                        menuOpen = false
                        list.firstOrNull()?.let { onSelect(it.id) }
                    },
                    // The heading carries the glyph of what it starts with, which is the same
                    // glyph the chosen category will wear once the picker closes.
                    leadingIcon = {
                        Icon(
                            imageVector = CategoryIcons.icon(list.firstOrNull()),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    label = { Text(name) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { menuOpen = true }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Subcategory", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = CategoryIcons.icon(selected ?: entries.firstOrNull()),
                contentDescription = null,
                modifier = Modifier.size(18.dp).padding(end = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = (selected ?: entries.firstOrNull())?.name.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
            ExpandChevron(expanded = menuOpen)
        }

        ExpressiveMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            entries.forEach { category ->
                ExpressiveMenuItem(
                    text = category.name,
                    leadingIcon = CategoryIcons.icon(category),
                    selected = category.id == selectedId,
                    onClick = {
                        menuOpen = false
                        onSelect(category.id)
                    },
                )
            }
        }
    }
}
