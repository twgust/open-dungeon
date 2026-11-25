package dev.opendungeon.data.generation

import dev.opendungeon.core.model.DataError
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.generation.Sampling
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.domain.generation.TextRequest
import dev.opendungeon.domain.generation.TokenUsage
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KoogTextGeneratorTest {

    private val request = TextRequest(
        model = aiModel(parameters = setOf("temperature", "max_tokens", "seed")),
        messages = listOf(ChatMessage.System("You narrate."), ChatMessage.User("Begin.")),
        sampling = Sampling(temperature = 0.8, topK = 40, maxTokens = 600, seed = 7),
        sessionId = "draft-1",
    )

    @Test
    fun `streams deltas and ends with usage`() = runTest {
        val openRouter = ScriptedOpenRouter(
            eventStream(
                chunk(content = "Rain falls "),
                chunk(content = "on the salt flats."),
                chunk(finishReason = "stop"),
                chunk(usage = """{"prompt_tokens":40,"completion_tokens":9,"total_tokens":49}"""),
            ),
        )
        val keys = FakeApiKeyStore()

        val events = KoogTextGenerator(openRouter.executor(keys), keys).stream(request).toList()

        assertEquals(
            listOf(
                TextEvent.Delta("Rain falls "),
                TextEvent.Delta("on the salt flats."),
                TextEvent.Completed(usage = TokenUsage(promptTokens = 40, completionTokens = 9), cutOff = false),
            ),
            events,
        )
    }

    @Test
    fun `sends only supported parameters, the session and the attribution`() = runTest {
        val openRouter = ScriptedOpenRouter(eventStream(chunk(content = "x", finishReason = "stop")))
        val keys = FakeApiKeyStore()

        KoogTextGenerator(openRouter.executor(keys), keys).stream(request).toList()

        val sent = openRouter.request(0)
        assertEquals("/api/v1/chat/completions", sent.url.encodedPath)
        assertEquals("Bearer ${VALID_KEY.value}", sent.headers[HttpHeaders.Authorization])
        assertEquals("Open Dungeon", sent.headers["X-OpenRouter-Title"])
        assertEquals("https://example.test", sent.headers["HTTP-Referer"])

        val body = openRouter.body(0)
        assertEquals("nous/hermes-4-405b", body.getValue("model").jsonPrimitive.content)
        assertEquals("true", body.getValue("stream").jsonPrimitive.content)
        assertEquals(0.8, body.getValue("temperature").jsonPrimitive.content.toDouble())
        assertEquals(600, body.getValue("max_tokens").jsonPrimitive.content.toInt())
        assertEquals(7, body.getValue("seed").jsonPrimitive.content.toInt())
        assertEquals("draft-1", body.getValue("session_id").jsonPrimitive.content)
        assertFalse("top_k" in body, "top_k is not in the model's supported_parameters and must not be sent")
        assertFalse("provider" in body)
        val roles = body.getValue("messages").jsonArray.map { it.jsonObject.getValue("role").jsonPrimitive.content }
        assertEquals(listOf("system", "user"), roles)
    }

    @Test
    fun `an out of credits answer becomes a typed failure`() = runTest {
        val openRouter = ScriptedOpenRouter(
            json("""{"error":{"code":402,"message":"Insufficient credits"}}""", status = 402),
        )
        val keys = FakeApiKeyStore()

        val events = KoogTextGenerator(openRouter.executor(keys), keys).stream(request).toList()

        assertEquals(
            listOf(TextEvent.Failed(GenerationError.Access(DataError.InsufficientCredits("Insufficient credits")))),
            events,
        )
    }

    @Test
    fun `moderation stops the stream as a refusal`() = runTest {
        val openRouter = ScriptedOpenRouter(
            eventStream(chunk(content = "She leans in and"), chunk(finishReason = "content_filter")),
        )
        val keys = FakeApiKeyStore()

        val events = KoogTextGenerator(openRouter.executor(keys), keys).stream(request).toList()

        assertEquals(listOf(TextEvent.Delta("She leans in and"), TextEvent.Failed(GenerationError.Refused)), events)
    }

    @Test
    fun `hitting the token limit completes but says so`() = runTest {
        val openRouter = ScriptedOpenRouter(eventStream(chunk(content = "And then"), chunk(finishReason = "length")))
        val keys = FakeApiKeyStore()

        val last = KoogTextGenerator(openRouter.executor(keys), keys).stream(request).toList().last()

        assertTrue(last is TextEvent.Completed && last.cutOff)
    }

    @Test
    fun `without a key nothing is sent`() = runTest {
        val openRouter = ScriptedOpenRouter()
        val keys = FakeApiKeyStore(initial = null)

        val events = KoogTextGenerator(openRouter.executor(keys), keys).stream(request).toList()

        assertEquals(listOf(TextEvent.Failed(GenerationError.Access(DataError.MissingApiKey))), events)
        assertEquals(0, openRouter.requestCount)
    }
}
