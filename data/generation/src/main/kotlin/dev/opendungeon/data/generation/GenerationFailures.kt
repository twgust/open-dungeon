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
    chain.firstNotNullOfOrNull { it as? OpenRouterFailure }?.let { return it.toGenerationError() }
    val status = chain.filterIsInstance<KoogHttpClientException>().firstNotNullOfOrNull { e ->
        e.statusCode?.let { it to e.errorBody.errorMessage() }
    }
    return when {
        status?.first == NOT_FOUND -> GenerationError.NoProvider
        status != null -> GenerationError.Access(DataError.fromHttpStatus(status.first, status.second))
        chain.any { it is IOException } -> GenerationError.Access(DataError.Network)
        chain.any { it is SerializationException } -> GenerationError.Access(DataError.UnexpectedResponse)
        chain.any { it is IncompleteStreamException } -> GenerationError.Interrupted
        // Koog gave up on the request and says nothing deeper, for example an event stream that failed
        // without a response. Anything else in the chain would be a bug, and is not caught here.
        chain.all { it.isKoogRequestFailure } -> GenerationError.Interrupted
        else -> null
    }
}

/**
 * OpenRouter's chat completions answer 404 when no provider of the model takes the request as sent,
 * typically because none honours every parameter while `require_parameters` is set.
 */
private const val NOT_FOUND = 404

/**
 * A failure inside a 200 response. Key, credit and rate problems keep their meaning; anything else, such
 * as a provider failing mid-answer, broke off the generation.
 */
internal fun OpenRouterFailure.toGenerationError(): GenerationError {
    val error = status?.let { DataError.fromHttpStatus(it, message) }
    return when (error) {
        is DataError.Unauthorized, is DataError.InsufficientCredits, is DataError.RateLimited ->
            GenerationError.Access(error)
        else -> GenerationError.Interrupted
    }
}

private val Throwable.isKoogRequestFailure: Boolean
    get() = this is LLMClientException || this is KoogHttpClientException || this is SSEClientException

/** The message in OpenRouter's error envelope, or the raw body when it is not one. */
private fun String?.errorMessage(): String {
    if (isNullOrBlank()) return ""
    return decodeOpenRouterError(this)?.message ?: this
}
