package dev.opendungeon.data.openrouter

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelParameter
import dev.opendungeon.core.model.ModelPricing
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.network.NetworkError
import dev.opendungeon.core.network.OpenRouterApi
import dev.opendungeon.core.network.dto.ModelDto
import dev.opendungeon.core.network.dto.ModelsPageDto
import dev.opendungeon.core.network.dto.PageLinksDto
import dev.opendungeon.core.network.dto.PricingDto
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ModelCatalogRepositoryTest {

    private fun model(id: String, prompt: String = "0.000001", completion: String = "0.000002") = ModelDto(
        id = id,
        name = id,
        pricing = PricingDto(prompt, completion),
        supportedParameters = listOf("temperature", "brand_new_param"),
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
            DefaultModelCatalogRepository(api, FakeApiKeyStore(VALID_KEY)).fetchModels(),
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
            DefaultModelCatalogRepository(api, FakeApiKeyStore(VALID_KEY)).fetchModels(),
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
    fun `requires a stored key`() = runTest {
        assertEquals(
            Outcome.Failure(DataError.MissingApiKey),
            DefaultModelCatalogRepository(FakeOpenRouterApi(), FakeApiKeyStore()).fetchModels(),
        )
    }

    @Test
    fun `maps transport failures`() = runTest {
        val api = FakeOpenRouterApi(pages = { Outcome.Failure(NetworkError.Transport(IOException())) })

        assertEquals(
            Outcome.Failure(DataError.Network),
            DefaultModelCatalogRepository(api, FakeApiKeyStore(VALID_KEY)).fetchModels(),
        )
    }

    @Test
    fun `a next link that never ends fails instead of looping`() = runTest {
        val fullPage = List(OpenRouterApi.MAX_PAGE_SIZE) { model("a/$it") }
        val api =
            FakeOpenRouterApi(pages = {
                Outcome.Success(ModelsPageDto(fullPage, links = PageLinksDto(next = "/again")))
            })

        val result = DefaultModelCatalogRepository(api, FakeApiKeyStore(VALID_KEY)).fetchModels()

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
            DefaultModelCatalogRepository(api, FakeApiKeyStore(VALID_KEY)).fetchModels(),
        )
    }
}
