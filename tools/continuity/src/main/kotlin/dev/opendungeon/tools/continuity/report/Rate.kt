package dev.opendungeon.tools.continuity.report

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * [hits] out of [total], with a 95% Wilson score interval. A handful of probes is a small sample, and the
 * interval is what says whether two policies really differ or only drew differently.
 */
@Serializable
internal data class Rate(val hits: Int, val total: Int, val share: Double?, val low: Double?, val high: Double?) {

    val isEmpty: Boolean get() = total == 0

    companion object {
        /** z for a two-sided 95% interval. */
        private const val Z = 1.96

        fun of(hits: Int, total: Int): Rate {
            require(total >= 0 && hits in 0..total) { "Not a rate: $hits of $total" }
            if (total == 0) return Rate(hits, total, share = null, low = null, high = null)
            val n = total.toDouble()
            val p = hits / n
            val z2 = Z * Z
            val centre = (p + z2 / (2 * n)) / (1 + z2 / n)
            val margin = Z * sqrt(p * (1 - p) / n + z2 / (4 * n * n)) / (1 + z2 / n)
            return Rate(
                hits,
                total,
                share = p,
                low = (centre - margin).coerceAtLeast(0.0),
                high = (centre + margin).coerceAtMost(1.0),
            )
        }
    }
}
