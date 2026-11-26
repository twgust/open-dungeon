package dev.opendungeon.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors `ListEndpointsResponse` / `PublicEndpoint` in https://openrouter.ai/openapi.json (checked 2026-11-26).
// Only fields the app reads are declared. `status` is left out: its values are not documented.

@Serializable
public data class EndpointsResponseDto(val data: ModelEndpointsDto)

@Serializable
public data class ModelEndpointsDto(val id: String, val endpoints: List<EndpointDto> = emptyList())

@Serializable
public data class EndpointDto(
    @SerialName("provider_name") val providerName: String,
    val tag: String = "",
    @SerialName("context_length") val contextLength: Int? = null,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    val pricing: PricingDto,
    val quantization: String? = null,
    @SerialName("supported_parameters") val supportedParameters: List<String> = emptyList(),
    @SerialName("uptime_last_1d") val uptimeLastDay: Double? = null,
)
