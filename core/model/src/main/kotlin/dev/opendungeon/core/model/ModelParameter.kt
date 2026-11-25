package dev.opendungeon.core.model

/**
 * A request parameter a model accepts, from the catalog's `supported_parameters`.
 *
 * OpenRouter adds parameters without notice, so unrecognised names survive as [Unknown]
 * instead of failing the whole catalog.
 */
public sealed interface ModelParameter {
    public val wireName: String

    public enum class Known(override val wireName: String) : ModelParameter {
        Temperature("temperature"),
        TopP("top_p"),
        TopK("top_k"),
        MinP("min_p"),
        TopA("top_a"),
        FrequencyPenalty("frequency_penalty"),
        PresencePenalty("presence_penalty"),
        RepetitionPenalty("repetition_penalty"),
        MaxTokens("max_tokens"),
        MaxCompletionTokens("max_completion_tokens"),
        LogitBias("logit_bias"),
        Logprobs("logprobs"),
        TopLogprobs("top_logprobs"),
        Prediction("prediction"),
        Seed("seed"),
        ResponseFormat("response_format"),
        StructuredOutputs("structured_outputs"),
        StrictToolSchemas("strict_tool_schemas"),
        Stop("stop"),
        Tools("tools"),
        ToolChoice("tool_choice"),
        ParallelToolCalls("parallel_tool_calls"),
        IncludeReasoning("include_reasoning"),
        Reasoning("reasoning"),
        ReasoningEffort("reasoning_effort"),
        WebSearchOptions("web_search_options"),
        Verbosity("verbosity"),
    }

    public data class Unknown(override val wireName: String) : ModelParameter

    public companion object {
        private val byWireName = Known.entries.associateBy(Known::wireName)

        public fun fromWireName(name: String): ModelParameter = byWireName[name] ?: Unknown(name)
    }
}
