package dev.opendungeon.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Mirrors `ModelsListResponse` / `Model` in https://openrouter.ai/openapi.json (checked 2026-11-25).
// Only fields the app reads are declared; unknown fields are ignored. Fields the spec marks
// required still get defaults where losing one model is worse than a missing detail, because
// OpenRouter ships schema additions without notice (https://openrouter.ai/docs/api_reference/versioning).

@Serializable
public data class ModelsPageDto(
    val data: List<ModelDto>,
    @SerialName("total_count") val totalCount: Int? = null,
    val links: PageLinksDto? = null,
)

@Serializable
public data class PageLinksDto(
    /** Relative URL of the next page, or `null` on the last page. */
    val next: String? = null,
)

@Serializable
public data class ModelDto(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("context_length") val contextLength: Int? = null,
    val architecture: ArchitectureDto? = null,
    val pricing: PricingDto,
    @SerialName("top_provider") val topProvider: TopProviderDto? = null,
    @SerialName("supported_parameters") val supportedParameters: List<String> = emptyList(),
    @SerialName("default_parameters") val defaultParameters: DefaultParametersDto? = null,
    val reasoning: ModelReasoningDto? = null,
    /** Request paths that serve the model, such as `/api/v1/chat/completions`. Absent on older responses. */
    val endpoints: List<String>? = null,
)

@Serializable
public data class ArchitectureDto(
    @SerialName("input_modalities") val inputModalities: List<String> = emptyList(),
    @SerialName("output_modalities") val outputModalities: List<String> = emptyList(),
)

/** Prices are decimal strings in USD per token. `"-1"` marks dynamic routers, `"0"` free variants. */
@Serializable
public data class PricingDto(val prompt: String, val completion: String)

@Serializable
public data class TopProviderDto(
    @SerialName("context_length") val contextLength: Int? = null,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    @SerialName("is_moderated") val isModerated: Boolean? = null,
)

@Serializable
public data class DefaultParametersDto(
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Double? = null,
    @SerialName("presence_penalty") val presencePenalty: Double? = null,
    @SerialName("repetition_penalty") val repetitionPenalty: Double? = null,
)

@Serializable
public data class ModelReasoningDto(
    val mandatory: Boolean = false,
    @SerialName("default_enabled") val defaultEnabled: Boolean = false,
    /**
     * Highest effort first. The spec tells three cases apart: a list, JSON `null` (every effort is
     * accepted) and absent (no choice of effort). Kotlin `null` here is absent; JSON `null` is `JsonNull`.
     */
    @SerialName("supported_efforts") val supportedEfforts: JsonElement? = null,
    @SerialName("default_effort") val defaultEffort: String? = null,
    @SerialName("supports_max_tokens") val supportsMaxTokens: Boolean = false,
)
