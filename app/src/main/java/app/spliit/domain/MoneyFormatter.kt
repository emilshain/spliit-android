package app.spliit.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Formats and parses the integer minor units the API deals in.
 *
 * A group's currency is a free-text symbol — "$", "CHF", "kr" — and only newer groups also carry
 * an ISO code. The reference formats with the user's own locale for grouping, decimal separator
 * and symbol placement, and substitutes the group's symbol into that; so does this.
 *
 * The digit count behind the decimal point comes from the ISO code: two nearly everywhere, none
 * for the yen, three for the Gulf dinars. A group with a bare symbol is assumed to be in
 * hundredths, because that is what it was stored as.
 */
class MoneyFormatter(
    val currencySymbol: String,
    val currencyCode: String? = null,
    val locale: Locale = Locale.getDefault(),
    /** Set by [plain]: the precision is already known, and there is no symbol to place. */
    overrideDigits: Int? = null,
) {

    val minorUnitDigits: Int = overrideDigits ?: minorUnitDigitsFor(currencyCode)

    private val currencyFormat: DecimalFormat = run {
        // A currency instance, not a number one: its pattern carries the ¤ that places the symbol
        // where this locale puts money, and setting the currency is what fixes the fraction
        // digits for a yen or a dinar.
        val format = NumberFormat.getCurrencyInstance(locale) as DecimalFormat

        val resolved = if (currencyCode != null && currencyCode.length == 3) {
            runCatching { Currency.getInstance(currencyCode) }.getOrNull()
        } else {
            null
        }
        if (resolved != null) {
            // `setCurrency` on a format built from a fixed pattern leaves the two fraction digits
            // that pattern pinned, so the currency's own precision is applied by hand — a yen
            // has none, and `1234` is ¥1,234 rather than ¥12.34.
            format.currency = resolved
        }
        format.minimumFractionDigits = minorUnitDigits
        format.maximumFractionDigits = minorUnitDigits

        // The group's own symbol, in the locale's placement and separators.
        val symbols = format.decimalFormatSymbols
        symbols.currencySymbol = currencySymbol
        format.decimalFormatSymbols = symbols
        format.roundingMode = RoundingMode.HALF_UP
        format
    }

    /** The amount with its symbol, in the user's own conventions. */
    fun string(minorUnits: Int): String =
        currencyFormat.format(decimal(minorUnits))

    /** The amount without a symbol — for input fields and axis labels. */
    fun plainString(minorUnits: Int): String = plainFormat(locale, minorUnitDigits).format(decimal(minorUnits))

    private fun decimal(minorUnits: Int): BigDecimal =
        BigDecimal.valueOf(minorUnits.toLong()).movePointLeft(minorUnitDigits)

    companion object {

        /** A formatter with no currency of its own, for input fields in a known precision. */
        fun plain(minorUnitDigits: Int, locale: Locale = Locale.getDefault()): MoneyFormatter =
            MoneyFormatter("", null, locale, minorUnitDigits)

        fun minorUnitDigitsFor(code: String?, locale: Locale = Locale.getDefault()): Int {
            if (code == null || code.length != 3) return 2
            return runCatching { Currency.getInstance(code).defaultFractionDigits }
                .getOrNull()
                ?.takeIf { it >= 0 }
                ?: 2
        }

        /**
         * Parses what someone typed into an amount field back into minor units.
         *
         * Accepts the locale's decimal separator as well as a plain dot, since keyboards and
         * pasted values disagree often enough to matter.
         *
         * @param minorUnitDigits what to scale by. The default suits a share count or a
         *   percentage, which the protocol scales by 100 whatever the group is denominated in.
         */
        fun minorUnits(
            text: String,
            locale: Locale = Locale.getDefault(),
            minorUnitDigits: Int = 2,
        ): Int? {
            val value = number(text, locale) ?: return null
            val scaled = value.movePointRight(minorUnitDigits)
            if (scaled > BigDecimal.valueOf(Int.MAX_VALUE.toLong()) ||
                scaled < BigDecimal.valueOf(Int.MIN_VALUE.toLong())
            ) {
                return null
            }
            return scaled.setScale(0, RoundingMode.HALF_UP).toInt()
        }

        /** The number or arithmetic expression someone typed, unscaled. */
        fun number(text: String, locale: Locale = Locale.getDefault()): BigDecimal? {
            val separator = decimalSeparator(locale)
            var cleaned = text.trim()
            cleaned = cleaned.replace(separator, ".")
            cleaned = cleaned.replace(",", ".")
            cleaned = cleaned
                .replace("×", "*")
                .replace("÷", "/")
                .replace("−", "-")
            cleaned = cleaned.filterNot { Character.getType(it) == Character.CURRENCY_SYMBOL.toInt() }

            if (cleaned.isEmpty() || cleaned.length > 512) return null
            return AmountExpression(cleaned).parse()
        }

        /**
         * Resolves a calculation, and leaves plain numbers and unfinished input as they were:
         * typing "12" should not become "12.00" under the caret.
         */
        fun calculatedText(
            text: String,
            locale: Locale = Locale.getDefault(),
            minorUnitDigits: Int = 2,
        ): String? {
            if (text.none { it in "+-*/×÷−()" }) return null
            val value = minorUnits(text, locale, minorUnitDigits) ?: return null
            return plain(minorUnitDigits, locale).plainString(value)
        }

        /**
         * A rate shown at the precision it arrived with, up to six places — enough for the
         * currencies quoted in thousands to a euro — and never grouped, since the field it lands
         * in is parsed back as a plain number.
         */
        fun text(rate: BigDecimal, locale: Locale = Locale.getDefault()): String {
            val format = DecimalFormat("0.######", DecimalFormatSymbols(locale))
            format.isGroupingUsed = false
            format.roundingMode = RoundingMode.HALF_UP
            return format.format(rate)
        }

        fun decimalSeparator(locale: Locale): String =
            DecimalFormatSymbols(locale).decimalSeparator.toString()

        private fun plainFormat(locale: Locale, digits: Int): DecimalFormat {
            val pattern = if (digits <= 0) "0" else "0." + "0".repeat(digits)
            return DecimalFormat(pattern, DecimalFormatSymbols(locale)).apply {
                isGroupingUsed = false
                minimumFractionDigits = digits
                maximumFractionDigits = digits
                roundingMode = RoundingMode.HALF_UP
            }
        }
    }
}

/**
 * Decimal arithmetic, with multiplication and division before addition and subtraction.
 *
 * The parser must consume the entire input, so a typo can never save a partial amount.
 */
private class AmountExpression(private val text: String) {

    private var index = 0

    fun parse(): BigDecimal? {
        val value = sum() ?: return null
        return if (index == text.length) value else null
    }

    private fun sum(): BigDecimal? {
        var value = product() ?: return null
        while (true) {
            when (peek()) {
                '+' -> {
                    index++
                    value += product() ?: return null
                }

                '-' -> {
                    index++
                    value -= product() ?: return null
                }

                else -> return value
            }
        }
    }

    private fun product(): BigDecimal? {
        var value = operand() ?: return null
        while (true) {
            when (peek()) {
                '*' -> {
                    index++
                    value *= operand() ?: return null
                }

                '/' -> {
                    index++
                    val divisor = operand() ?: return null
                    if (divisor.signum() == 0) return null
                    value = value.divide(divisor, 34, RoundingMode.HALF_UP)
                }

                else -> return value
            }
        }
    }

    private fun operand(): BigDecimal? {
        val negative = consume('-')
        if (!negative) consume('+')

        val value = if (consume('(')) {
            val inner = sum() ?: return null
            if (!consume(')')) return null
            inner
        } else {
            val start = index
            while (index < text.length && (text[index].isDigit() || text[index] == '.')) index++
            val token = text.substring(start, index)
            if (token.isEmpty() ||
                token.count { it == '.' } > 1 ||
                token.none { it.isDigit() }
            ) {
                return null
            }
            runCatching { BigDecimal(token) }.getOrNull() ?: return null
        }

        return if (negative) value.negate() else value
    }

    private fun peek(): Char? = if (index < text.length) text[index] else null

    private fun consume(c: Char): Boolean {
        if (peek() == c) {
            index++
            return true
        }
        return false
    }
}
