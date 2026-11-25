package app.spliit.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * An action a row offers when it is swiped.
 *
 * [collapses] separates the two kinds. A delete takes the row with it, so once the row is out of
 * the way the action shrinks to nothing. A toggle — star, archive — leaves the row in the list, so
 * the row springs back instead.
 */
data class SwipeAction(
    val icon: ImageVector,
    val label: String,
    val containerColor: Color,
    val contentColor: Color,
    val collapses: Boolean = false,
    val onAction: () -> Unit,
)

/**
 * Material 3 Expressive's swipe, the one the Clock app uses.
 *
 * Nothing is drawn behind the row at rest — the page's own background shows through — so a card
 * is a card and not a card inside a tinted slot. Swiping opens a gap between the row and its edge,
 * and the colour fills exactly that gap: it starts as a sliver behind the icon and widens with the
 * thumb, which is why the action feels attached to the gesture rather than revealed by it.
 *
 * A delete that completes takes the coloured panel's height to zero before the list removes the
 * row, so the list closes the gap instead of jumping it shut.
 */
@Composable
fun ExpressiveSwipe(
    modifier: Modifier = Modifier,
    shape: Shape,
    startToEnd: SwipeAction? = null,
    endToStart: SwipeAction? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val collapse = remember { Animatable(0f) }
    var width by remember { mutableStateOf(0f) }
    // One commit per gesture: a second drag while the row is on its way out does nothing.
    var committed by remember { mutableStateOf(false) }

    suspend fun settle(action: SwipeAction, direction: Int) {
        if (committed) return
        committed = true
        offset.animateTo(direction * width, SpliitMotion.spatialFast())
        if (action.collapses) {
            collapse.animateTo(1f, SpliitMotion.effectsDefault())
        }
        action.onAction()
        if (!action.collapses) {
            // A toggle leaves the row where it was; the list re-renders it with the new state.
            offset.animateTo(0f, SpliitMotion.spatialDefault())
            committed = false
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { width = it.width.toFloat() }
            .graphicsLayer {
                scaleY = 1f - collapse.value
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            },
    ) {
        val trailingReveal = (-offset.value).coerceAtLeast(0f)
        val leadingReveal = offset.value.coerceAtLeast(0f)

        if (endToStart != null && trailingReveal > 0f) {
            ActionPanel(endToStart, trailingReveal, Alignment.CenterEnd, shape)
        }
        if (startToEnd != null && leadingReveal > 0f) {
            ActionPanel(startToEnd, leadingReveal, Alignment.CenterStart, shape)
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offset.snapTo((offset.value + delta).coerceIn(-width, width))
                        }
                    },
                    onDragStopped = { velocity ->
                        val threshold = width * 0.4f
                        val flung = width * 0.1f
                        when {
                            offset.value > threshold ||
                                (offset.value > flung && velocity > 800f) -> {
                                val action = startToEnd
                                if (action != null) settle(action, 1) else offset.animateTo(0f, SpliitMotion.snappy())
                            }

                            offset.value < -threshold ||
                                (offset.value < -flung && velocity < -800f) -> {
                                val action = endToStart
                                if (action != null) settle(action, -1) else offset.animateTo(0f, SpliitMotion.snappy())
                            }

                            else -> offset.animateTo(0f, SpliitMotion.snappy())
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

/** The colour of a swipe, as wide as the gap the row has opened, flush with the row's shape. */
@Composable
private fun BoxScope.ActionPanel(
    action: SwipeAction,
    reveal: Float,
    edge: Alignment,
    shape: Shape,
) {
    val width = with(LocalDensity.current) { reveal.toDp() }
    Box(
        modifier = Modifier
            .align(edge)
            .width(width)
            .fillMaxHeight()
            .clip(shape)
            .background(action.containerColor),
        contentAlignment = if (edge == Alignment.CenterEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.label,
            modifier = Modifier.padding(horizontal = 22.dp).size(22.dp),
            tint = action.contentColor,
        )
    }
}
