package app.spliit.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import app.spliit.data.local.AppearancePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The templates are Material 3 expressive, or they are not templates of this app.
 *
 * The scheme itself comes from Material Color Utilities, and the app's extra roles — money,
 * monograms, the empty-state tile — are read back out of that same scheme. This renders every
 * template in both modes and checks each `on-` colour really does clear WCAG AA against the fill
 * it sits on, and that none of the extra roles falls back to a brand hex.
 *
 * Spliit's palette is the port of the iOS colours and is deliberately outside this check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class PaletteTemplatesTest {

    @get:Rule
    val compose = createComposeRule()

    private val templates = AppearancePalette.entries - AppearancePalette.Spliit

    @Test
    fun everyTemplatePairClearsAa() {
        val captured = mutableMapOf<Pair<AppearancePalette, Boolean>, Pair<ColorScheme, SpliitColors>>()

        compose.setContent {
            for (palette in templates) {
                for (dark in listOf(false, true)) {
                    SpliitTheme(dark = dark, dynamicColor = false, palette = palette) {
                        val scheme = MaterialTheme.colorScheme
                        val colors = LocalSpliitColors.current
                        SideEffect { captured[palette to dark] = scheme to colors }
                    }
                }
            }
        }
        compose.waitForIdle()

        assertEquals(templates.size * 2, captured.size)

        for ((key, value) in captured) {
            val (palette, dark) = key
            val (scheme, colors) = value
            val mode = "$palette ${if (dark) "dark" else "light"}"

            // Material's own pairs.
            pair("$mode onPrimary", scheme.onPrimary, scheme.primary)
            pair("$mode onSecondary", scheme.onSecondary, scheme.secondary)
            pair("$mode onTertiary", scheme.onTertiary, scheme.tertiary)
            pair("$mode onError", scheme.onError, scheme.error)
            pair("$mode onPrimaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer)
            pair("$mode onSecondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer)
            pair("$mode onTertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer)
            pair("$mode onErrorContainer", scheme.onErrorContainer, scheme.errorContainer)
            pair("$mode onSurface", scheme.onSurface, scheme.surface)
            pair("$mode onSurfaceVariant", scheme.onSurfaceVariant, scheme.surface)
            pair("$mode onBackground", scheme.onBackground, scheme.background)
            pair("$mode inverseOnSurface", scheme.inverseOnSurface, scheme.inverseSurface)

            // Every pairing a component draws, not just Material's own.
            componentPairs(mode, scheme)

            // The roles this app adds, on every surface an amount or a tile is drawn on.
            for (surface in listOf(scheme.background, scheme.surface, scheme.surfaceContainerLow)) {
                pair("$mode moneyPositive on surface", colors.moneyPositive, surface)
                pair("$mode moneyNegative on surface", colors.moneyNegative, surface)
            }
            pair("$mode empty-state icon on its tile", colors.brandOnAccentSoft, colors.brandAccentSoft)
            for (index in colors.monogramFills.indices) {
                pair("$mode monogram $index", colors.monogramOn(index), colors.monogramFill(index))
            }

            // Money keeps its direction: green for owed, red for owing. Neither borrows a brand
            // hex or the scheme's `error` any more - both lean towards the palette's accent and
            // share its chroma, so they belong to the template. What must not drift is the
            // reading: green stays green, red stays red, on every template.
            assertTrue(
                "$mode moneyPositive is not green",
                colors.moneyPositive.green > colors.moneyPositive.red &&
                    colors.moneyPositive.green > colors.moneyPositive.blue,
            )
            assertTrue(
                "$mode moneyNegative is not red",
                colors.moneyNegative.red > colors.moneyNegative.green &&
                    colors.moneyNegative.red > colors.moneyNegative.blue,
            )
            assertEquals(scheme.secondary, colors.brandSecondary)
            assertEquals(scheme.primaryContainer, colors.brandAccentSoft)
            assertEquals(scheme.onPrimaryContainer, colors.brandOnAccentSoft)
            assertEquals(
                listOf(
                    scheme.primaryContainer,
                    scheme.secondaryContainer,
                    scheme.tertiaryContainer,
                    scheme.primary,
                    scheme.secondary,
                    scheme.tertiary,
                    scheme.errorContainer,
                    scheme.inverseSurface,
                ),
                colors.monogramFills,
            )
        }
    }

    /**
     * Spliit's own palette draws the same components, so it owes the same contrast. Money is the
     * one thing checked here that is not a Material role - and it is checked against the same
     * surfaces, because an amount can be printed on any of them.
     */
    @Test
    fun spliitPaletteClearsAa() {
        val captured = mutableMapOf<Boolean, Pair<ColorScheme, SpliitColors>>()

        compose.setContent {
            for (dark in listOf(false, true)) {
                SpliitTheme(dark = dark, dynamicColor = false, palette = AppearancePalette.Spliit) {
                    val scheme = MaterialTheme.colorScheme
                    val colors = LocalSpliitColors.current
                    SideEffect { captured[dark] = scheme to colors }
                }
            }
        }
        compose.waitForIdle()

        assertEquals(2, captured.size)

        for ((dark, value) in captured) {
            val (scheme, colors) = value
            val mode = "Spliit ${if (dark) "dark" else "light"}"

            componentPairs(mode, scheme)

            for (surface in listOf(scheme.background, scheme.surface, scheme.surfaceContainerLow)) {
                pair("$mode moneyPositive on surface", colors.moneyPositive, surface)
                pair("$mode moneyNegative on surface", colors.moneyNegative, surface)
            }
        }
    }

    /**
     * The role pairings the app's components draw, straight out of the Material 3 role set:
     * `onSurface` and `onSurfaceVariant` on every container level, `onPrimary` on `primary` (the
     * add button and the active filter pill), `onSecondaryContainer` on `secondaryContainer` (the
     * selected tab and the selected menu row), and `inverseOnSurface` on `inverseSurface` (the
     * undo bar). Dividers use `outlineVariant`, which is decorative and carries no text.
     */
    private fun componentPairs(mode: String, scheme: ColorScheme) {
        val containers = listOf(
            scheme.background,
            scheme.surface,
            scheme.surfaceContainerLow,
            scheme.surfaceContainer,
            scheme.surfaceContainerHigh,
            scheme.surfaceContainerHighest,
        )
        for (surface in containers) {
            pair("$mode onSurface on container", scheme.onSurface, surface)
            pair("$mode onSurfaceVariant on container", scheme.onSurfaceVariant, surface)
        }
        pair("$mode primary on background", scheme.primary, scheme.background)
        pair("$mode primary on surface", scheme.primary, scheme.surface)
        pair("$mode onPrimary on primary", scheme.onPrimary, scheme.primary)
        pair("$mode onSecondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer)
        pair("$mode inverseOnSurface", scheme.inverseOnSurface, scheme.inverseSurface)
    }

    private fun pair(label: String, on: Color, fill: Color) {
        val ratio = contrast(on, fill)
        assertTrue("%s is %.2f:1, below AA 4.5:1".format(label, ratio), ratio >= 4.5)
    }

    /** WCAG relative-luminance contrast — the measure Material's tonal roles are chosen for. */
    private fun contrast(a: Color, b: Color): Double {
        val first = a.luminance() + 0.05
        val second = b.luminance() + 0.05
        return maxOf(first, second) / minOf(first, second)
    }
}
