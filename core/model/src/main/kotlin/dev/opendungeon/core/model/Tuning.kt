package dev.opendungeon.core.model

/**
 * The parameters the player set for one model. `null` (or an empty [stop]) leaves the choice to the
 * provider: OpenRouter forwards only what a request carries, and omitting a parameter is not the same
 * as sending its conventional default.
 *
 * Ranges are the documented ones (https://openrouter.ai/docs/api_reference/parameters.md), held in
 * [TuningRanges] so the editor and this check agree.
 */
public data class Tuning(
    val temperature: Double? = null,
    val topP: Double? = null,
    /** `0` disables top-k, the same as leaving it unset. */
    val topK: Int? = null,
    val minP: Double? = null,
    val topA: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val repetitionPenalty: Double? = null,
    /** Sent as `max_completion_tokens` or `max_tokens`, whichever the model lists. */
    val maxTokens: Int? = null,
    val seed: Long? = null,
    val stop: List<String> = emptyList(),
    val verbosity: Verbosity? = null,
    val reasoning: Reasoning? = null,
) {
    init {
        temperature.requireIn(TuningRanges.Temperature, "temperature")
        topP.requireIn(TuningRanges.Probability, "topP")
        minP.requireIn(TuningRanges.Probability, "minP")
        topA.requireIn(TuningRanges.Probability, "topA")
        frequencyPenalty.requireIn(TuningRanges.Penalty, "frequencyPenalty")
        presencePenalty.requireIn(TuningRanges.Penalty, "presencePenalty")
        repetitionPenalty.requireIn(TuningRanges.RepetitionPenalty, "repetitionPenalty")
        require(topK == null || topK >= 0) { "topK must be >= 0, was $topK" }
        require(maxTokens == null || maxTokens >= 1) { "maxTokens must be >= 1, was $maxTokens" }
        require(stop.size <= TuningRanges.MAX_STOP_SEQUENCES) { "at most ${TuningRanges.MAX_STOP_SEQUENCES} stops" }
        require(stop.none(String::isEmpty)) { "stop sequences must not be empty" }
    }

    /** True when nothing is set, so a request carries no tuning at all. */
    public val isUntouched: Boolean get() = this == Untouched

    public companion object {
        public val Untouched: Tuning = Tuning()

        private fun Double?.requireIn(range: ClosedFloatingPointRange<Double>, name: String) {
            require(this == null || this in range) { "$name must be in $range, was $this" }
        }
    }
}

/** The documented bounds of each tunable parameter. */
public object TuningRanges {
    public val Temperature: ClosedFloatingPointRange<Double> = 0.0..2.0
    public val Probability: ClosedFloatingPointRange<Double> = 0.0..1.0
    public val Penalty: ClosedFloatingPointRange<Double> = -2.0..2.0
    public val RepetitionPenalty: ClosedFloatingPointRange<Double> = 0.0..2.0
    public const val MAX_STOP_SEQUENCES: Int = 4
}

/** How long and detailed the answer is; `verbosity` on the wire. */
public enum class Verbosity(public val wireName: String) {
    Low("low"),
    Medium("medium"),
    High("high"),
    ExtraHigh("xhigh"),
    Max("max"),
    ;

    public companion object {
        private val byWireName = entries.associateBy(Verbosity::wireName)

        public fun fromWireName(name: String): Verbosity? = byWireName[name]
    }
}

/** Whether a reasoning model thinks before it answers, and how hard. */
public sealed interface Reasoning {
    /** Not allowed for models whose reasoning is mandatory. */
    public data object Off : Reasoning

    /** Thinking at [effort], or at the model's default effort when `null`. */
    public data class On(val effort: ReasoningEffort?) : Reasoning

    /** Thinking for at most [maxTokens], for models that take a budget. */
    public data class Budget(val maxTokens: Int) : Reasoning {
        init {
            require(maxTokens >= 1) { "a reasoning budget must be >= 1 token, was $maxTokens" }
        }
    }
}

/**
 * A reasoning effort such as `high` or `minimal`. Kept as the wire word: each model lists the efforts it
 * accepts, and OpenRouter adds new ones without notice.
 */
@JvmInline
public value class ReasoningEffort(public val wireName: String) {
    public companion object {
        /** Turns reasoning off; never offered as an effort, see [Reasoning.Off]. */
        public val None: ReasoningEffort = ReasoningEffort("none")

        /** Every effort OpenRouter accepts, highest first, for models that set no allowlist of their own. */
        public val All: List<ReasoningEffort> =
            listOf("max", "xhigh", "high", "medium", "low", "minimal").map(::ReasoningEffort)
    }
}
