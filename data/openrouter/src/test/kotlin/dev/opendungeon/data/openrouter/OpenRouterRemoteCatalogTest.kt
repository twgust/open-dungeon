package dev.opendungeon.data.openrouter

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelEndpoint
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelParameter
import dev.opendungeon.core.model.ModelPricing
import dev.opendungeon.core.model.ModelSort
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.network.NetworkError
import dev.opendungeon.core.network.OpenRouterApi
import dev.opendungeon.core.network.dto.EndpointDto
import dev.opendungeon.core.network.dto.ModelDto
import dev.opendungeon.core.network.dto.ModelEndpointsDto
import dev.opendungeon.core.network.dto.ModelsPageDto
import dev.opendungeon.core.network.dto.PageLinksDto
import dev.opendungeon.core.network.dto.PricingDto
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OpenRouterRemoteCatalogTest {

    private fun model(
        id: String,
        prompt: String = "0.000001",
        completion: String = "0.000002",
        endpoints: List<String>? = null,
    ) = ModelDto(
        id = id,
        name = id,
        pricing = PricingDto(prompt, completion),
        supportedParameters = listOf("temperature", "brand_new_param"),
        endpoints = endpoints,
    )

    @Test
    fun `follows pages until the last one`() = runTest {
        val fullPage = List(OpenRouterApi.MAX_PAGE_SIZE) { model("a/$it") }
        val api = FakeOpenRouterApi(pages = { offset ->
            when (offset) {
                0 -> Outcome.Success(
                    ModelsPageDto(fullPage, links = PageLinksDto(next = "/api/v1/models/user?offset=1000")),
                )
                1000 -> Outcome.Success(ModelsPageDto(listOf(model("b/last")), links = PageLinksDto(next = null)))
                else -> error("unexpected offset $offset")
            }
        })

        val models = assertIs<Outcome.Success<List<AiModel>>>(
            OpenRouterRemoteCatalog(api, FakeApiKeyStore(VALID_KEY)).fetchModels(sort = null),
        ).value

        assertEquals(listOf(0, 1000), api.requestedOffsets)
        assertEquals(1001, models.size)
        assertEquals(
            setOf(ModelParameter.Known.Temperature, ModelParameter.Unknown("brand_new_param")),
            models.last().supportedParameters,
        )
    }

    @Test
    fun `models price sentinels explicitly and keeps models whose prices it cannot read`() = runTest {
        val api = FakeOpenRouterApi(pages = {
            Outcome.Success(
                ModelsPageDto(
                    listOf(
                        model("router", prompt = "-1", completion = "-1"),
                        model("free", prompt = "0", completion = "0"),
                        model("paid"),
                        model("broken", prompt = "n/a"),
                    ),
                ),
            )
        })

        val models = assertIs<Outcome.Success<List<AiModel>>>(
            OpenRouterRemoteCatalog(api, FakeApiKeyStore(VALID_KEY)).fetchModels(sort = null),
        ).value

        assertEquals(
            listOf(
                ModelPricing.Dynamic,
                ModelPricing.Free,
                ModelPricing.Metered(BigDecimal("0.000001"), BigDecimal("0.000002")),
                ModelPricing.Unknown,
            ),
            models.map(AiModel::pricing),
        )
    }

    @Test
    fun `leaves out models that cannot answer a chat request`() = runTest {
        val chat = listOf("/api/v1/chat/completions", "/api/v1/responses")
        val api = FakeOpenRouterApi(pages = {
            Outcome.Success(
                ModelsPageDto(
                    listOf(
                        model("a/chat", endpoints = chat),
                        model("a/batch:batch", endpoints = listOf("/api/v1/batches")),
                        model("a/unlisted", endpoints = null),
                    ),
                ),
            )
        })

        val models = assertIs<Outcome.Success<List<AiModel>>>(
            OpenRouterRemoteCatalog(api, FakeApiKeyStore(VALID_KEY)).fetchModels(sort = null),
        ).value

        assertEquals(listOf("a/chat", "a/unlisted"), models.map { it.id.value })
    }

    @Test
    fun `passes the sort to OpenRouter and keeps its order`() = runTest {
        val api = FakeOpenRouterApi(pages = {
            Outcome.Success(ModelsPageDto(listOf(model("b/second"), model("a/first"))))
        })

        val models = assertIs<Outcome.Success<List<AiModel>>>(
            OpenRouterRemoteCatalog(api, FakeApiKeyStore(VALID_KEY)).fetchModels(ModelSort.TopWeekly),
        ).value

        assertEquals(listOf<String?>("top-weekly"), api.requestedSorts)
        assertEquals(listOf("b/second", "a/first"), models.map { it.id.value })
    }

    @Test
    fun `maps each provider of a model`() = runTest {
        val api = FakeOpenRouterApi(endpoints = { id ->
            Outcome.Success(
                ModelEndpointsDto(
                    id = id,
                    endpoints = listOf(
                        EndpointDto(
                            providerName = "DeepInfra",
                            tag = "deepinfra/fp4",
                            contextLength = 163_840,
                            pricing = PricingDto("0.00000025", "0.00000095"),
                            quantization = "fp4",
                            supportedParameters = listOf("top_k", "seed"),
                            uptimeLastDay = 99.5,
                        ),
                    ),
                ),
            )
        })

        val endpoints = assertIs<Outcome.Success<List<ModelEndpoint>>>(
            OpenRouterRemoteCatalog(api, FakeApiKeyStore(VALID_KEY)).fetchEndpoints(ModelId("deepseek/v3")),
        ).value

        val endpoint = endpoints.single()
        assertEquals("DeepInfra", endpoint.providerName)
        assertEquals(setOf(ModelParameter.Known.TopK, ModelParameter.Known.Seed), endpoint.supportedParameters)
        assertEquals(ModelPricing.Metered(BigDecimal("0.00000025"), BigDecimal("0.00000095")), endpoint.pricing)
        assertEquals(listOf("deepseek/v3"), api.requestedEndpoints)
    }

    @Test
    fun `provider detail fails like the catalog does`() = runTest {
        val api = FakeOpenRouterApi(endpoints = { Outcome.Failure(NetworkError.Http(404, null)) })

        assertEquals(
            Outcome.Failure(DataError.Remote(404, "")),
            OpenRouterRemoteCatalog(api, FakeApiKeyStore(VALID_KEY)).fetchEndpoints(ModelId("gone/model")),
        )
    }

    @Test
    fun `requires a stored key`() = runTest {
        assertEquals(
            Outcome.Failure(DataError.MissingApiKey),
            OpenRouterRemoteCatalog(FakeOpenRouterApi(), FakeApiKeyStore()).fetchModels(sort = null),
        )
    }

    @Test
    fun `maps transport failures`() = runTest {
        val api = FakeOpenRouterApi(pages = { Outcome.Failure(NetworkError.Transport(IOException())) })

        assertEquals(
            Outcome.Failure(DataError.Network),
            OpenRouterRemoteCatalog(api, FakeApiKeyStore(VALID_KEY)).fetchModels(sort = null),
        )
    }

    @Test
    fun `a next link that never ends fails instead of looping`() = runTest {
        val fullPage = List(OpenRouterApi.MAX_PAGE_SIZE) { model("a/$it") }
        val api =
            FakeOpenRouterApi(pages = {
                Outcome.Success(ModelsPageDto(fullPage, links = PageLinksDto(next = "/again")))
            })

        val result = OpenRouterRemoteCatalog(api, FakeApiKeyStore(VALID_KEY)).fetchModels(sort = null)

        assertEquals(Outcome.Failure(DataError.UnexpectedResponse), result)
        assertEquals(50, api.requestedOffsets.size)
    }

    @Test
    fun `a page that fails midway fails the whole catalog`() = runTest {
        val fullPage = List(OpenRouterApi.MAX_PAGE_SIZE) { model("a/$it") }
        val api = FakeOpenRouterApi(pages = { offset ->
            if (offset == 0) {
                Outcome.Success(ModelsPageDto(fullPage, links = PageLinksDto(next = "/next")))
            } else {
                Outcome.Failure(NetworkError.Http(429, null))
            }
        })

        assertEquals(
            Outcome.Failure(DataError.RateLimited("")),
            OpenRouterRemoteCatalog(api, FakeApiKeyStore(VALID_KEY)).fetchModels(sort = null),
        )
    }
}
