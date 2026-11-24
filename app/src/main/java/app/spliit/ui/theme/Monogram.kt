package app.spliit.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Someone's initials, in a colour that is theirs.
 *
 * Spliit has no accounts and no avatars, so a participant has only ever been a name in a row.
 * A monogram gives the eye something to find: the same person is the same colour in the
 * balances, in the expense list and in a suggested payment, on every device.
 *
 * Growing with the text keeps the chip in proportion with the name beside it. The cap is what
 * stops it becoming the row: at the largest accessibility size an uncapped circle is wider than
 * the screen, and the name is what the row is actually for.
 */
@Composable
fun Monogram(
    name: String,
    colorIndex: Int,
    size: Dp = 28.dp,
) {
    val fontScale = LocalDensity.current.fontScale
    val side = size * fontScale.coerceAtMost(1.6f)

    Box(
        modifier = Modifier
            .size(side)
            .clip(CircleShape)
            .background(SpliitPalette.monogram(colorIndex)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = MonogramPalette.initials(name),
            color = Color.White,
            fontSize = (side.value * 0.42f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * A participant reduced to nothing but their colour.
 *
 * For places where the names are already written out — a monogram there would repeat the text
 * and shout over it, but a dot ties the row to the same colour language.
 */
@Composable
fun ParticipantDot(position: Int, size: Dp = 8.dp) {
    val fontScale = LocalDensity.current.fontScale
    Box(
        modifier = Modifier
            .size(size * fontScale.coerceAtMost(1.6f))
            .clip(CircleShape)
            .background(SpliitPalette.monogram(MonogramPalette.indexAtPosition(position))),
    )
}
