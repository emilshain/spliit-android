package app.spliit.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Skeleton loading.
 *
 * A page that is fetching draws the shape of the page it is about to be: the cards, rows and
 * lines the data will fill, in a neutral tint with a slow sheen crossing them. That reads as
 * "this is loading" while also saying what is loading, which a spinner in the middle of an empty
 * screen cannot. A spinner is still right in the two places where something small is happening
 * inside a page that is already drawn: a button that is saving, and the footer that is paging.
 *
 * The tint is the text colour at a low alpha, so it follows the theme into dark mode without a
 * second palette.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val sheen = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val progress by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeleton sheen",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                drawRect(base)
                // A soft band crossing left to right, wider than the block so it never sits
                // still at the edges.
                val band = size.width * 0.7f
                val start = -band + (size.width + band) * progress
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, sheen, Color.Transparent),
                        start = Offset(start, 0f),
                        end = Offset(start + band, 0f),
                    ),
                )
            },
    )
}

/**
 * One line of fake text. [widthFraction] of the space it is given; the real line is usually a
 * little shorter than the line above it, so the placeholder should be too.
 */
@Composable
fun SkeletonLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: Dp = 12.dp,
) {
    SkeletonBox(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        shape = RoundedCornerShape(50),
    )
}

/** A monogram, glyph or avatar placeholder. */
@Composable
fun SkeletonCircle(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    SkeletonBox(modifier = modifier.size(size), shape = RoundedCornerShape(50))
}

/**
 * A card placeholder: the surface a block of content sits on, with no shadow, exactly like the
 * card it replaces.
 */
@Composable
fun SkeletonCard(
    height: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            shape = shape,
        )
    }
}

/**
 * A list row: leading block, two lines, trailing amount. Sits on its own card, so a list of
 * these already looks like the list that is coming.
 */
@Composable
fun SkeletonRow(
    modifier: Modifier = Modifier,
    leadingSize: Dp = 38.dp,
    trailingWidth: Dp = 52.dp,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonCircle(size = leadingSize)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeletonLine(widthFraction = 0.55f, height = 13.dp)
                SkeletonLine(widthFraction = 0.35f, height = 10.dp)
            }
            SkeletonBox(
                modifier = Modifier
                    .width(trailingWidth)
                    .height(13.dp),
                shape = RoundedCornerShape(50),
            )
        }
    }
}

/** A short line standing in for a section header. */
@Composable
fun SkeletonSection(
    modifier: Modifier = Modifier,
    width: Dp = 90.dp,
) {
    SkeletonBox(
        modifier = modifier
            .width(width)
            .height(11.dp),
        shape = RoundedCornerShape(50),
    )
}

/**
 * The fallback a new screen can reach for: a stack of section headers and rows under the
 * floating tabs. Screens that know better draw their own composition from the primitives above;
 * this is here so no page has to fall back to a spinner.
 */
@Composable
fun SkeletonListPage(
    modifier: Modifier = Modifier,
    rows: Int = 6,
    sections: Int = 2,
    topPadding: Dp = 68.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = topPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(sections) { section ->
            SkeletonSection(modifier = Modifier.padding(top = if (section == 0) 0.dp else 10.dp))
            repeat(rows / sections) {
                SkeletonRow()
            }
        }
    }
}
