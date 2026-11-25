package app.spliit.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * How this app moves.
 *
 * Taken from Material 3 Expressive, whose motion is spring-based rather than timed: a spring
 * carries its velocity into whatever the user does next, so a tab switch interrupted halfway
 * bends into the new direction instead of restarting. That is also how SwiftUI animates — the
 * reference app's `.snappy` and `.smooth` are springs — so the two themes agree on the shape of
 * the motion even though the numbers here come from Google's tokens.
 *
 * The scheme splits in two, which is the part that matters:
 *
 * - **Spatial** specs move, size and reshape things. A damping ratio below 1 lets them overshoot
 *   slightly — the "expressive" part — and is what makes a card land rather than arrive.
 * - **Effects** specs fade and tint. Overshoot here would read as a flicker, so they are
 *   critically damped: fast, smooth, no bounce.
 *
 * The values sit between Material's expressive preset (damping 0.6, stiffness 700) and SwiftUI's
 * snappy spring (damping ~0.85, stiffness ~500): 0.78 at 420f is alive at arm's length without
 * looking like a toy. Raise the damping to calm the whole app down; lower it to make it playful.
 */
object SpliitMotion {

    /** Position, size and shape. Partly covering the screen: sheets, tab content, bars. */
    fun <T> spatialDefault(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.78f, stiffness = 420f)

    /** Small things reacting to a touch: a pressed control, a checkbox, a slice of a bar. */
    fun <T> spatialFast(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.80f, stiffness = 900f)

    /** Whole screens arriving or leaving. Slower, and a touch more bounce is visible on the way. */
    fun <T> spatialSlow(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.74f, stiffness = 260f)

    /** Colour and opacity. No overshoot: a fading surface that bounces looks like a glitch. */
    fun <T> effectsDefault(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f)

    /** The same, for things that must be gone before the user notices they were there. */
    fun <T> effectsFast(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 3800f)

    /**
     * Material's "emphasized" curves, for the few places a duration is the right tool — a
     * screen-wide content swap, where three things have to line up and a spring each would
     * arrive at slightly different moments.
     */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val SHORT_MILLIS = 150
    const val MEDIUM_MILLIS = 250
    const val LONG_MILLIS = 400
}

/**
 * Corner radii, rounded up from Material's defaults.
 *
 * Expressive leans on generous, almost pill-like corners for anything the user touches, and
 * keeps the tighter ones for dense content. Only the roles Material components read are set
 * here; the hand-drawn surfaces in the screens use their own radii.
 */
object SpliitShapes {
    val Material = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
}
