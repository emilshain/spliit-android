package app.spliit.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A card with a torn bottom edge, like a receipt pulled off the roll.
 *
 * The tear is the point of it: the bottom corners are not rounded, and the edge runs as a row of
 * points instead. Naming the shape rather than drawing an image keeps it sharp at any width, and
 * lets it follow the card's own colour in either theme.
 */
class TornReceiptShape(
    private val topCorner: Dp = 24.dp,
    private val toothWidth: Dp = 14.dp,
    private val toothDepth: Dp = 7.dp,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val corner = with(density) { topCorner.toPx() }
        val tooth = with(density) { toothWidth.toPx() }
        val depth = with(density) { toothDepth.toPx() }
        val bottom = size.height - depth

        val path = Path().apply {
            moveTo(0f, corner)
            quadraticTo(0f, 0f, corner, 0f)
            lineTo(size.width - corner, 0f)
            quadraticTo(size.width, 0f, size.width, corner)
            lineTo(size.width, bottom)

            // Teeth along the bottom, alternating between the card's edge and the tear's point.
            var x = size.width
            var pointingDown = true
            while (x > 0f) {
                val next = (x - tooth).coerceAtLeast(0f)
                lineTo(next, if (pointingDown) size.height else bottom)
                x = next
                pointingDown = !pointingDown
            }

            lineTo(0f, bottom)
            close()
        }
        return Outline.Generic(path)
    }
}
