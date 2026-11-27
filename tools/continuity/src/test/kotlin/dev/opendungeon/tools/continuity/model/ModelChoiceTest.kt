package dev.opendungeon.tools.continuity.model

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelEndpoint
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelSort
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.model.Reasoning
import dev.opendungeon.core.model.ReasoningEffort
import dev.opendungeon.core.model.ReasoningSupport
import dev.opendungeon.data.openrouter.RemoteModelCatalog
import dev.opendungeon.domain.openrouter.testing.aiModel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelChoiceTest {

    private fun reasoning(mandatory: Boolean, vararg efforts: String) = ReasoningSupport(
        mandatory = mandatory,
        enabledByDefault = true,
        supportedEfforts = efforts.map(::ReasoningEffort),
        defaultEffort = efforts.firstOrNull()?.let(::ReasoningEffort),
        supportsBudget = false,
    )

    @Test
    fun `least turns reasoning off where the model allows it`() {
        val model =
            aiModel("deepseek/deepseek-v4.1-flash", reasoning = reasoning(mandatory = false, "max", "high", "low"))

        assertEquals(Reasoning.Off, ReasoningChoice.Least.on(model))
    }

    @Test
    fun `least picks the lowest effort when reasoning cannot be turned off`() {
        val model = aiModel("z-ai/glm-5.3-flash", reasoning = reasoning(mandatory = true, "max", "high", "low"))

        assertEquals(Reasoning.On(ReasoningEffort("low")), ReasoningChoice.Least.on(model))
        assertEquals(Reasoning.On(ReasoningEffort("max")), ReasoningChoice.Most.on(model))
    }

    @Test
    fun `nothing is sent for a model that does not reason, or when the default is wanted`() {
        assertNull(ReasoningChoice.Least.on(aiModel("plain/model")))
        assertNull(ReasoningChoice.Default.on(aiModel("deepseek/x", reasoning = reasoning(mandatory = false, "high"))))
    }

    @Test
    fun `the judge is deterministic and the subject keeps the provider's sampling`() {
        val model = aiModel("deepseek/deepseek-v4.1-flash", reasoning = reasoning(mandatory = false, "high"))

        assertEquals(0.0, ModelChoice.judge(model).tuning.temperature)
        assertNull(ModelChoice.subject(model, ReasoningChoice.Least, temperature = null).tuning.temperature)
        assertEquals(
            Reasoning.Off,
            ModelChoice.subject(model, ReasoningChoice.Least, temperature = null).tuning.reasoning,
        )
    }

    @Test
    fun `the resolver finds models in the live catalog and suggests neighbours for a missing one`() = runTest {
        val catalog = object : RemoteModelCatalog {
            override suspend fun fetchModels(sort: ModelSort?): Outcome<List<AiModel>, DataError> = Outcome.Success(
                listOf(
                    aiModel("deepseek/deepseek-v4.1-flash"),
                    aiModel("deepseek/deepseek-v4-pro"),
                    aiModel("z-ai/glm-5.3"),
                ),
            )

            override suspend fun fetchEndpoints(id: ModelId): Outcome<List<ModelEndpoint>, DataError> = error("unused")
        }
        val resolver = CatalogModelResolver(catalog)

        val found = resolver.resolve(setOf(ModelId("z-ai/glm-5.3")))
        val missing = resolver.resolve(setOf(ModelId("deepseek/deepseek-v9")))

        assertEquals(setOf(ModelId("z-ai/glm-5.3")), (found as Outcome.Success).value.keys)
        assertEquals(
            ResolveError.Unknown(
                ModelId("deepseek/deepseek-v9"),
                listOf(ModelId("deepseek/deepseek-v4-pro"), ModelId("deepseek/deepseek-v4.1-flash")),
            ),
            (missing as Outcome.Failure).error,
        )
    }
}
