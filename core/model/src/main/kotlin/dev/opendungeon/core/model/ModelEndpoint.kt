package dev.opendungeon.core.model

/**
 * One provider serving a model, from `GET /models/{author}/{slug}/endpoints`. A model's own parameter list
 * is the union over its providers; this is what one of them accepts.
 */
public data class ModelEndpoint(
    val providerName: String,
    /** Routing tag such as `deepinfra/fp4`. */
    val tag: String,
    val contextLength: Int?,
    val maxCompletionTokens: Int?,
    val pricing: ModelPricing,
    val quantization: String?,
    val supportedParameters: Set<ModelParameter>,
    /** Percent of the last day's minutes this provider served well; `null` without enough traffic. */
    val uptimeLastDay: Double?,
)
