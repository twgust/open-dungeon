package dev.opendungeon.data.generation

import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.streaming.IncompleteStreamException
import ai.koog.prompt.streaming.StreamFrame
import dev.opendungeon.core.model.DataError
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.generation.TextEvent
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GenerationFailuresTest {

    private fun koog(cause: Throwable? = null, status: Int? = null, body: String? = null) = LLMClientException(
        "OpenRouter",
        cause = KoogHttpClientException("OpenRouter", statusCode = status, errorBody = body, cause = cause),
    )

    @Test
    fun `a status reads the envelope's message, or the raw body when there is none`() {
        assertEquals(
            GenerationError.Access(DataError.RateLimited("Slow down")),
            koog(status = 429, body = """{"error":{"code":429,"message":"Slow down"}}""").toGenerationErrorOrNull(),
        )
        assertEquals(
            GenerationError.Access(DataError.Remote(502, "<html>Bad gateway</html>")),
            koog(status = 502, body = "<html>Bad gateway</html>").toGenerationErrorOrNull(),
        )
    }

    @Test
    fun `transport and decoding failures are named for what they are`() {
        assertEquals(GenerationError.Access(DataError.Network), koog(IOException("reset")).toGenerationErrorOrNull())
        assertEquals(
            GenerationError.Access(DataError.UnexpectedResponse),
            koog(SerializationException("bad")).toGenerationErrorOrNull(),
        )
    }

    @Test
    fun `a stream that stops early, or that Koog gave up on, is interrupted`() {
        assertEquals(GenerationError.Interrupted, IncompleteStreamException().toGenerationErrorOrNull())
        assertEquals(GenerationError.Interrupted, koog().toGenerationErrorOrNull())
    }

    @Test
    fun `a bug is never dressed up as a generation error`() {
        assertNull(IllegalStateException("broken").toGenerationErrorOrNull())
        assertNull(koog(NullPointerException()).toGenerationErrorOrNull(), "even when Koog wrapped it")
    }

    @Test
    fun `a bug inside the stream is thrown, not reported as a failed generation`() = runTest {
        val frames = flow<StreamFrame> {
            emit(StreamFrame.TextDelta("Rain"))
            throw IllegalStateException("broken")
        }

        assertThrows<IllegalStateException> { frames.toTextEvents().toList() }
    }

    @Test
    fun `a request failure inside the stream ends it with one failure`() = runTest {
        val frames = flow<StreamFrame> {
            emit(StreamFrame.TextDelta("Rain"))
            throw koog(IOException("reset"))
        }

        assertEquals(
            listOf(TextEvent.Delta("Rain"), TextEvent.Failed(GenerationError.Access(DataError.Network))),
            frames.toTextEvents().toList(),
        )
    }
}
