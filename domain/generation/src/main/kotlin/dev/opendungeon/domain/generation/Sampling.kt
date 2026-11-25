package dev.opendungeon.domain.generation

/**
 * Sampling settings for one request. `null` leaves the choice to the provider.
 *
 * A value is only sent when the target model lists the matching parameter in its
 * `supported_parameters`; everything else is dropped by the generator, never sent and rejected.
 */
public data class Sampling(
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val minP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val repetitionPenalty: Double? = null,
    val maxTokens: Int? = null,
    val seed: Long? = null,
) {
    init {
        require(temperature == null || temperature in 0.0..2.0) { "temperature must be in 0..2, was $temperature" }
        require(topP == null || topP in 0.0..1.0) { "topP must be in 0..1, was $topP" }
        require(topK == null || topK >= 1) { "topK must be >= 1, was $topK" }
        require(minP == null || minP in 0.0..1.0) { "minP must be in 0..1, was $minP" }
        require(frequencyPenalty == null || frequencyPenalty in -2.0..2.0) { "frequencyPenalty must be in -2..2" }
        require(presencePenalty == null || presencePenalty in -2.0..2.0) { "presencePenalty must be in -2..2" }
        require(repetitionPenalty == null || repetitionPenalty in 0.0..2.0) { "repetitionPenalty must be in 0..2" }
        require(maxTokens == null || maxTokens >= 1) { "maxTokens must be >= 1, was $maxTokens" }
    }

    public companion object {
        public val ProviderDefaults: Sampling = Sampling()
    }
}
