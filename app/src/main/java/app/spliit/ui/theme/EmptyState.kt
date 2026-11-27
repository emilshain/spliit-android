package app.spliit.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.spliit.R

/** The art above an empty state's title. */
sealed interface EmptyStateArt {
    /** The brand mark, for welcome and first-run — the places where naming the app is the point. */
    data object Logo : EmptyStateArt

    /** An icon in a tile tinted with the accent. */
    data class Icon(val vector: ImageVector) : EmptyStateArt
}

/**
 * The screen with nothing on it.
 *
 * Centred when it fits, scrollable when it doesn't: at the largest text sizes a title, a
 * description and a stack of buttons are taller than the screen, and an empty state whose only
 * action has fallen off the bottom is worse than no empty state at all.
 */
@Composable
fun EmptyState(
    art: EmptyStateArt,
    title: String,
    description: String? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Artwork(art)

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = SpliitType.Title,
                    textAlign = TextAlign.Center,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 320.dp),
                    )
                }
            }

            Box(modifier = Modifier.padding(top = 4.dp)) { actions() }
        }
    }
}

@Composable
private fun Artwork(art: EmptyStateArt) {
    when (art) {
        // The mark itself, from the same artwork as the launcher icon but without its plate:
        // tinted with the theme's accent so it reads in light mode and in dark, where the
        // artwork's own green would sit too bright on the page. Fixed size, as on iOS: chrome
        // should not grow with Dynamic Type.
        EmptyStateArt.Logo -> Image(
            painter = painterResource(R.drawable.ic_mark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(96.dp),
        )

        is EmptyStateArt.Icon -> Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    LocalSpliitColors.current.brandAccentSoft,
                    RoundedCornerShape(SpliitDimens.TileCorner),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = art.vector,
                contentDescription = null,
                tint = LocalSpliitColors.current.brandOnAccentSoft,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}
