package app.spliit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The colours the iOS app adds on top of the system palette, taken from its asset catalogue.
 *
 * Backgrounds and text stay with Material, for the same reason iOS leaves them to the system:
 * they already adapt to dark mode and to increased contrast. Only what the system has no opinion
 * about — the money axis and the monogram palette — is fixed here.
 */
object SpliitPalette {

    val AccentLight = Color(0xFF059669)
    val AccentDark = Color(0xFF10B981)

    /** Owed to you. Emerald, darkened in light mode to clear AA against white. */
    val MoneyPositiveLight = Color(0xFF047857)
    val MoneyPositiveDark = Color(0xFF34D399)

    /** You owe. The logo's coral. */
    val MoneyNegativeLight = Color(0xFFC2334A)
    val MoneyNegativeDark = Color(0xFFFF8A9B)

    /** The accent at a whisper, for the tile an empty state's icon sits in. */
    val BrandAccentSoftLight = Color(0xFFECFDF5)
    val BrandAccentSoftDark = Color(0xFF0B3B2E)

    val BrandSecondaryLight = Color(0xFFBE185D)
    val BrandSecondaryDark = Color(0xFFEC4899)

    /** Grouped-list neutrals: what iOS draws behind a list and on a card. */
    val BackgroundLight = Color(0xFFF2F2F7)
    val BackgroundDark = Color(0xFF000000)
    val CardLight = Color(0xFFFFFFFF)
    val CardDark = Color(0xFF1C1C1E)

    /** The eight monogram colours, numbered from 1 and identical in both themes. */
    val Monograms: List<Color> = listOf(
        Color(0xFF059669), // emerald
        Color(0xFF0891B2), // cyan
        Color(0xFF6366F1), // indigo
        Color(0xFFBE185D), // pink
        Color(0xFFEA580C), // orange
        Color(0xFFCA8A04), // amber
        Color(0xFF4D7C0F), // olive
        Color(0xFF7C3AED), // violet
    )

    fun monogram(index: Int): Color = Monograms[((index % Monograms.size) + Monograms.size) % Monograms.size]

    private val Light = lightColorScheme(
        primary = AccentLight,
        onPrimary = Color.White,
        background = BackgroundLight,
        onBackground = Color(0xFF1C1C1E),
        surface = CardLight,
        onSurface = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFFEFEFF4),
        onSurfaceVariant = Color(0xFF6C6C70),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFF7F7FA),
        surfaceContainerHigh = Color(0xFFEFEFF4),
        surfaceContainerHighest = Color(0xFFE8E8EE),
        secondaryContainer = Color(0xFFD1FAE5),
        onSecondaryContainer = Color(0xFF064E3B),
        outlineVariant = Color(0xFFE3E3E9),
        surfaceTint = AccentLight,
        error = MoneyNegativeLight,
    )

    private val Dark = darkColorScheme(
        primary = AccentDark,
        onPrimary = Color(0xFF00281B),
        background = BackgroundDark,
        onBackground = Color(0xFFF2F2F7),
        surface = CardDark,
        onSurface = Color(0xFFF2F2F7),
        surfaceVariant = Color(0xFF2C2C2E),
        onSurfaceVariant = Color(0xFFAEAEB2),
        surfaceContainerLowest = Color(0xFF0A0A0B),
        surfaceContainerLow = Color(0xFF232326),
        surfaceContainer = Color(0xFF1C1C1E),
        surfaceContainerHigh = Color(0xFF2C2C2E),
        surfaceContainerHighest = Color(0xFF35353A),
        secondaryContainer = Color(0xFF0B3B2E),
        onSecondaryContainer = Color(0xFFA7F3D0),
        outlineVariant = Color(0xFF3A3A3F),
        surfaceTint = AccentDark,
        error = MoneyNegativeDark,
    )

    @Composable
    fun scheme(dark: Boolean = isSystemInDarkTheme()) = if (dark) Dark else Light
}

/** The colours that are not part of Material's scheme, read the same way from anywhere. */
data class SpliitColors(
    val moneyPositive: Color,
    val moneyNegative: Color,
    val brandSecondary: Color,
    val brandAccentSoft: Color,
)

private val LightColors = SpliitColors(
    moneyPositive = SpliitPalette.MoneyPositiveLight,
    moneyNegative = SpliitPalette.MoneyNegativeLight,
    brandSecondary = SpliitPalette.BrandSecondaryLight,
    brandAccentSoft = SpliitPalette.BrandAccentSoftLight,
)

private val DarkColors = SpliitColors(
    moneyPositive = SpliitPalette.MoneyPositiveDark,
    moneyNegative = SpliitPalette.MoneyNegativeDark,
    brandSecondary = SpliitPalette.BrandSecondaryDark,
    brandAccentSoft = SpliitPalette.BrandAccentSoftDark,
)

val LocalSpliitColors = staticCompositionLocalOf { LightColors }

/**
 * Elevation, the M3 Expressive way: tone first, shadow second.
 *
 * A surface that sits on the page is separated by its colour, not by a drop shadow. The
 * surface-container roles in [SpliitPalette.scheme] do that work: as a container rises it takes
 * a brighter tint (in light) or a lighter neutral (in dark), so a card, the tab track and the
 * background read as three levels without a single blurred edge. Cards, rows, bars and the undo
 * snackbar are all flat for this reason.
 *
 * A shadow is spent only where something really does float over the page and the user is meant
 * to see the gap: a menu, the pills that fly out of the add button, and the snackbar. The FAB
 * keeps Material's own shadow. Those are the numbers below; keep them few.
 */
object SpliitElevation {
    /** Menus and other anchored popups above the page. */
    val Menu = 6.dp

    /** The action pills a FAB menu unfolds. Lighter: they are small and near their button. */
    val FloatingAction = 4.dp

    /** The undo bar, the one bar that briefly covers the content. */
    val Bar = 4.dp
}

/**
 * Text styles the port needs by name.
 *
 * SF Rounded is a system face on iOS, and no custom font ships with this app. The substitution
 * recorded in the audit — the system sans — is the one unavoidable typographic difference;
 * everything else (sizes, weights, tracking) follows the reference.
 */
object SpliitType {
    val Wordmark = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.2).sp,
    )

    val Title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    )

    /** A group or participant name in a row. */
    val RowTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
    )

    /** The line under a row title: counts, dates, second-order facts. */
    val Secondary = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
    )

    /** A number that is the point of the screen: a group's balance, an expense's total. */
    val Display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.4).sp,
    )

    /** The caption above a number — "you are owed", "total amount". Small, and never a sentence. */
    val Overline = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 1.1.sp,
    )

    /** "Starred", "Recent groups" and the rest: the only thing naming a section. */
    val SectionHeader = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp,
    )
}

@Composable
fun SpliitTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSpliitColors provides if (dark) DarkColors else LightColors) {
        MaterialTheme(
            colorScheme = SpliitPalette.scheme(dark),
            typography = Typography(),
            shapes = SpliitShapes.Material,
            content = content,
        )
    }
}
