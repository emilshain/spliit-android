package app.spliit.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.spliit.data.local.AppearancePalette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

/**
 * The colour templates, drawn by Google's Material Color Utilities.
 *
 * `material-kolor` is the Kotlin port of `material-foundation/material-color-utilities`: the same
 * HCT maths the platform uses for wallpaper colour, so a template here is a real Material 3 tonal
 * scheme rather than a handful of hand-picked hexes. Every role — primary, secondary, tertiary,
 * the five surface containers, outline, error — comes out of the engine, which is what makes the
 * template survive both light and dark and keeps `on-` colours legible on their containers.
 *
 * [AppearancePalette.Spliit] is deliberately not generated: it is the port's own palette from the
 * iOS app, and it stays hand-authored.
 */
object PaletteTemplates {

    /** Seed colours, one hue per template. Expressive keeps the chroma high enough to sing. */
    fun seed(palette: AppearancePalette): Color = when (palette) {
        AppearancePalette.Spliit -> SpliitPalette.AccentLight
        AppearancePalette.Iris -> Color(0xFF6750A4)
        AppearancePalette.Lagoon -> Color(0xFF00696D)
        AppearancePalette.Ember -> Color(0xFFB3402A)
        AppearancePalette.Meadow -> Color(0xFF386A20)
    }

    /** The scheme a template draws with, in the mode asked for. */
    @Composable
    fun scheme(palette: AppearancePalette, dark: Boolean): ColorScheme = when (palette) {
        AppearancePalette.Spliit -> SpliitPalette.scheme(dark)
        else -> dynamicColorScheme(
            seedColor = seed(palette),
            isDark = dark,
            isAmoled = false,
            style = PaletteStyle.Expressive,
        )
    }
}
