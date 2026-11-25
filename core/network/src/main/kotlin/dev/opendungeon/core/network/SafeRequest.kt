package dev.opendungeon.core.network

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.network.dto.ErrorResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.serialization.SerializationException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Executes a request and folds every expected failure into a [NetworkResult].
 * Cancellation is always rethrown; programming errors are not caught.
 */
internal suspend inline fun <reified T> HttpClient.safeRequest(
    crossinline block: HttpRequestBuilder.() -> Unit,
): NetworkResult<T> {
    val response = try {
        request { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        return Outcome.Failure(NetworkError.Transport(e))
    }

    if (!response.status.isSuccess()) {
        return Outcome.Failure(NetworkError.Http(response.status.value, response.errorBodyOrNull()?.error))
    }

    return try {
        Outcome.Success(response.body<T>())
    } catch (e: ContentConvertException) {
        Outcome.Failure(NetworkError.Decoding(e))
    } catch (e: SerializationException) {
        Outcome.Failure(NetworkError.Decoding(e))
    } catch (e: NoTransformationFoundException) {
        // A body that is not JSON at all, e.g. an HTML page from a proxy or CDN.
        Outcome.Failure(NetworkError.Decoding(e))
    }
}

@PublishedApi
internal suspend fun HttpResponse.errorBodyOrNull(): ErrorResponseDto? = try {
    body<ErrorResponseDto>()
} catch (e: CancellationException) {
    throw e
} catch (_: ContentConvertException) {
    null
} catch (_: SerializationException) {
    null
} catch (_: NoTransformationFoundException) {
    null
}
