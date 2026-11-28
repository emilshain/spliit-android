package app.spliit.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The spacing and corner scale the screens share.
 *
 * The numbers are Material 3's spacing tokens rather than values picked per screen: the system is
 * an 8dp scale where `space100` is 8dp, with a handful of nested units (2, 4, 6, 10, 14) that
 * Material also defines. Naming them after the tokens is what keeps a gap on one page the same
 * gap on every other one — the value comes from the scale, not from what looked right once.
 *
 * The screens used to pad cards themselves, so a stack of cards had a 2dp gap on the expenses and
 * activities, 4dp on the balances and 6dp on the totals. A stack is now built with
 * [stackedCardShape] for the corners and [stackedCardPadding] for the gap, and every stack in the
 * app uses those two: adjacent cards are exactly [CardGap] apart, everywhere.
 */
object SpliitDimens {

    // Material's system spacing tokens. `Space100` is the base unit, 8dp.

    val Space0 = 0.dp
    val Space25 = 2.dp
    val Space50 = 4.dp
    val Space75 = 6.dp
    val Space100 = 8.dp
    val Space150 = 12.dp
    val Space175 = 14.dp
    val Space200 = 16.dp
    val Space250 = 20.dp
    val Space300 = 24.dp
    val Space400 = 32.dp

    /**
     * The two corners a card owns, and the two it shares with the card beside it.
     *
     * Material's expressive "large increased" corner is 20dp; 22 is that rounded up with the rest
     * of [SpliitShapes], which keeps a card and the FAB in the same family.
     */
    val CardCorner = 22.dp
    val AdjacentCorner = 10.dp

    /** The gap between two cards in a stack. Material's segmented-list gap: 2dp. */
    val CardGap = Space50

    /** The horizontal inset of any card from the screen edge. */
    val ScreenGutter = Space200

    /** The padding inside a card. */
    val CardPadding = Space200

    /** A tile inside a card: the plate behind a category glyph, a receipt thumbnail. */
    val TileCorner = Space175

    /** A label-sized tag, such as the chips that sit under an expense's title. */
    val ChipCorner = Space100

    /** The inset of a card's contents. Every list row in the app uses these two. */
    val RowPaddingHorizontal = Space200
    // The same as the sides: a card's content sits in an even frame, so no card looks like it is
    // leaning towards one edge.
    val RowPaddingVertical = Space200

    /** The gap between two cards that are separate things, rather than one stack. */
    val CardSpacing = Space150

    /** The corner of the floating add button, until it rounds out into a close. */
    val FabCorner = Space200

    /** The margin under the tab bar, between the pill and the top of a tab's content. */
    val TabBarGap = Space150

    /** A section title above its cards, aligned to the card's own text rather than its edge. */
    val SectionLabelStart = Space250
    val SectionLabelTop = Space250
    val SectionLabelBottom = Space50
}

/**
 * The corner a card takes from its place in a stack.
 *
 * The sides facing air keep the large corner; the sides it shares with its neighbours take the
 * small one, which is what makes two cards read as one grouped list instead of two tiles that
 * happen to be near each other.
 */
fun stackedCardShape(first: Boolean, last: Boolean): RoundedCornerShape = RoundedCornerShape(
    topStart = if (first) SpliitDimens.CardCorner else SpliitDimens.AdjacentCorner,
    topEnd = if (first) SpliitDimens.CardCorner else SpliitDimens.AdjacentCorner,
    bottomStart = if (last) SpliitDimens.CardCorner else SpliitDimens.AdjacentCorner,
    bottomEnd = if (last) SpliitDimens.CardCorner else SpliitDimens.AdjacentCorner,
)

/**
 * The outer inset of one card in a vertical stack.
 *
 * The screen gutter on both sides, and exactly [SpliitDimens.CardGap] between this card and the
 * one above it — no card in a stack pads its own bottom, so the gap is the same number however
 * many cards the stack has, on every page.
 */
fun Modifier.stackedCardPadding(first: Boolean): Modifier = this
    .padding(horizontal = SpliitDimens.ScreenGutter)
    .padding(top = if (first) SpliitDimens.Space0 else SpliitDimens.CardGap)
