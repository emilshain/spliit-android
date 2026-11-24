package app.spliit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * Money is integers on the wire, and every number the app shows or saves goes through here; a
 * currency with no minor unit, or a decimal comma, is where that breaks.
 */
class MoneyFormatterTest {

    private val us = Locale.US

    @Test
    fun `formatting places the symbol and groups in the locale's own way`() {
        val formatter = MoneyFormatter("€", "EUR", us)

        assertEquals("€12.34", formatter.string(minorUnits = 1234))
        assertEquals("€1,234.50", formatter.string(minorUnits = 123_450))
    }

    @Test
    fun `a currency with no minor unit keeps the digits that were stored`() {
        val yen = MoneyFormatter("¥", "JPY", us)

        assertEquals(0, yen.minorUnitDigits)
        assertEquals("¥1,234", yen.string(minorUnits = 1234))
    }

    @Test
    fun `a group with only a symbol is assumed to count in hundredths`() {
        val formatter = MoneyFormatter("kr", null, us)

        assertEquals(2, formatter.minorUnitDigits)
        assertEquals("kr5.00", formatter.string(minorUnits = 500))
    }

    @Test
    fun `a three digit currency is read from its ISO code`() {
        assertEquals(3, MoneyFormatter.minorUnitDigitsFor("BHD", us))
        assertEquals(0, MoneyFormatter.minorUnitDigitsFor("KRW", us))
        assertEquals(2, MoneyFormatter.minorUnitDigitsFor(null, us))
        assertEquals(2, MoneyFormatter.minorUnitDigitsFor("nonsense", us))
    }

    @Test
    fun `parsing scales by the currency's precision, not always by a hundred`() {
        assertEquals(1234, MoneyFormatter.minorUnits("12.34", us, 2))
        assertEquals(1234, MoneyFormatter.minorUnits("1234", us, 0))
        assertEquals(123_400, MoneyFormatter.minorUnits("123.4", us, 3))
    }

    @Test
    fun `parsing accepts a decimal comma and pasted symbols`() {
        assertEquals(1250, MoneyFormatter.minorUnits("12,50", us, 2))
        assertEquals(1250, MoneyFormatter.minorUnits(" €12.50 ", us, 2))
    }

    @Test
    fun `arithmetic is resolved, with multiplication before addition`() {
        assertEquals(1700, MoneyFormatter.minorUnits("2*5+7", us, 2))
        assertEquals(1050, MoneyFormatter.minorUnits("(2+1)*3.5", us, 2))
        assertEquals(450, MoneyFormatter.minorUnits("10-5.5", us, 2))
    }

    @Test
    fun `an unusable expression is not a number`() {
        assertNull(MoneyFormatter.minorUnits("12+", us, 2))
        assertNull(MoneyFormatter.minorUnits("abc", us, 2))
        assertNull(MoneyFormatter.minorUnits("1/0", us, 2))
        assertNull(MoneyFormatter.minorUnits("1.2.3", us, 2))
    }

    @Test
    fun `rounding is half up, as the server expects`() {
        assertEquals(1235, MoneyFormatter.minorUnits("12.345", us, 2))
        assertEquals(-1235, MoneyFormatter.minorUnits("-12.345", us, 2))
    }

    @Test
    fun `plain text has no symbol and no grouping`() {
        val formatter = MoneyFormatter.plain(2, us)

        assertEquals("1234.50", formatter.plainString(123_450))
        assertEquals("-0.05", formatter.plainString(-5))
    }

    @Test
    fun `a calculation entered in a field is resolved, a plain number is left alone`() {
        assertEquals("17.00", MoneyFormatter.calculatedText("2*5+7", us, 2))
        assertNull(MoneyFormatter.calculatedText("12", us, 2))
        assertNull(MoneyFormatter.calculatedText("12+", us, 2))
    }

    @Test
    fun `a rate keeps its own precision, up to six places`() {
        assertEquals("1.0575", MoneyFormatter.text(rate = java.math.BigDecimal("1.0575"), locale = us))
        assertEquals("1234.5", MoneyFormatter.text(rate = java.math.BigDecimal("1234.500000"), locale = us))
    }
}
