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
import dev.opendungeon.core.model.ModelParameter
import dev.opendungeon.core.model.ModelParameter.Known
import dev.opendungeon.core.model.Reasoning
import dev.opendungeon.core.model.ReasoningEffort
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.Usage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val IMAGE_MODALITY = "image"

private val ReasoningParameters = setOf(Known.Reasoning, Known.ReasoningEffort, Known.IncludeReasoning)

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
        if (supportedParameters.any { it in ReasoningParameters }) {
            add(LLMCapability.Thinking)
        }
        if (IMAGE_MODALITY in inputModalities) add(LLMCapability.Vision.Image)
    },
    contextLength = contextLength?.toLong(),
    maxOutputTokens = maxCompletionTokens?.toLong(),
)

/**
 * Request parameters for [model]. A tuned value is only sent when the model lists its parameter in
 * `supported_parameters`; providers reject or silently ignore the rest, and neither is acceptable.
 *
 * Whenever a tuned value goes out, `provider.require_parameters` goes with it, so OpenRouter routes only
 * to providers that honour every value sent instead of one that drops some of them.
 *
 * @param requireParameters routes to providers that honour every parameter sent even with nothing
 *   tuned, which native structured output depends on.
 */
internal fun Tuning.toOpenRouterParams(
    model: AiModel,
    sessionId: String?,
    requireParameters: Boolean,
): OpenRouterParams {
    val supported = model.supportedParameters

    fun <T> T?.ifSupported(parameter: Known): T? = takeIf { parameter in supported }

    // `max_tokens` is deprecated in favour of `max_completion_tokens`; the model's list says which it takes.
    val completionLimit = maxTokens.ifSupported(Known.MaxCompletionTokens)
    val tokenLimit = maxTokens.takeIf { completionLimit == null }.ifSupported(Known.MaxTokens)
    val tuned = buildMap<String, JsonElement> {
        completionLimit?.let { put("max_completion_tokens", JsonPrimitive(it)) }
        seed.ifSupported(Known.Seed)?.let { put("seed", JsonPrimitive(it)) }
        verbosity.ifSupported(Known.Verbosity)?.let { put("verbosity", JsonPrimitive(it.wireName)) }
        reasoning?.let { putReasoning(it, supported) }
    }
    val params = OpenRouterParams(
        temperature = temperature.ifSupported(Known.Temperature),
        maxTokens = tokenLimit,
        topP = topP.ifSupported(Known.TopP),
        // 0 turns top-k off, which is what leaving it out does; Koog only accepts 1 and above.
        topK = topK?.takeIf { it > 0 }.ifSupported(Known.TopK),
        minP = minP.ifSupported(Known.MinP),
        topA = topA.ifSupported(Known.TopA),
        frequencyPenalty = frequencyPenalty.ifSupported(Known.FrequencyPenalty),
        presencePenalty = presencePenalty.ifSupported(Known.PresencePenalty),
        repetitionPenalty = repetitionPenalty.ifSupported(Known.RepetitionPenalty),
        stop = stop.ifEmpty { null }.ifSupported(Known.Stop),
    )
    val sendsTuning = tuned.isNotEmpty() || params.carriesSampling()
    val extra = buildMap {
        putAll(tuned)
        sessionId?.let { put("session_id", JsonPrimitive(it)) }
    }
    return params.copy(
        additionalProperties = extra.ifEmpty { null },
        provider = if (requireParameters || sendsTuning) ProviderPreferences(requireParameters = true) else null,
    )
}

/**
 * The `reasoning` object when the model lists it, else the older `reasoning_effort` string. A budget has
 * no `reasoning_effort` form, and neither has "on at the model's default", so those send nothing there.
 */
private fun MutableMap<String, JsonElement>.putReasoning(reasoning: Reasoning, supported: Set<ModelParameter>) {
    when {
        Known.Reasoning in supported -> put(
            "reasoning",
            when (reasoning) {
                Reasoning.Off -> buildJsonObject { put("enabled", false) }
                is Reasoning.On -> buildJsonObject {
                    val effort = reasoning.effort
                    if (effort != null) put("effort", effort.wireName) else put("enabled", true)
                }
                is Reasoning.Budget -> buildJsonObject { put("max_tokens", reasoning.maxTokens) }
            },
        )
        Known.ReasoningEffort in supported -> when (reasoning) {
            Reasoning.Off -> ReasoningEffort.None
            is Reasoning.On -> reasoning.effort
            is Reasoning.Budget -> null
        }?.let { put("reasoning_effort", JsonPrimitive(it.wireName)) }
    }
}

private fun OpenRouterParams.carriesSampling(): Boolean = listOf(
    temperature,
    maxTokens,
    topP,
    topK,
    minP,
    topA,
    frequencyPenalty,
    presencePenalty,
    repetitionPenalty,
    stop,
).any { it != null }

internal fun List<ChatMessage>.toPrompt(id: String, params: OpenRouterParams): Prompt = prompt(id, params) {
    for (message in this@toPrompt) {
        when (message) {
            is ChatMessage.System -> system(message.text)
            is ChatMessage.User -> user(message.text)
            is ChatMessage.Assistant -> assistant(message.text)
        }
    }
}

/** Token counts and what OpenRouter charged, as [OpenRouterChatClient] recorded them; `null` without counts. */
internal fun ResponseMetaInfo.toUsage(): Usage? {
    val prompt = inputTokensCount ?: return null
    val completion = outputTokensCount ?: return null
    val extras = metadata
    return Usage(
        promptTokens = prompt,
        completionTokens = completion,
        reasoningTokens = extras?.get(OpenRouterChatClient.MetaKeys.REASONING_TOKENS)?.jsonPrimitive?.intOrNull,
        cost = extras?.get(OpenRouterChatClient.MetaKeys.COST)?.jsonPrimitive?.content?.toBigDecimalOrNull(),
    )
}

/** The error [OpenRouterChatClient] recorded for a stream that broke off, if it recorded one. */
internal fun ResponseMetaInfo.reportedFailure(): OpenRouterFailure? {
    val extras = metadata ?: return null
    val message = extras[OpenRouterChatClient.MetaKeys.ERROR_MESSAGE]?.jsonPrimitive?.content ?: return null
    return OpenRouterFailure(
        status = extras[OpenRouterChatClient.MetaKeys.ERROR_STATUS]?.jsonPrimitive?.intOrNull,
        message = message,
    )
}
