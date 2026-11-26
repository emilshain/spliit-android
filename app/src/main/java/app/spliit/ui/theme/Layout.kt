package app.spliit.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The spacing and corner scale the screens share.
 *
 * The app draws its own cards rather than using `Card`, so the numbers that make a section read
 * as one stack lived as literals in each screen — and drifted. These are the only values a screen
 * should pass to `RoundedCornerShape` or to the padding around a card:
 *
 * - [CardCorner] — the corner a card owns at the end of a stack.
 * - [AdjacentCorner] — the corner it shares with the card above or below it.
 * - [CardGap] — the gap between two cards in a stack. Small on purpose: the small corners and the
 *   2dp gap are what make a run of cards read as one grouped list instead of loose tiles.
 * - [ScreenGutter] — the horizontal inset of any card from the screen edge.
 * - [CardPadding] — the padding inside a card.
 *
 * Material's own expressive "large increased" corner is 20dp; 22 is that rounded up with the rest
 * of [SpliitShapes], which keeps a card and the FAB in the same family.
 */
object SpliitDimens {
    val CardCorner = 22.dp
    val AdjacentCorner = 10.dp
    val CardGap = 2.dp
    val ScreenGutter = 16.dp
    val CardPadding = 16.dp

    /** The margin under the tab bar, between the pill and the top of a tab's content. */
    val TabBarGap = 12.dp

    /** A section title above its cards, aligned to the card's own text rather than its edge. */
    val SectionLabelStart = 20.dp
    val SectionLabelTop = 20.dp
    val SectionLabelBottom = 4.dp
}
