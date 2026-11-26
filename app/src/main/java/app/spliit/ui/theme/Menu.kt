package app.spliit.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * Which side of the thing that opened it the menu grows from.
 *
 * A menu on a toolbar opens downwards, under the button that owns it; the one on the add button
 * opens upwards, so the button stays where it was and the menu rises out of it.
 */
enum class MenuPlacement { Below, Above }

/**
 * Material 3 Expressive's vertical menu.
 *
 * The 2025 menu redesign is a rounder, airier thing than the list this app used before: a 16dp
 * container, 48dp rows with a 12dp inset selection pill rather than a full-width highlight, and
 * 8dp between an icon and its label. Colour follows the "standard" mapping — a surface-based
 * container with the secondary container marking the current choice — because a vibrant
 * tertiary menu is for the one moment a screen has a choice worth shouting about, and none of
 * this app's menus are that moment.
 *
 * It is not a [androidx.compose.material3.DropdownMenu]: that component's shape, highlight and
 * motion are the baseline ones and are not configurable enough to reach this look.
 *
 * The motion is SwiftUI's: the menu grows from the corner nearest its anchor on a `.snappy`
 * spring, with the scale overshooting by a hair, while the opacity alone uses a critically
 * damped spring so the fade never flickers.
 */
@Composable
fun ExpressiveMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    placement: MenuPlacement = MenuPlacement.Below,
    alignEnd: Boolean = true,
    gap: Dp = 8.dp,
    minWidth: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return

    val density = LocalDensity.current
    val positionProvider = remember(placement, alignEnd, gap, density) {
        ExpressiveMenuPositionProvider(
            placement = placement,
            alignEnd = alignEnd,
            gapPx = with(density) { gap.roundToPx() },
            marginPx = with(density) { 8.dp.roundToPx() },
        )
    }
    val shape = MenuShape

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        // One layer carries the whole open: the scale, the fade and the shadow. `scaleIn` inside
        // an `AnimatedVisibility` puts the scale on one layer and `Modifier.shadow` on a child
        // layer, and the render thread then recomputes the child's shadow a frame behind the
        // shape it belongs to - the menu's edge moves, its shadow arrives late. Elevation on the
        // same `graphicsLayer` as the scale is drawn in the same pass, so the two cannot separate.
        val opened = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { opened.value = true }
        val scale by animateFloatAsState(
            targetValue = if (opened.value) 1f else 0.9f,
            animationSpec = SpliitMotion.snappy(),
            label = "menu scale",
        )
        val alpha by animateFloatAsState(
            targetValue = if (opened.value) 1f else 0f,
            animationSpec = SpliitMotion.smooth(),
            label = "menu fade",
        )
        // Grows from the corner nearest the anchor.
        val origin = remember(alignEnd, placement) {
            TransformOrigin(
                if (alignEnd) 1f else 0f,
                if (placement == MenuPlacement.Above) 1f else 0f,
            )
        }

        // As wide as its longest row, and no wider than the screen it opens on.
        val windowWidth = LocalConfiguration.current.screenWidthDp.dp
        Column(
            modifier = modifier
                .widthIn(
                    min = minWidth,
                    max = (windowWidth - 32.dp).coerceAtLeast(180.dp),
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = origin
                    this.alpha = alpha
                    shadowElevation = SpliitElevation.Menu.toPx()
                    this.shape = shape
                    clip = false
                }
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/**
 * One row of an [ExpressiveMenu]: 48dp tall, with a pill that fills in behind the current
 * choice instead of the whole row changing colour.
 */
@Composable
fun ExpressiveMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
    supportingText: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (selected) colors.secondaryContainer else Color.Transparent,
        animationSpec = SpliitMotion.smooth(),
        label = "menu item container",
    )
    val contentColor = when {
        destructive -> colors.error
        selected -> colors.onSecondaryContainer
        else -> colors.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = SpliitType.Secondary,
                        color = if (selected) contentColor else colors.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * The chevron on a row that opens a menu, turned with the same spring the menu itself uses.
 */
@Composable
fun ExpandChevron(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = SpliitMotion.snappy(),
        label = "chevron rotation",
    )
    Icon(
        imageVector = Icons.Filled.ExpandMore,
        contentDescription = null,
        tint = tint,
        modifier = modifier.rotate(rotation),
    )
}

private val MenuShape = RoundedCornerShape(16.dp)

/**
 * Places the menu against the anchor it was composed inside: end-aligned by default, above or
 * below it by [placement], and nudged back inside the window when the anchor sits near an edge.
 */
private class ExpressiveMenuPositionProvider(
    private val placement: MenuPlacement,
    private val alignEnd: Boolean,
    private val gapPx: Int,
    private val marginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val endAligned = if (layoutDirection == LayoutDirection.Rtl) !alignEnd else alignEnd
        val x = if (endAligned) {
            anchorBounds.right - popupContentSize.width
        } else {
            anchorBounds.left
        }

        val below = anchorBounds.bottom + gapPx
        val above = anchorBounds.top - popupContentSize.height - gapPx
        val fitsBelow = below + popupContentSize.height <= windowSize.height - marginPx
        val fitsAbove = above >= marginPx
        val y = when {
            placement == MenuPlacement.Above && fitsAbove -> above
            fitsBelow -> below
            fitsAbove -> above
            else -> below
        }

        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val maxY = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx)
        return IntOffset(
            x = x.coerceIn(marginPx, maxX),
            y = y.coerceIn(marginPx, maxY),
        )
    }
}
