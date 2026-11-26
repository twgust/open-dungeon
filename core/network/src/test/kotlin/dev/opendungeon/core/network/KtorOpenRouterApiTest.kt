package dev.opendungeon.core.network

import dev.opendungeon.core.model.ApiKey
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.network.dto.CurrentKeyDto
import dev.opendungeon.core.network.dto.ModelEndpointsDto
import dev.opendungeon.core.network.dto.ModelsPageDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class KtorOpenRouterApiTest {

    private val apiKey = requireNotNull(ApiKey.parse("sk-or-v1-" + "ab".repeat(32)))
    private val attribution = AppAttribution(title = "Open Dungeon", siteUrl = "https://example.org")
    private val requests = mutableListOf<HttpRequestData>()

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): OpenRouterApi {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return KtorOpenRouterApi(openRouterBaseClient(engine, attribution))
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader.getResource(name)) { "missing fixture $name" }.readText()

    @Test
    fun `lists models for the key with auth, attribution and paging`() = runTest {
        val result = api {
            json(
                fixture("models_user_page.json"),
            )
        }.listModelsForKey(apiKey, offset = 1000, limit = 1000, sort = null)

        val page = assertIs<Outcome.Success<ModelsPageDto>>(result).value
        assertEquals(
            listOf(
                "upstage/solar-mini-4-5",
                "~unbiased/pareto-latest",
                "inclusionai/ling-3.2-flash:free",
            ),
            page.data.map {
                it.id
            },
        )
        assertNull(page.links?.next)
        assertEquals("medium", page.data.first().reasoning?.defaultEffort)

        val request = requests.single()
        assertEquals("https://openrouter.ai/api/v1/models/user?offset=1000&limit=1000", request.url.toString())
        assertEquals("Bearer ${apiKey.value}", request.headers[HttpHeaders.Authorization])
        assertEquals("Open Dungeon", request.headers[TITLE_HEADER])
        assertEquals("https://example.org", request.headers[REFERER_HEADER])
    }

    @Test
    fun `asks OpenRouter to rank the list when a sort is given`() = runTest {
        api { json("""{"data":[],"total_count":0,"links":{"next":null}}""") }
            .listModelsForKey(apiKey, offset = 0, limit = 1000, sort = "top-weekly")

        assertEquals(
            "https://openrouter.ai/api/v1/models/user?offset=0&limit=1000&sort=top-weekly",
            requests.single().url.toString(),
        )
    }

    @Test
    fun `lists a model's providers, keeping a variant's suffix in its own segment`() = runTest {
        val result = api {
            json(fixture("model_endpoints.json"))
        }.listEndpoints(apiKey, "deepseek/deepseek-chat-v3.1:free")

        val endpoints = assertIs<Outcome.Success<ModelEndpointsDto>>(result).value.endpoints
        assertEquals(2, endpoints.size)
        assertEquals("DeepInfra", endpoints.first().providerName)
        assertEquals("deepinfra/fp4", endpoints.first().tag)
        assertEquals(100.0, endpoints.first().uptimeLastDay)
        assertEquals(
            "https://openrouter.ai/api/v1/models/deepseek/deepseek-chat-v3.1:free/endpoints",
            requests.single().url.toString(),
        )
    }

    @Test
    fun `unwraps the current key envelope`() = runTest {
        val body = """{"data":{"label":"sk-or-v1-ab…","is_free_tier":false,"usage":1.5,"limit":10,""" +
            """"limit_remaining":8.5,"new_field":true}}"""
        val result = api { json(body) }.getCurrentKey(apiKey)

        val key = assertIs<Outcome.Success<CurrentKeyDto>>(result).value
        assertEquals(8.5, key.limitRemaining)
        assertEquals("https://openrouter.ai/api/v1/key", requests.single().url.toString())
    }

    @Test
    fun `decodes the documented error envelope on failure`() = runTest {
        val result = api {
            json("""{"error":{"code":401,"message":"User not found."}}""", HttpStatusCode.Unauthorized)
        }.getCurrentKey(apiKey)

        val error = assertIs<NetworkError.Http>(assertIs<Outcome.Failure<NetworkError>>(result).error)
        assertEquals(401, error.status)
        assertEquals("User not found.", error.body?.message)
    }

    @Test
    fun `tolerates error bodies that are not json`() = runTest {
        val result = api { respond("<html>Bad gateway</html>", HttpStatusCode.BadGateway) }.getCurrentKey(apiKey)

        val error = assertIs<NetworkError.Http>(assertIs<Outcome.Failure<NetworkError>>(result).error)
        assertEquals(502, error.status)
        assertNull(error.body)
    }

    @Test
    fun `maps io failures to transport errors`() = runTest {
        val result = api { throw IOException("offline") }.getCurrentKey(apiKey)

        assertIs<NetworkError.Transport>(assertIs<Outcome.Failure<NetworkError>>(result).error)
    }

    @Test
    fun `maps schema mismatches to decoding errors`() = runTest {
        val result = api { json("""{"data":{"usage":"not a number"}}""") }.getCurrentKey(apiKey)

        assertIs<NetworkError.Decoding>(assertIs<Outcome.Failure<NetworkError>>(result).error)
    }
}
