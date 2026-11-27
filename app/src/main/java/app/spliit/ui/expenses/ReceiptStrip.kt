package app.spliit.ui.expenses

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.spliit.data.model.ExpenseDocument
import app.spliit.ui.theme.SpliitDimens
import app.spliit.ui.theme.pressScale
import coil.compose.AsyncImage

/**
 * The receipts an expense carries, as the photographs themselves.
 *
 * A count would say "1 attachment" and leave the reader with nothing to look at; the point of a
 * receipt is that it can be read. Tapping one opens it at the size of the screen, where the
 * numbers on it can actually be checked.
 */
@Composable
fun ReceiptStrip(
    documents: List<ExpenseDocument>,
    onOpen: (ExpenseDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        documents.forEach { document ->
            ReceiptTile(document = document, onClick = { onOpen(document) })
        }
    }
}

@Composable
fun ReceiptPreview(document: ExpenseDocument, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SpliitDimens.CardCorner),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            AsyncImage(
                model = document.url,
                contentDescription = "Receipt photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SpliitDimens.CardCorner))
                    .clickable {
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun ReceiptTile(document: ExpenseDocument, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(SpliitDimens.TileCorner)
    Surface(
        modifier = Modifier
            .size(76.dp)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .pressScale(interaction, pressedScale = 0.94f),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        AsyncImage(
            model = document.url,
            contentDescription = "Receipt photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.clip(shape),
        )
    }
}
