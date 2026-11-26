package dev.opendungeon.data.generation

import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.streaming.IncompleteStreamException
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.network.decodeOpenRouterError
import dev.opendungeon.domain.generation.GenerationError
import io.ktor.client.plugins.sse.SSEClientException
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * Interprets what Koog threw. Koog wraps every failure of a request in its own exceptions, so the cause
 * chain is searched for the first thing that explains it.
 *
 * Returns `null` when the throwable is not a failed request at all but a bug, here or in Koog. Callers
 * rethrow those rather than dress them up as a generation error.
 */
internal fun Throwable.toGenerationErrorOrNull(): GenerationError? {
    val chain = generateSequence(this) { it.cause }.toList()
    val http = chain.filterIsInstance<KoogHttpClientException>().firstNotNullOfOrNull { e ->
        e.statusCode?.let { status -> DataError.fromHttpStatus(status, e.errorBody.errorMessage()) }
    }
    return when {
        http != null -> GenerationError.Access(http)
        chain.any { it is IOException } -> GenerationError.Access(DataError.Network)
        chain.any { it is SerializationException } -> GenerationError.Access(DataError.UnexpectedResponse)
        chain.any { it is IncompleteStreamException } -> GenerationError.Interrupted
        // Koog gave up on the request and says nothing deeper, for example an event stream that failed
        // without a response. Anything else in the chain would be a bug, and is not caught here.
        chain.all { it.isKoogRequestFailure } -> GenerationError.Interrupted
        else -> null
    }
}

private val Throwable.isKoogRequestFailure: Boolean
    get() = this is LLMClientException || this is KoogHttpClientException || this is SSEClientException

/** The message in OpenRouter's error envelope, or the raw body when it is not one. */
private fun String?.errorMessage(): String {
    if (isNullOrBlank()) return ""
    return decodeOpenRouterError(this)?.message ?: this
}
