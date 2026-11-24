package app.spliit.ui.expenses

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.spliit.ui.group.GroupScreenModel
import app.spliit.ui.theme.SpliitType

/**
 * A deleted expense should leave with a way back, rather than simply vanish.
 *
 * Shared by the expenses list and by search results, because a delete from either starts the same
 * undo window on the same model.
 */
@Composable
fun ExpenseUndoBar(model: GroupScreenModel, modifier: Modifier = Modifier) {
    val pending = model.pendingDeletion ?: return
    Surface(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "“${pending.expense.title}” deleted",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = SpliitType.Secondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { model.undoDelete() }) { Text("Undo") }
        }
    }
}
