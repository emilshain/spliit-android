package app.spliit.ui.group

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.spliit.ui.theme.SpliitDimens
import app.spliit.ui.theme.SpliitType
import kotlinx.coroutines.delay

/**
 * The group's link, handed to whoever is going to spend with it.
 *
 * The web app puts this in a popover off the toolbar; here it is a sheet, because the link is
 * long enough to want reading and the two things done with it — copy or send — are the sheet's
 * own buttons. What is shared is the same link the web app hands out, so a code, a paste or a
 * forwarded message all land on the same group.
 *
 * There is no invitation and no membership: the link is the access. That is why the warning is
 * part of this, not a footnote elsewhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupShareSheet(
    groupName: String,
    url: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var didCopy by remember { mutableStateOf(false) }

    // The confirmation is a state of the button, not a toast: a toast would outlive the sheet and
    // cover the very button it is talking about.
    LaunchedEffect(didCopy) {
        if (didCopy) {
            delay(2000)
            didCopy = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Share group", style = SpliitType.RowTitle)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SpliitDimens.TileCorner),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                SelectionContainer {
                    Text(
                        text = url,
                        style = SpliitType.Secondary,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = {
                        copyLink(context, url)
                        didCopy = true
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        if (didCopy) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (didCopy) "Copied" else "Copy link")
                }

                Button(
                    onClick = { shareLink(context, groupName, url) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Share link")
                }
            }
        }
    }
}

private fun copyLink(context: Context, url: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Spliit group link", url))
}

/**
 * Android's own chooser, because the app the link goes to should be the one the person already
 * sends things with rather than a list this app keeps.
 */
private fun shareLink(context: Context, groupName: String, url: String) {
    val message = "Join my group $groupName on Spliit\n$url"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
        putExtra(Intent.EXTRA_SUBJECT, groupName)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share group")) }
}
