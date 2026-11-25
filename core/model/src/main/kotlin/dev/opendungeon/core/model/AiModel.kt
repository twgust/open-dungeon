package dev.opendungeon.core.model

/** A model the player can pick, as reported by OpenRouter's catalog for their key. */
public data class AiModel(
    val id: ModelId,
    val name: String,
    val description: String?,
    val contextLength: Int?,
    val maxCompletionTokens: Int?,
    val pricing: ModelPricing,
    val inputModalities: Set<String>,
    val outputModalities: Set<String>,
    val supportedParameters: Set<ModelParameter>,
    val defaultParameters: DefaultParameters,
    val reasoning: ReasoningSupport?,
)

/** OpenRouter model slug, e.g. `anthropic/claude-opus-4.5` or a variant such as `…:free`. */
@JvmInline
public value class ModelId(public val value: String)

/** Provider-recommended sampling defaults. `null` means the model states no default. */
public data class DefaultParameters(
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val repetitionPenalty: Double? = null,
)

public data class ReasoningSupport(
    val mandatory: Boolean,
    val enabledByDefault: Boolean,
    val supportedEfforts: List<String>,
    val defaultEffort: String?,
)
