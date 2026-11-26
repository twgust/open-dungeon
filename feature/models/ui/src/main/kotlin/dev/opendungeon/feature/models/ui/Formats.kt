package dev.opendungeon.feature.models.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

private val Million = BigDecimal(1_000_000)
private const val THOUSAND = 1_000
private const val MILLION = 1_000_000
private const val MIN_PRICE_DECIMALS = 2

/** A per-token price as dollars per million tokens, the way OpenRouter quotes it: `$0.20`, `$0.0375`. */
internal fun BigDecimal.perMillionTokens(): String = dollars(multiply(Million), minDecimals = MIN_PRICE_DECIMALS)

/** What a request cost, to the last digit OpenRouter gave: `$0.000123`. */
internal fun BigDecimal.cost(): String = dollars(this, minDecimals = 0)

private fun dollars(amount: BigDecimal, minDecimals: Int): String {
    val exact = amount.stripTrailingZeros()
    val shown = if (exact.scale() < minDecimals) exact.setScale(minDecimals) else exact
    return "$" + shown.toPlainString()
}

/** A token count the way catalogs print it: `131K`, `1M`, `1.5M`, `800`. */
internal fun Int.tokens(): String = when {
    this >= MILLION -> BigDecimal(this).divide(BigDecimal(MILLION), 1, RoundingMode.HALF_UP).stripTrailingZeros()
        .toPlainString() + "M"
    this >= THOUSAND -> "${(this + THOUSAND / 2) / THOUSAND}K"
    else -> toString()
}

/** A whole number with grouping, as the device's language writes it. */
internal fun Long.grouped(locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(this)

/** A slider value: two decimals, without trailing zeros past the first. */
internal fun Double.sliderValue(): String =
    BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().let {
        if (it.scale() < 1) it.setScale(1) else it
    }.toPlainString()
