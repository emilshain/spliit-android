package app.spliit.ui.expenses

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.spliit.data.model.ExpenseDocument
import app.spliit.ui.theme.SpliitDimens
import app.spliit.ui.theme.SpliitMotion
import app.spliit.ui.theme.SpliitType
import app.spliit.ui.theme.pressScale
import coil.compose.AsyncImage

/**
 * The receipt photographs on an expense, and the button that adds one.
 *
 * This is the same feature the web app has: a photo of the receipt travels with the expense, so
 * everyone splitting it can see what was actually paid for. The thumbnail is the photograph
 * itself — the receipt is the evidence, and a name like "IMG_2041.jpg" is not.
 *
 * The strip grows with its contents on a spatial spring rather than snapping a tile in, so a
 * photo that has just finished uploading settles into the row instead of appearing beside it.
 */
@Composable
fun ReceiptAttachments(
    documents: List<ExpenseDocument>,
    uploading: Boolean,
    failure: String?,
    onAdd: () -> Unit,
    onRemove: (ExpenseDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Receipts",
            style = SpliitType.Overline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onAdd, enabled = !uploading) {
            if (uploading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Uploading…")
            } else {
                Icon(
                    Icons.Filled.AddAPhoto,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Add photo")
            }
        }
    }

    if (documents.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(SpliitMotion.spatialDefault()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            documents.forEach { document ->
                ReceiptThumbnail(document = document, onRemove = { onRemove(document) })
            }
        }
    }

    failure?.let { message ->
        Text(
            text = message,
            style = SpliitType.Secondary,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ReceiptThumbnail(document: ExpenseDocument, onRemove: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(SpliitDimens.TileCorner)

    Box(modifier = Modifier.size(84.dp)) {
        Surface(
            modifier = Modifier.size(84.dp),
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

        // A small circle, not an icon button: the tile is the receipt, and the only thing that
        // may sit on it is the way to take it off.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.inverseSurface)
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = onRemove,
                )
                .pressScale(interaction, pressedScale = 0.9f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove receipt",
                tint = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
