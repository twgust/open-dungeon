package dev.opendungeon.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors `GET /key` in https://openrouter.ai/openapi.json (checked 2026-11-25).

@Serializable
public data class CurrentKeyResponseDto(val data: CurrentKeyDto)

@Serializable
public data class CurrentKeyDto(
    val label: String = "",
    @SerialName("is_free_tier") val isFreeTier: Boolean = false,
    val usage: Double = 0.0,
    /** USD credit limit, `null` when the key is unlimited. */
    val limit: Double? = null,
    @SerialName("limit_remaining") val limitRemaining: Double? = null,
)
