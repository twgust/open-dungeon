package dev.opendungeon.data.generation

import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openrouter.OpenRouterParams
import ai.koog.prompt.executor.clients.openrouter.models.ProviderPreferences
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.ResponseMetaInfo
import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.ModelParameter.Known
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.Sampling
import dev.opendungeon.domain.generation.TokenUsage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

private const val IMAGE_MODALITY = "image"

/**
 * Describes a catalog model to Koog. Capabilities come from the model's own `supported_parameters`,
 * so Koog picks native structured output only where OpenRouter says the model supports it.
 */
internal fun AiModel.toLLModel(): LLModel = LLModel(
    provider = LLMProvider.OpenRouter,
    id = id.value,
    capabilities = buildList {
        add(LLMCapability.Completion)
        if (Known.Temperature in supportedParameters) add(LLMCapability.Temperature)
        if (Known.Tools in supportedParameters) add(LLMCapability.Tools)
        if (Known.ToolChoice in supportedParameters) add(LLMCapability.ToolChoice)
        if (Known.StructuredOutputs in supportedParameters) add(LLMCapability.Schema.JSON.Standard)
        if (Known.Reasoning in supportedParameters || Known.IncludeReasoning in supportedParameters) {
            add(LLMCapability.Thinking)
        }
        if (IMAGE_MODALITY in inputModalities) add(LLMCapability.Vision.Image)
    },
    contextLength = contextLength?.toLong(),
    maxOutputTokens = maxCompletionTokens?.toLong(),
)

/**
 * Request parameters for [model]. A sampling value is only sent when the model lists it in
 * `supported_parameters`; providers reject or silently ignore the rest, and neither is acceptable.
 *
 * @param requireParameters routes only to provider endpoints that honour every parameter sent,
 *   which native structured output depends on.
 */
internal fun Sampling.toOpenRouterParams(
    model: AiModel,
    sessionId: String?,
    requireParameters: Boolean,
): OpenRouterParams {
    val supported = model.supportedParameters

    fun <T> T?.ifSupported(parameter: Known): T? = takeIf { parameter in supported }

    val extra = buildMap<String, JsonElement> {
        sessionId?.let { put("session_id", JsonPrimitive(it)) }
        seed.ifSupported(Known.Seed)?.let { put("seed", JsonPrimitive(it)) }
    }
    return OpenRouterParams(
        temperature = temperature.ifSupported(Known.Temperature),
        maxTokens = maxTokens.ifSupported(Known.MaxTokens),
        topP = topP.ifSupported(Known.TopP),
        topK = topK.ifSupported(Known.TopK),
        minP = minP.ifSupported(Known.MinP),
        frequencyPenalty = frequencyPenalty.ifSupported(Known.FrequencyPenalty),
        presencePenalty = presencePenalty.ifSupported(Known.PresencePenalty),
        repetitionPenalty = repetitionPenalty.ifSupported(Known.RepetitionPenalty),
        additionalProperties = extra.ifEmpty { null },
        provider = if (requireParameters) ProviderPreferences(requireParameters = true) else null,
    )
}

internal fun List<ChatMessage>.toPrompt(id: String, params: OpenRouterParams): Prompt = prompt(id, params) {
    for (message in this@toPrompt) {
        when (message) {
            is ChatMessage.System -> system(message.text)
            is ChatMessage.User -> user(message.text)
            is ChatMessage.Assistant -> assistant(message.text)
        }
    }
}

internal fun ResponseMetaInfo.toTokenUsage(): TokenUsage? {
    val prompt = inputTokensCount ?: return null
    val completion = outputTokensCount ?: return null
    return TokenUsage(promptTokens = prompt, completionTokens = completion)
}
