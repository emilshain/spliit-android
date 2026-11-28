package app.spliit.ui.theme

import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * The drag position is a plain float rather than an `Animatable` that every drag delta snaps in a
 * new coroutine — that is what used to leave the row parked halfway: two coroutines animating the
 * same value, each cancelling the other, and a gesture that ended while one of them was still
 * running. Now the drag writes the value directly, the settle is the only animation, and the
 * gesture is disabled for its duration, so there is exactly one thing moving the row at a time.
 */
@Composable
fun ExpressiveSwipe(
    modifier: Modifier = Modifier,
    startToEnd: SwipeAction? = null,
    endToStart: SwipeAction? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    var shrink by remember { mutableFloatStateOf(0f) }
    // How much of the action panel is still there. A delete closes it to nothing; a toggle
    // leaves it at its width and springs the row back.
    var panelOpen by remember { mutableFloatStateOf(1f) }
    // True from the moment a gesture is decided until the row has finished moving: drags and a
    // second gesture are ignored meanwhile, so nothing can race the animation.
    var settling by remember { mutableStateOf(false) }

    suspend fun glideTo(target: Float, spec: androidx.compose.animation.core.FiniteAnimationSpec<Float>) {
        animate(initialValue = offset, targetValue = target, animationSpec = spec) { value, _ ->
            offset = value
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { width = it.width.toFloat() }
            .graphicsLayer {
                scaleY = 1f - shrink
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            },
    ) {
        val trailingReveal = (-offset).coerceAtLeast(0f)
        val leadingReveal = offset.coerceAtLeast(0f)

        if (endToStart != null && trailingReveal > 0f) {
            ActionPanel(endToStart, trailingReveal * panelOpen, Alignment.CenterEnd)
        }
        if (startToEnd != null && leadingReveal > 0f) {
            ActionPanel(startToEnd, leadingReveal * panelOpen, Alignment.CenterStart)
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !settling,
                    state = rememberDraggableState { delta ->
                        offset = (offset + delta).coerceIn(-width, width)
                    },
                    onDragStopped = { velocity ->
                        val threshold = width * 0.4f
                        val flung = width * 0.1f
                        val action = when {
                            offset > threshold || (offset > flung && velocity > 800f) -> startToEnd to 1
                            offset < -threshold || (offset < -flung && velocity < -800f) -> endToStart to -1
                            else -> null
                        }
                        if (settling) return@draggable
                        settling = true
                        scope.launch {
                            try {
                                if (action?.first == null) {
                                    glideTo(0f, SpliitMotion.snappy())
                                    return@launch
                                }
                                val (chosen, direction) = action as Pair<SwipeAction, Int>
                                // The row always travels its full width: the swipe commits to the
                                // action it revealed, whether that action takes the row away or
                                // leaves it in the list.
                                glideTo(direction * width, SpliitMotion.spatialFast())
                                if (chosen.collapses) {
                                    // The row is on its way out: what it leaves behind is the
                                    // panel, and it closes to nothing rather than waiting there
                                    // for the list to drop the row.
                                    animate(
                                        initialValue = 1f,
                                        targetValue = 0f,
                                        animationSpec = SpliitMotion.spatialDefault(),
                                    ) { value, _ -> panelOpen = value }
                                    animate(
                                        initialValue = 0f,
                                        targetValue = 1f,
                                        animationSpec = SpliitMotion.spatialFast(),
                                    ) { value, _ -> shrink = value }
                                    chosen.onAction()
                                } else {
                                    // A toggle leaves the row in the list, and the order matters:
                                    // the row springs home *first*, and the action is applied once
                                    // it has landed. Applying it first re-sorts the list, which
                                    // disposes this row mid-glide — that is the card that used to
                                    // vanish and reappear with no movement at all. The way home is
                                    // the fast spatial spring, so the return is over before the
                                    // list has finished re-sorting.
                                    glideTo(0f, SpliitMotion.spatialFast())
                                    chosen.onAction()
                                }
                            } finally {
                                // Never leave the row settled at a half position, whatever happened.
                                if (!settling) offset = 0f
                                settling = false
                            }
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

/** The colour of a swipe, as wide as the gap the row has opened and as tall as the row. */
@Composable
private fun BoxScope.ActionPanel(
    action: SwipeAction,
    reveal: Float,
    edge: Alignment,
) {
    val revealWidth = with(LocalDensity.current) { reveal.toDp() }
    // Inside a lazy item the height is unbounded, so a fillMaxHeight panel measured only its
    // icon and sat short of the card. Matching the parent's measured size gives it the card's
    // own height, growing out of the edge the row slid away from.
    Box(modifier = Modifier.matchParentSize(), contentAlignment = edge) {
        Box(
            modifier = Modifier
                .width(revealWidth)
                .fillMaxHeight()
                .clip(PanelShape)
                .background(action.containerColor),
            contentAlignment = if (edge == Alignment.CenterEnd) {
                Alignment.CenterEnd
            } else {
                Alignment.CenterStart
            },
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                modifier = Modifier.padding(horizontal = 22.dp).size(22.dp),
                tint = action.contentColor,
            )
        }
    }
}

/**
 * The panel is a pill: fully round on every side, whatever corner the card beside it happens to
 * have. It is a container of its own rather than a continuation of the row's shape, and the round
 * ends read as a button under the thumb instead of a sliver of tinted card.
 */
private val PanelShape = RoundedCornerShape(percent = 50)
