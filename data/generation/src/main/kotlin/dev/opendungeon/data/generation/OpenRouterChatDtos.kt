package dev.opendungeon.data.generation

import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMRequest
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMResponse
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMStreamResponse
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIResponseFormat
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStreamToolCall
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openrouter.models.ProviderPreferences
import ai.koog.prompt.executor.clients.serialization.AdditionalPropertiesFlatteningSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

// `POST /chat/completions`, written from `ChatRequest`, `ChatUsage` and the streaming page of
// https://openrouter.ai/openapi.json (checked 2026-11-26). Names are camelCase here and go out snake_case:
// Koog's client JSON uses JsonNamingStrategy.SnakeCase. Parameters the typed fields do not cover travel in
// [ChatRequestDto.additionalProperties] and are flattened into the body.

@Serializable
internal class ChatRequestDto(
    val messages: List<OpenAIMessage>,
    override val model: String,
    override val stream: Boolean?,
    override val temperature: Double? = null,
    override val topP: Double? = null,
    override val topLogprobs: Int? = null,
    val topK: Int? = null,
    val minP: Double? = null,
    val topA: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val repetitionPenalty: Double? = null,
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val tools: List<OpenAITool>? = null,
    val toolChoice: OpenAIToolChoice? = null,
    val responseFormat: OpenAIResponseFormat? = null,
    val provider: ProviderPreferences? = null,
    val user: String? = null,
    val additionalProperties: Map<String, JsonElement>? = null,
) : OpenAIBaseLLMRequest

internal object ChatRequestSerializer :
    AdditionalPropertiesFlatteningSerializer<ChatRequestDto>(
        ChatRequestDto.serializer(),
        additionalPropertiesField = "additional_properties",
    )

@Serializable
internal class ChatCompletionDto(
    val choices: List<ChoiceDto> = emptyList(),
    override val created: Long = 0L,
    override val id: String = "",
    override val model: String = "",
    val usage: UsageDto? = null,
    val error: ChatErrorDto? = null,
) : OpenAIBaseLLMResponse

@Serializable
internal class ChoiceDto(val message: OpenAIMessage, val finishReason: String? = null)

@Serializable
internal class ChatChunkDto(
    val choices: List<ChunkChoiceDto> = emptyList(),
    override val created: Long = 0L,
    override val id: String = "",
    override val model: String = "",
    /** On the extra accounting chunk OpenRouter sends just before `[DONE]`. */
    val usage: UsageDto? = null,
    /** A failure after the stream was committed; the stream ends with it. */
    val error: ChatErrorDto? = null,
) : OpenAIBaseLLMStreamResponse

@Serializable
internal class ChunkChoiceDto(val delta: DeltaDto = DeltaDto(), val finishReason: String? = null)

@Serializable
internal class DeltaDto(
    val content: String? = null,
    val reasoning: String? = null,
    val toolCalls: List<OpenAIStreamToolCall>? = null,
)

@Serializable
internal class UsageDto(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    /** USD as a JSON number; kept as its literal so it converts to a decimal exactly. */
    val cost: JsonPrimitive? = null,
    val completionTokensDetails: CompletionTokensDetailsDto? = null,
)

@Serializable
internal class CompletionTokensDetailsDto(val reasoningTokens: Int? = null)

/** `code` is a number on chat completions, but a string elsewhere in the API; either is accepted. */
@Serializable
internal class ChatErrorDto(val code: JsonPrimitive? = null, val message: String = "")
