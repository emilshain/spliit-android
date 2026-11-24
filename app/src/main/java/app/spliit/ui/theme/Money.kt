package app.spliit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding

/** How much room an amount takes, matching the reference's four money roles. */
enum class MoneySize { HERO, LEAD, ROW, SUPPORT }

/**
 * Which way money goes, as a colour.
 *
 * A colour cannot be read aloud, so every screen that tints an amount also says the direction in
 * words beside it.
 */
enum class MoneySign {
    POSITIVE,
    NEGATIVE,
    NEUTRAL,
    ;

    companion object {
        fun of(balance: Int): MoneySign = when {
            balance > 0 -> POSITIVE
            balance < 0 -> NEGATIVE
            else -> NEUTRAL
        }
    }
}

/**
 * An amount, in the face the reference reserves for money: tabular digits so a column of them
 * lines up, and the sign's own colour when the sign means something.
 */
@Composable
fun Money(
    value: String,
    size: MoneySize = MoneySize.ROW,
    sign: MoneySign = MoneySign.NEUTRAL,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpliitColors.current
    val tint = when (sign) {
        MoneySign.POSITIVE -> colors.moneyPositive
        MoneySign.NEGATIVE -> colors.moneyNegative
        MoneySign.NEUTRAL -> MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = value,
        style = moneyStyle(size),
        color = tint,
        modifier = modifier,
    )
}

private fun moneyStyle(size: MoneySize): TextStyle = when (size) {
    MoneySize.HERO -> TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = "tnum",
    )

    MoneySize.LEAD -> TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        fontFeatureSettings = "tnum",
    )

    MoneySize.ROW -> TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 17.sp,
        fontFeatureSettings = "tnum",
    )

    MoneySize.SUPPORT -> TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        fontFeatureSettings = "tnum",
    )
}

/**
 * "THIS WEEK" over a run of expenses: small, wide and quiet, as the reference draws it.
 *
 * Uppercased here rather than written that way, so the words a screen reader gets are the words
 * that were meant.
 */
@Composable
fun DateBucketHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 0.8.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** The accent, for a link-like action. */
val accent: Color
    @Composable get() = MaterialTheme.colorScheme.primary
