package dev.opendungeon.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Error envelope shared by every OpenRouter endpoint: `{"error": {"code": 401, "message": "…"}}`. */
@Serializable
public data class ErrorResponseDto(val error: ErrorDto)

@Serializable
public data class ErrorDto(val code: Int? = null, val message: String = "", val metadata: JsonObject? = null)
