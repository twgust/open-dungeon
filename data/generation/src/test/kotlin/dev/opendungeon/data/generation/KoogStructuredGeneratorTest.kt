package dev.opendungeon.data.generation

import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.generation.StructuredRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KoogStructuredGeneratorTest {

    @Serializable
    @SerialName("Person")
    data class Person(val name: String, val wants: String)

    private fun request(parameters: Set<String>) = StructuredRequest(
        model = aiModel(parameters = parameters),
        messages = listOf(ChatMessage.User("Invent one person for a gothic heist.")),
        serializer = Person.serializer(),
        descriptions = mapOf("Person.wants" to "What they want, in one short sentence"),
    )

    private val answer = """{"name":"Maren Kask","wants":"Her mother's bones back."}"""

    @Test
    fun `a model with structured outputs gets a native schema on a strict route`() = runTest {
        val openRouter = ScriptedOpenRouter(json(completion(answer)))
        val keys = FakeApiKeyStore()

        val result = KoogStructuredGenerator(openRouter.executor(keys), keys)
            .generate(request(parameters = setOf("structured_outputs", "response_format")))

        assertEquals(Outcome.Success(Person("Maren Kask", "Her mother's bones back.")), result)
        val body = openRouter.body(0)
        val format = body.getValue("response_format").jsonObject
        assertEquals("json_schema", format.getValue("type").jsonPrimitive.content)
        val schema = format.getValue("json_schema").toString()
        assertTrue("What they want, in one short sentence" in schema, "descriptions reach the schema")
        assertEquals("true", body.getValue("provider").jsonObject.getValue("require_parameters").jsonPrimitive.content)
    }

    @Test
    fun `any other model gets the schema as instructions`() = runTest {
        val openRouter = ScriptedOpenRouter(json(completion(answer)))
        val keys = FakeApiKeyStore()

        val result = KoogStructuredGenerator(openRouter.executor(keys), keys).generate(request(parameters = emptySet()))

        assertEquals(Outcome.Success(Person("Maren Kask", "Her mother's bones back.")), result)
        val body = openRouter.body(0)
        assertFalse("response_format" in body)
        assertFalse("provider" in body)
        val prompt = body.getValue("messages").jsonArray.joinToString { it.toString() }
        assertTrue("What they want, in one short sentence" in prompt, "the schema is spelled out in the prompt")
    }

    @Test
    fun `a malformed answer is repaired by a fixing pass`() = runTest {
        val openRouter = ScriptedOpenRouter(
            json(completion("Here she is: name Maren, wants revenge")),
            json(completion(answer)),
        )
        val keys = FakeApiKeyStore()

        val result = KoogStructuredGenerator(openRouter.executor(keys), keys).generate(request(parameters = emptySet()))

        assertEquals(Outcome.Success(Person("Maren Kask", "Her mother's bones back.")), result)
        assertEquals(2, openRouter.requestCount)
    }

    @Test
    fun `an answer that never parses is a schema mismatch`() = runTest {
        val openRouter = ScriptedOpenRouter(
            json(completion("no")),
            json(completion("still no")),
            json(completion("never")),
        )
        val keys = FakeApiKeyStore()

        val result = KoogStructuredGenerator(openRouter.executor(keys), keys).generate(request(parameters = emptySet()))

        assertIs<GenerationError.SchemaMismatch>(assertIs<Outcome.Failure<GenerationError>>(result).error)
        assertEquals(3, openRouter.requestCount, "one request and two fixing attempts, no more")
    }

    @Test
    fun `a rejected key is an access failure`() = runTest {
        val openRouter = ScriptedOpenRouter(
            json("""{"error":{"code":401,"message":"User not found."}}""", status = 401),
        )
        val keys = FakeApiKeyStore()

        val result = KoogStructuredGenerator(openRouter.executor(keys), keys).generate(request(parameters = emptySet()))

        assertEquals(Outcome.Failure(GenerationError.Access(DataError.Unauthorized("User not found."))), result)
    }
}
