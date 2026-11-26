package dev.opendungeon.core.network

import dev.opendungeon.core.network.dto.ErrorDto
import dev.opendungeon.core.network.dto.ErrorResponseDto
import kotlinx.serialization.SerializationException

/**
 * Reads OpenRouter's error envelope out of a raw response body, for clients that only hand over text,
 * such as Koog's. `null` when the body is not the documented envelope.
 */
public fun decodeOpenRouterError(body: String): ErrorDto? = try {
    OpenRouterJson.decodeFromString<ErrorResponseDto>(body).error
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    // Thrown by kotlinx.serialization for input that is not JSON at all.
    null
}
