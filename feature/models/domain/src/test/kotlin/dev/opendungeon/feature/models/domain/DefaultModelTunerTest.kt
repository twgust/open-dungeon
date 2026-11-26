package dev.opendungeon.feature.models.domain

import app.cash.turbine.test
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelEndpoint
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelPricing
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.domain.openrouter.Catalog
import dev.opendungeon.domain.openrouter.testing.FETCHED_AT
import dev.opendungeon.domain.openrouter.testing.FakeModelCatalog
import dev.opendungeon.domain.openrouter.testing.FakeModelSetup
import dev.opendungeon.domain.openrouter.testing.aiModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultModelTunerTest {

    private val model = aiModel("nous/hermes-4-405b", parameters = listOf("temperature", "top_p", "seed"))
    private val endpoint = ModelEndpoint(
        providerName = "DeepInfra",
        tag = "deepinfra/fp8",
        contextLength = 131_072,
        maxCompletionTokens = 8_192,
        pricing = ModelPricing.Free,
        quantization = "fp8",
        supportedParameters = model.supportedParameters,
        uptimeLastDay = 99.5,
    )

    private fun TestScope.tuner(
        catalog: FakeModelCatalog = FakeModelCatalog(Catalog(listOf(model), FETCHED_AT)),
        setup: FakeModelSetup = FakeModelSetup(chosen = model.id),
    ) = DefaultModelTuner(backgroundScope, catalog, setup)

    private fun ModelTuner.ready() = assertIs<TunerState.Ready>(state.value)

    @Test
    fun `opens on the chosen model with its stored tuning and its providers`() = runTest(UnconfinedTestDispatcher()) {
        val catalog = FakeModelCatalog(Catalog(listOf(model), FETCHED_AT)).apply {
            endpointsResult = Outcome.Success(listOf(endpoint))
        }
        val setup = FakeModelSetup(chosen = model.id).apply { storeTuning(model.id, Tuning(temperature = 0.4)) }

        val ready = tuner(catalog, setup).ready()

        assertEquals(model, ready.model)
        assertEquals(ParameterSheet.of(model, Tuning(temperature = 0.4)), ready.sheet)
        assertEquals(Providers.Loaded(listOf(endpoint)), ready.providers)
        assertTrue(ready.isTuned)
    }

    @Test
    fun `without a chosen model in the catalog there is nothing to tune`() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(TunerState.NoModel, tuner(setup = FakeModelSetup()).state.value)
        assertEquals(TunerState.NoModel, tuner(setup = FakeModelSetup(chosen = ModelId("gone/model"))).state.value)
    }

    @Test
    fun `an edit shows at once and is stored`() = runTest(UnconfinedTestDispatcher()) {
        val setup = FakeModelSetup(chosen = model.id)
        val tuner = tuner(setup = setup)

        tuner.edit(TuningEdit.Slide(Setting.Temperature, 1.3))

        assertEquals(1.3, (tuner.ready().sheet.rows.first() as ParameterRow.Slider).value)
        assertEquals(Tuning(temperature = 1.3), setup.tuning(model.id).first())
    }

    @Test
    fun `an edit for a parameter the model does not list is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val setup = FakeModelSetup(chosen = model.id)
        val tuner = tuner(setup = setup)

        tuner.edit(TuningEdit.Type(Setting.TopK, 40))

        assertFalse(tuner.ready().isTuned)
        assertEquals(emptyList(), setup.savedTunings)
    }

    @Test
    fun `an edit the device cannot store is taken back and reported`() = runTest(UnconfinedTestDispatcher()) {
        val setup = FakeModelSetup(chosen = model.id).apply { storeTuning(model.id, Tuning(topP = 0.9)) }
        val tuner = tuner(setup = setup)
        setup.failure = DataError.Storage

        tuner.failures.test {
            tuner.edit(TuningEdit.Slide(Setting.Temperature, 0.2))

            assertEquals(DataError.Storage, awaitItem())
        }
        assertEquals(ParameterSheet.of(model, Tuning(topP = 0.9)), tuner.ready().sheet)
    }

    @Test
    fun `reset all leaves everything to the provider`() = runTest(UnconfinedTestDispatcher()) {
        val setup = FakeModelSetup(chosen = model.id).apply {
            storeTuning(model.id, Tuning(temperature = 0.4, seed = 1))
        }
        val tuner = tuner(setup = setup)

        tuner.resetAll()

        assertFalse(tuner.ready().isTuned)
        assertEquals(Tuning.Untouched, setup.tuning(model.id).first())
    }

    @Test
    fun `providers that failed to load can be fetched again`() = runTest(UnconfinedTestDispatcher()) {
        val catalog = FakeModelCatalog(Catalog(listOf(model), FETCHED_AT)).apply {
            endpointsResult = Outcome.Failure(DataError.Network)
        }
        val tuner = tuner(catalog)
        assertEquals(Providers.Failed(DataError.Network), tuner.ready().providers)

        val answer = CompletableDeferred<Outcome<List<ModelEndpoint>, DataError>>().also(catalog.heldEndpoints::addLast)
        tuner.retryProviders()
        assertEquals(Providers.Loading, tuner.ready().providers)
        answer.complete(Outcome.Success(listOf(endpoint)))

        assertEquals(Providers.Loaded(listOf(endpoint)), tuner.ready().providers)
    }
}
