package app.spliit.domain

import java.util.Currency
import java.util.Locale

/**
 * An ISO-4217 currency, as the platform already knows it.
 *
 * The list, the names, the symbols and the minor-unit precision all come from the JDK, so the
 * picker is translated wherever Android is and there is no currency table in the repo to keep up
 * to date — the same reason the iOS app leans on Foundation.
 */
data class CurrencyInfo(
    /** ISO-4217, uppercase — "CHF". */
    val code: String,
    /** Localised — "Swiss Franc", "franc suisse". */
    val name: String,
    /** What amounts are shown with — "CHF", "$", "kr". */
    val symbol: String,
    /** Digits in the minor unit: two nearly everywhere, none for the yen. */
    val minorUnitDigits: Int,
)

object CurrencyCatalog {

    /** Every currency the platform can name, ordered the way the user's language orders words. */
    fun all(locale: Locale = Locale.getDefault()): List<CurrencyInfo> =
        Currency.getAvailableCurrencies()
            .mapNotNull { make(it, locale) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    /** One currency by code, or null if the platform can't name it. */
    fun named(code: String, locale: Locale = Locale.getDefault()): CurrencyInfo? {
        val trimmed = code.trim().uppercase(Locale.ROOT)
        if (trimmed.isEmpty()) return null
        val currency = runCatching { Currency.getInstance(trimmed) }.getOrNull() ?: return null
        return make(currency, locale)
    }

    /**
     * The currency the device is set to, which is the one a new group most often wants.
     *
     * A locale with no country — the sort a test or a server can have — has no currency, and
     * says so rather than guessing.
     */
    fun device(locale: Locale = Locale.getDefault()): CurrencyInfo? =
        runCatching { Currency.getInstance(locale) }.getOrNull()?.let { make(it, locale) }

    /** The device's own currency first, then the five the web app promotes. */
    fun suggested(locale: Locale = Locale.getDefault()): List<CurrencyInfo> {
        val codes = mutableListOf("USD", "EUR", "GBP", "JPY", "CNY")
        device(locale)?.let { device ->
            codes.remove(device.code)
            codes.add(0, device.code)
        }
        return codes.mapNotNull { named(it, locale) }
    }

    private fun make(currency: Currency, locale: Locale): CurrencyInfo? {
        val code = currency.currencyCode
        if (code.length != 3) return null
        val digits = currency.defaultFractionDigits.takeIf { it >= 0 } ?: 2
        return CurrencyInfo(
            code = code,
            name = currency.getDisplayName(locale),
            symbol = currency.getSymbol(locale),
            minorUnitDigits = digits,
        )
    }
}
