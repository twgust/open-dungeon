package dev.opendungeon.feature.models.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.opendungeon.core.model.ModelPricing
import dev.opendungeon.core.model.ModelSort
import dev.opendungeon.core.model.ReasoningEffort
import dev.opendungeon.core.model.Verbosity
import dev.opendungeon.feature.models.domain.Setting

@Composable
internal fun ModelPricing.label(): String = when (this) {
    ModelPricing.Free -> stringResource(R.string.price_free)
    ModelPricing.Dynamic -> stringResource(R.string.price_dynamic)
    ModelPricing.Unknown -> stringResource(R.string.price_unknown)
    is ModelPricing.Metered ->
        stringResource(R.string.price_metered, promptPerToken.perMillionTokens(), completionPerToken.perMillionTokens())
}

@Composable
internal fun contextLabel(tokens: Int): String = stringResource(R.string.context_tokens, tokens.tokens())

@Composable
internal fun ModelSort?.label(): String = stringResource(
    when (this) {
        null -> R.string.sort_default
        ModelSort.MostPopular -> R.string.sort_popular
        ModelSort.TopWeekly -> R.string.sort_trending
        ModelSort.Newest -> R.string.sort_newest
        ModelSort.Intelligence -> R.string.sort_intelligence
        ModelSort.PriceLowToHigh -> R.string.sort_cheapest
        ModelSort.PriceHighToLow -> R.string.sort_priciest
        ModelSort.ContextHighToLow -> R.string.sort_context
        ModelSort.Throughput -> R.string.sort_throughput
        ModelSort.Latency -> R.string.sort_latency
        ModelSort.Multilingual -> R.string.sort_multilingual
    },
)

/** A modality's name; one OpenRouter adds later shows as it is spelled on the wire. */
@Composable
internal fun modalityLabel(modality: String): String = when (modality) {
    "image" -> stringResource(R.string.modality_image)
    "audio" -> stringResource(R.string.modality_audio)
    "file" -> stringResource(R.string.modality_file)
    "video" -> stringResource(R.string.modality_video)
    else -> modality
}

@Composable
internal fun Setting.label(): String = stringResource(
    when (this) {
        Setting.Temperature -> R.string.setting_temperature
        Setting.TopP -> R.string.setting_top_p
        Setting.TopK -> R.string.setting_top_k
        Setting.MinP -> R.string.setting_min_p
        Setting.TopA -> R.string.setting_top_a
        Setting.FrequencyPenalty -> R.string.setting_frequency_penalty
        Setting.PresencePenalty -> R.string.setting_presence_penalty
        Setting.RepetitionPenalty -> R.string.setting_repetition_penalty
        Setting.MaxTokens -> R.string.setting_max_tokens
        Setting.Seed -> R.string.setting_seed
        Setting.Stop -> R.string.setting_stop
        Setting.Verbosity -> R.string.setting_verbosity
        Setting.Reasoning -> R.string.setting_reasoning
    },
)

@Composable
internal fun Verbosity.label(): String = stringResource(
    when (this) {
        Verbosity.Low -> R.string.verbosity_low
        Verbosity.Medium -> R.string.verbosity_medium
        Verbosity.High -> R.string.verbosity_high
        Verbosity.ExtraHigh -> R.string.verbosity_xhigh
        Verbosity.Max -> R.string.verbosity_max
    },
)

/** An effort's name; one OpenRouter adds later shows as it is spelled on the wire. */
@Composable
internal fun ReasoningEffort.label(): String = when (wireName) {
    "max" -> stringResource(R.string.effort_max)
    "xhigh" -> stringResource(R.string.effort_xhigh)
    "high" -> stringResource(R.string.effort_high)
    "medium" -> stringResource(R.string.effort_medium)
    "low" -> stringResource(R.string.effort_low)
    "minimal" -> stringResource(R.string.effort_minimal)
    else -> wireName
}
