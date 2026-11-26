package dev.opendungeon.data.generation

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterParams
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.buildStreamFrameFlow
import ai.koog.utils.time.KoogClock
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Koog's client for OpenRouter's chat completions, on Koog's own OpenAI-compatible base: message
 * conversion, tools, structured output and the event stream stay Koog's. Ours are the request and
 * response types, because Koog 1.3's `OpenRouterLLMClient` drops two things the stream carries:
 * `usage.cost` on the final accounting chunk, and the `error` object of a failure after the stream began.
 *
 * Usage beyond token counts travels to the generator in [ResponseMetaInfo.metadata], under [MetaKeys].
 * So does an error after the stream began: the stream then ends normally with the `error` finish reason,
 * so every delta before it still reaches the collector. A completion that carries an error throws
 * [OpenRouterFailure].
 */
internal class OpenRouterChatClient(
    settings: OpenRouterClientSettings,
    httpClient: KoogHttpClient,
    clock: KoogClock = KoogClock.System,
) : AbstractOpenAILLMClient<ChatCompletionDto, ChatChunkDto>(
        settings = settings,
        httpClient = httpClient,
        clock = clock,
        logger = logger,
        toolsConverter = OpenAICompatibleToolDescriptorSchemaGenerator(),
    ) {

    override val clientName: String = CLIENT_NAME

    override fun llmProvider(): LLMProvider = LLMProvider.OpenRouter

    override fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean,
    ): String {
        val openRouter = params as? OpenRouterParams
        val request = ChatRequestDto(
            messages = messages,
            model = model.id,
            stream = stream,
            temperature = params.temperature,
            topP = openRouter?.topP,
            topLogprobs = openRouter?.topLogprobs,
            topK = openRouter?.topK,
            minP = openRouter?.minP,
            topA = openRouter?.topA,
            frequencyPenalty = openRouter?.frequencyPenalty,
            presencePenalty = openRouter?.presencePenalty,
            repetitionPenalty = openRouter?.repetitionPenalty,
            maxTokens = params.maxTokens,
            stop = openRouter?.stop,
            tools = tools,
            toolChoice = toolChoice,
            responseFormat = createResponseFormat(params.schema, model),
            provider = openRouter?.provider,
            user = params.user,
            additionalProperties = params.additionalProperties,
        )
        return json.encodeToString(ChatRequestSerializer, request)
    }

    override fun decodeResponse(data: String): ChatCompletionDto = json.decodeFromString(data)

    override fun decodeStreamingResponse(data: String): ChatChunkDto = json.decodeFromString(data)

    override fun processProviderChatResponse(response: ChatCompletionDto): List<Message.Assistant> {
        response.error?.let { throw it.toFailure() }
        if (response.choices.isEmpty()) throw LLMClientException(clientName, "OpenRouter answered without a choice")
        val metaInfo = response.usage.toMetaInfo()
        return response.choices.map { it.message.toMessageResponse(it.finishReason, metaInfo) }
    }

    override fun processStreamingResponse(response: Flow<ChatChunkDto>): Flow<StreamFrame> = buildStreamFrameFlow {
        var finishReason: String? = null
        var usage: UsageDto? = null
        var error: ChatErrorDto? = null
        response.collect { chunk ->
            chunk.error?.let { error = it }
            chunk.choices.firstOrNull()?.let { choice ->
                choice.delta.reasoning?.takeIf(String::isNotEmpty)?.let { emitReasoningDelta(text = it) }
                choice.delta.content?.takeIf(String::isNotEmpty)?.let { emitTextDelta(it) }
                choice.delta.toolCalls?.forEach { call ->
                    emitToolCallDelta(call.id, call.function?.name, call.function?.arguments, call.index)
                }
                // The accounting chunk repeats the finish reason; it is the same reason, not a second end.
                choice.finishReason?.let { finishReason = it }
            }
            chunk.usage?.let { usage = it }
        }
        emitEnd(if (error != null) FINISH_ERROR else finishReason, usage.toMetaInfo(error))
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        throw UnsupportedOperationException("OpenRouter has no moderation endpoint")

    private fun UsageDto?.toMetaInfo(error: ChatErrorDto? = null): ResponseMetaInfo = ResponseMetaInfo.create(
        clock,
        totalTokensCount = this?.totalTokens,
        inputTokensCount = this?.promptTokens,
        outputTokensCount = this?.completionTokens,
        metadata = buildJsonObject {
            this@toMetaInfo?.cost?.let { put(MetaKeys.COST, it) }
            this@toMetaInfo?.completionTokensDetails?.reasoningTokens?.let { put(MetaKeys.REASONING_TOKENS, it) }
            error?.code?.intOrNull?.let { put(MetaKeys.ERROR_STATUS, it) }
            error?.let { put(MetaKeys.ERROR_MESSAGE, it.message) }
        }.takeIf(JsonObject::isNotEmpty),
    )

    private fun ChatErrorDto.toFailure() = OpenRouterFailure(status = code?.intOrNull, message = message)

    /** Keys in [ResponseMetaInfo.metadata] this client fills. */
    object MetaKeys {
        const val COST: String = "cost"
        const val REASONING_TOKENS: String = "reasoning_tokens"

        /** The status-like code of an error after the stream began, when OpenRouter gave one. */
        const val ERROR_STATUS: String = "error_status"
        const val ERROR_MESSAGE: String = "error_message"
    }

    private companion object {
        const val CLIENT_NAME = "OpenRouter"

        /** The finish reason OpenRouter sets on the chunk that carries a mid-stream error. */
        const val FINISH_ERROR = "error"
        val logger = KotlinLogging.logger {}
    }
}

/**
 * A failure OpenRouter reported inside a 200 response, with the HTTP-like status of its error object
 * when it gave one.
 */
internal class OpenRouterFailure(val status: Int?, override val message: String) : RuntimeException(message)
