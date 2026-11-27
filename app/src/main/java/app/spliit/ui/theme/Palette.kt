package app.spliit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spliit.data.local.AppearancePalette

/**
 * The colours the iOS app adds on top of the system palette, taken from its asset catalogue.
 *
 * Backgrounds and text stay with Material, for the same reason iOS leaves them to the system:
 * they already adapt to dark mode and to increased contrast. Only what the system has no opinion
 * about — the money axis and the monogram palette — is fixed here.
 */
object SpliitPalette {

    // Tone 40: white text on it, and the accent itself as text on the page, both clear AA.
    // It is the same emerald the iOS app uses, one step deeper than the raw brand swatch.
    val AccentLight = Color(0xFF047A63)
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


    /**
     * The same eight hues as ink on a card rather than as a fill.
     *
     * A glyph tinted with a person's colour has to be legible on the card behind it, and the
     * monogram fills cannot do that in both themes: emerald is right on white and too dark on a
     * dark card, amber is right on black and washes out on a light one. So each mode gets its
     * own tone of the same hue — deep in light, light in dark.
     */
    val MonogramInksLight: List<Color> = listOf(
        Color(0xFF047857), // emerald
        Color(0xFF0E7490), // cyan
        Color(0xFF4F46E5), // indigo
        Color(0xFF9D174D), // pink
        Color(0xFFC2410C), // orange
        Color(0xFFA16207), // amber
        Color(0xFF4D7C0F), // olive
        Color(0xFF6D28D9), // violet
    )

    val MonogramInksDark: List<Color> = listOf(
        Color(0xFF34D399), // emerald
        Color(0xFF22D3EE), // cyan
        Color(0xFFA5B4FC), // indigo
        Color(0xFFF9A8D4), // pink
        Color(0xFFFDBA74), // orange
        Color(0xFFFDE047), // amber
        Color(0xFFBEF264), // olive
        Color(0xFFC4B5FD), // violet
    )

    private val Light = lightColorScheme(
        primary = AccentLight,
        onPrimary = Color.White,
        background = BackgroundLight,
        onBackground = Color(0xFF1C1C1E),
        surface = CardLight,
        onSurface = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFFEFEFF4),
        onSurfaceVariant = Color(0xFF636367),
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
        // The undo bar's pair, in this palette's own neutrals rather than the baseline's.
        inverseSurface = Color(0xFF1C1C1E),
        inverseOnSurface = Color(0xFFF2F2F7),
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
        inverseSurface = Color(0xFFF2F2F7),
        inverseOnSurface = Color(0xFF1C1C1E),
    )

    @Composable
    fun scheme(dark: Boolean = isSystemInDarkTheme()) = if (dark) Dark else Light

    /**
     * Whether this platform can derive a palette from the wallpaper. Adaptive colour arrived
     * with Android 12; older phones keep the app's own greens.
     */
    val supportsDynamicColor: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * The colours a template adds besides its scheme, read the same way from anywhere.
 *
 * A template brings every role with it, so these are not new hexes: they are the scheme's own
 * roles picked out by name — the second accent, the containers, the accents — each with the
 * `on-` colour Material measured against it. The two money roles are the exception: they keep
 * their green and their red, because that is the direction money went, but they take their hue
 * and their chroma from the palette so they belong to the theme instead of sitting on it.
 *
 * [AppearancePalette.Spliit] is the exception the app is named for: it keeps the port's colours
 * from the iOS asset catalogue, which is why [SpliitPalette] still exists.
 */
data class SpliitColors(
    /** Owed to you. */
    val moneyPositive: Color,
    /** You owe. */
    val moneyNegative: Color,
    /** A template's second accent. */
    val brandSecondary: Color,
    /** The tile an empty state's icon sits in. */
    val brandAccentSoft: Color,
    /** The icon's own colour on that tile. */
    val brandOnAccentSoft: Color,
    val monogramFills: List<Color>,
    val monogramOns: List<Color>,
    /** The same people's colours as ink on a card: dark in light mode, light in dark. */
    val monogramInks: List<Color>,
) {
    fun monogramFill(index: Int): Color = monogramFills.at(index)

    fun monogramOn(index: Int): Color = monogramOns.at(index)

    fun monogramInk(index: Int): Color = monogramInks.at(index)

    private fun List<Color>.at(index: Int): Color = this[((index % size) + size) % size]
}

private val SpliitMonograms = SpliitPalette.Monograms
private val SpliitMonogramOns = List(SpliitMonograms.size) { Color.White }

/** Spliit's own palette, the port of the iOS asset catalogue, in each mode. */
private val BrandLight = SpliitColors(
    moneyPositive = SpliitPalette.MoneyPositiveLight,
    moneyNegative = SpliitPalette.MoneyNegativeLight,
    brandSecondary = SpliitPalette.BrandSecondaryLight,
    brandAccentSoft = SpliitPalette.BrandAccentSoftLight,
    brandOnAccentSoft = SpliitPalette.AccentLight,
    monogramFills = SpliitMonograms,
    monogramOns = SpliitMonogramOns,
    monogramInks = SpliitPalette.MonogramInksLight,
)

private val BrandDark = SpliitColors(
    moneyPositive = SpliitPalette.MoneyPositiveDark,
    moneyNegative = SpliitPalette.MoneyNegativeDark,
    brandSecondary = SpliitPalette.BrandSecondaryDark,
    brandAccentSoft = SpliitPalette.BrandAccentSoftDark,
    brandOnAccentSoft = SpliitPalette.AccentDark,
    monogramFills = SpliitMonograms,
    monogramOns = SpliitMonogramOns,
    monogramInks = SpliitPalette.MonogramInksDark,
)

/**
 * A template's extra roles, read back out of its scheme.
 *
 * Nothing here is a literal: every value is a Material role, so the template owns the whole
 * appearance. `secondary` is the second accent, the containers and accents are what the scheme
 * reserves for tiles and monograms, and each comes with its own `on-` colour, which keeps every
 * set of initials legible on every template. The money roles are the one addition — a green and
 * a red the scheme does not name, built from its accent in [semanticTint].
 */
private fun templateColors(scheme: ColorScheme): SpliitColors = SpliitColors(
    moneyPositive = scheme.moneyPositiveTint(),
    moneyNegative = scheme.moneyNegativeTint(),
    brandSecondary = scheme.secondary,
    brandAccentSoft = scheme.primaryContainer,
    brandOnAccentSoft = scheme.onPrimaryContainer,
    monogramFills = listOf(
        scheme.primaryContainer,
        scheme.secondaryContainer,
        scheme.tertiaryContainer,
        scheme.primary,
        scheme.secondary,
        scheme.tertiary,
        scheme.errorContainer,
        scheme.inverseSurface,
    ),
    monogramOns = listOf(
        scheme.onPrimaryContainer,
        scheme.onSecondaryContainer,
        scheme.onTertiaryContainer,
        scheme.onPrimary,
        scheme.onSecondary,
        scheme.onTertiary,
        scheme.onErrorContainer,
        scheme.inverseOnSurface,
    ),
    // The accents rather than their containers: those are already the mode's own tone, so they
    // are dark on a light card and light on a dark one without a second palette.
    monogramInks = listOf(
        scheme.primary,
        scheme.secondary,
        scheme.tertiary,
        scheme.primary,
        scheme.secondary,
        scheme.tertiary,
        scheme.error,
        scheme.onSurfaceVariant,
    ),
)

/** The green that means "owed to you" on a generated template. */
private fun ColorScheme.moneyPositiveTint(): Color = semanticTint(anchorHue = 155.0)

/** The red that means "you owe" on a generated template. */
private fun ColorScheme.moneyNegativeTint(): Color = semanticTint(anchorHue = 8.0)

/**
 * One of the two money colours, drawn in the palette's own hand.
 *
 * A template brings its hues with it, so a stock emerald and a stock error red would be the only
 * colours on the screen the palette did not choose — and on Iris or Ember, the ones that clash
 * with it. Money cannot simply borrow `tertiary` either: on some templates the second accent is
 * pink, and "owed to you" has to read as green.
 *
 * So each tint starts from the hue everyone agrees on — emerald for owed, coral red for owing —
 * and leans that hue up to 16° towards the palette's accent, the same small rotation Material's
 * `harmonize` uses to tint a semantic colour into a scheme. Its chroma is the accent's own, so
 * the tint is exactly as loud as the palette around it, and its tone is set by the surface: 38
 * on light templates, 78 on dark ones, which keeps AA against every surface an amount is drawn
 * on. On a green template the green goes olive; on a warm one the red goes orange; on Iris the
 * green goes teal. Neither ever stops being green or red.
 */
private fun ColorScheme.semanticTint(anchorHue: Double): Color {
    val accent = Hct.fromInt(primary.toArgb())
    // Signed shortest way round the wheel, capped so the reading survives the lean.
    val lean = (accent.hue - anchorHue + 540.0) % 360.0 - 180.0
    val hue = (anchorHue + lean.coerceIn(-16.0, 16.0) + 360.0) % 360.0
    val chroma = accent.chroma.coerceIn(44.0, 72.0)
    val tone = if (background.luminance() > 0.5f) 38.0 else 78.0
    return Color(Hct.from(hue, chroma, tone).toInt())
}

val LocalSpliitColors = staticCompositionLocalOf { BrandLight }

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
    dynamicColor: Boolean = true,
    palette: AppearancePalette = AppearancePalette.Spliit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // The platform's own expressive palette: every role — surfaces, containers, accents — comes
    // from the wallpaper, and the light/dark choice above still decides which half.
    val wallpaper = dynamicColor && SpliitPalette.supportsDynamicColor
    val scheme = when {
        wallpaper -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // No wallpaper palette: a template drawn by Material Color Utilities instead.
        else -> PaletteTemplates.scheme(palette, dark)
    }
    // Spliit's hand-authored colours are the one case that is not its scheme's. A template or
    // the wallpaper is; then every extra role is read back out of that scheme instead.
    val brand = when {
        wallpaper || palette != AppearancePalette.Spliit -> templateColors(scheme)
        dark -> BrandDark
        else -> BrandLight
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        shapes = SpliitShapes.Material,
    ) {
        CompositionLocalProvider(LocalSpliitColors provides brand, content = content)
    }
}
