package dev.opendungeon.data.generation

import ai.koog.http.client.KoogHttpClientException
import dev.opendungeon.core.model.DataError
import dev.opendungeon.domain.generation.GenerationError
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * Interprets whatever Koog threw. Koog wraps transport failures in its own exceptions, so the cause
 * chain is searched for the first thing that explains the failure.
 */
internal fun Throwable.toGenerationError(): GenerationError {
    val chain = generateSequence(this) { it.cause }.toList()
    val http = chain.filterIsInstance<KoogHttpClientException>().firstNotNullOfOrNull { e ->
        e.statusCode?.let { status -> DataError.fromHttpStatus(status, e.errorBody.errorMessage()) }
    }
    return when {
        http != null -> GenerationError.Access(http)
        chain.any { it is IOException } -> GenerationError.Access(DataError.Network)
        chain.any { it is SerializationException } -> GenerationError.Access(DataError.UnexpectedResponse)
        else -> GenerationError.Interrupted
    }
}

/** OpenRouter's error envelope, `{"error": {"code", "message"}}`, or the raw body when it is not one. */
private fun String?.errorMessage(): String {
    if (isNullOrBlank()) return ""
    return try {
        OpenRouterKoogJson.decodeFromString<ErrorEnvelope>(this).error.message
    } catch (_: SerializationException) {
        this
    } catch (_: IllegalArgumentException) {
        this
    }
}

@Serializable
private data class ErrorEnvelope(val error: ErrorBody)

@Serializable
private data class ErrorBody(val message: String)
