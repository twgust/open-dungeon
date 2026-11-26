package dev.opendungeon.core.model

import java.math.BigDecimal

/**
 * What a model costs per token, in USD.
 *
 * OpenRouter encodes two sentinels in its price strings: `"-1"` for routers that pick a model at
 * request time, and `"0"` for free variants. They are modelled explicitly so callers cannot sort
 * a router to the top of a "cheapest" list by accident.
 */
public sealed interface ModelPricing {
    public data object Dynamic : ModelPricing

    /** OpenRouter sent prices that are not decimals. The model stays selectable; what it costs is not known. */
    public data object Unknown : ModelPricing

    public data object Free : ModelPricing

    public data class Metered(
        val promptPerToken: BigDecimal,
        val completionPerToken: BigDecimal,
    ) : ModelPricing
}
