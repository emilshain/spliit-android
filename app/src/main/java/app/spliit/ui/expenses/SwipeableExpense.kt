package app.spliit.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.spliit.ui.theme.ExpressiveSwipe
import app.spliit.ui.theme.SwipeAction
import app.spliit.ui.theme.pressScale

/**
 * A row that deletes from the trailing edge.
 *
 * The row itself is not dismissed: the store removes it, and the list re-renders — which keeps
 * the gesture and the undo window in one place.
 */
@Composable
fun SwipeableExpense(
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)

    ExpressiveSwipe(
        modifier = modifier,
        shape = shape,
        endToStart = SwipeAction(
            icon = Icons.Filled.Delete,
            label = "Delete",
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            collapses = true,
            onAction = onDelete,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                )
                .pressScale(interaction, pressedScale = 0.98f),
        ) {
            content()
        }
    }
}
