package dev.opendungeon.feature.models.ui

import java.math.BigDecimal
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatsTest {

    @Test
    fun `prices read per million tokens, with cents at least`() {
        assertEquals("$0.20", BigDecimal("0.0000002").perMillionTokens())
        assertEquals("$0.0375", BigDecimal("0.0000000375").perMillionTokens())
        assertEquals("$15.00", BigDecimal("0.000015").perMillionTokens())
        assertEquals("$0.00", BigDecimal.ZERO.perMillionTokens())
    }

    @Test
    fun `a cost keeps every digit OpenRouter gave`() {
        assertEquals("$0.0000981", BigDecimal("0.0000981").cost())
        assertEquals("$1", BigDecimal("1.000").cost())
    }

    @Test
    fun `token counts read the way catalogs print them`() {
        assertEquals("800", 800.tokens())
        assertEquals("8K", 8_192.tokens())
        assertEquals("131K", 131_072.tokens())
        assertEquals("1M", 1_048_576.tokens())
        assertEquals("1.5M", 1_500_000.tokens())
        assertEquals("32K", 32_000.tokens())
    }

    @Test
    fun `slider values show hundredths without noise`() {
        assertEquals("0.7", 0.7.sliderValue())
        assertEquals("1.0", 1.0.sliderValue())
        assertEquals("0.05", 0.05.sliderValue())
        assertEquals("-1.25", (-1.25).sliderValue())
    }

    @Test
    fun `whole numbers are grouped as the language writes them`() {
        assertEquals("8,192", 8_192L.grouped(Locale.US))
        assertEquals("8.192", 8_192L.grouped(Locale.GERMANY))
    }
}
