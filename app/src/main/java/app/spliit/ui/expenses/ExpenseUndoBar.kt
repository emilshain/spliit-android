package app.spliit.ui.expenses

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.spliit.ui.group.GroupScreenModel
import app.spliit.ui.theme.SpliitMotion
import app.spliit.ui.theme.SpliitType

/**
 * A deleted expense should leave with a way back, rather than simply vanish.
 *
 * Shared by the expenses list and by search results, because a delete from either starts the same
 * undo window on the same model.
 *
 * The bar slides up from the bottom edge and back down when the window closes — but the model
 * empties `pendingDeletion` the instant the window closes, so the bar holds onto the last one it
 * was shown. Otherwise it would have nothing to slide out with and would simply blink away.
 */
@Composable
fun ExpenseUndoBar(
    model: GroupScreenModel,
    modifier: Modifier = Modifier,
) {
    val pending = model.pendingDeletion
    var lastShown by remember { mutableStateOf(pending) }
    if (pending != null) lastShown = pending

    AnimatedVisibility(
        visible = pending != null,
        enter = slideInVertically(SpliitMotion.spatialDefault()) { height -> height } +
            fadeIn(SpliitMotion.effectsDefault()),
        exit = slideOutVertically(SpliitMotion.spatialFast()) { height -> height } +
            fadeOut(SpliitMotion.effectsFast()),
        modifier = modifier,
    ) {
        val shown = lastShown ?: return@AnimatedVisibility
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "“${shown.expense.title}” deleted",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = SpliitType.Secondary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { model.undoDelete() }) { Text("Undo") }
            }
        }
    }
}
