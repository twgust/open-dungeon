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
    /** In the catalog's order, which the parameter list keeps. */
    val supportedParameters: Set<ModelParameter>,
    val defaultParameters: DefaultParameters,
    val reasoning: ReasoningSupport?,
    /** Whether the top provider moderates content; `null` when OpenRouter does not say. */
    val isModerated: Boolean?,
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

/** How a reasoning model's thinking can be steered. See https://openrouter.ai/docs/guides/best-practices/reasoning-tokens.md. */
public data class ReasoningSupport(
    /** Reasoning cannot be turned off. */
    val mandatory: Boolean,
    val enabledByDefault: Boolean,
    /** Highest effort first. Empty when the model offers no choice of effort, only on and off. */
    val supportedEfforts: List<ReasoningEffort>,
    /** Pre-selected when reasoning is turned on. Never [ReasoningEffort.None]: that means off by default. */
    val defaultEffort: ReasoningEffort?,
    /** The model takes a token budget for reasoning (`reasoning.max_tokens`). */
    val supportsBudget: Boolean,
)
