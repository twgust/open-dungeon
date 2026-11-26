package dev.opendungeon.feature.models.domain

import app.cash.turbine.test
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.domain.generation.TextGenerator
import dev.opendungeon.domain.generation.TextRequest
import dev.opendungeon.domain.generation.Usage
import dev.opendungeon.domain.openrouter.Catalog
import dev.opendungeon.domain.openrouter.testing.FETCHED_AT
import dev.opendungeon.domain.openrouter.testing.FakeModelCatalog
import dev.opendungeon.domain.openrouter.testing.FakeModelSetup
import dev.opendungeon.domain.openrouter.testing.aiModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTryRunTest {

    private val model = aiModel("nous/hermes-4-405b")

    /** Streams whatever the test sends, and records each request and whether its stream was cancelled. */
    private class ScriptedGenerator : TextGenerator {
        val requests = mutableListOf<TextRequest>()
        var events = Channel<TextEvent>(Channel.UNLIMITED)
        var closed = false

        override fun stream(request: TextRequest): Flow<TextEvent> {
            requests += request
            return events.consumeAsFlow().onCompletion { closed = true }
        }
    }

    private val generator = ScriptedGenerator()
    private val setup = FakeModelSetup(chosen = model.id)

    private fun TestScope.run(catalog: FakeModelCatalog = FakeModelCatalog(Catalog(listOf(model), FETCHED_AT))) =
        DefaultTryRun(backgroundScope, catalog, setup, generator)

    @Test
    fun `sends the prompt with the model's tuning as it is when sent`() = runTest(UnconfinedTestDispatcher()) {
        val run = run()
        setup.storeTuning(model.id, Tuning(temperature = 0.3))

        run.send("  Describe the tavern.  ")

        assertEquals(
            TextRequest(model, listOf(ChatMessage.User("Describe the tavern.")), Tuning(temperature = 0.3)),
            generator.requests.single(),
        )
        assertEquals(RunStatus.Streaming, run.state.value.status)
    }

    @Test
    fun `streams reasoning and text, then the usage`() = runTest(UnconfinedTestDispatcher()) {
        val run = run()
        run.send("Go")
        val usage = Usage(promptTokens = 4, completionTokens = 9, cost = BigDecimal("0.0001"))

        generator.events.send(TextEvent.Reasoning("Think. "))
        generator.events.send(TextEvent.Delta("Smoke "))
        generator.events.send(TextEvent.Delta("curls."))
        generator.events.send(TextEvent.Completed(usage, cutOff = true))

        assertEquals("Think. ", run.state.value.reasoning)
        assertEquals("Smoke curls.", run.state.value.reply)
        assertEquals(RunStatus.Done(usage, cutOff = true), run.state.value.status)
    }

    @Test
    fun `a failure keeps the text so far`() = runTest(UnconfinedTestDispatcher()) {
        val run = run()
        run.send("Go")

        generator.events.send(TextEvent.Delta("Smoke"))
        generator.events.send(TextEvent.Failed(GenerationError.NoProvider))

        assertEquals("Smoke", run.state.value.reply)
        assertEquals(RunStatus.Failed(GenerationError.NoProvider), run.state.value.status)
    }

    @Test
    fun `a blank prompt, or one while a reply streams, is not sent`() = runTest(UnconfinedTestDispatcher()) {
        val run = run()

        run.send("   ")
        run.send("Go")
        run.send("Again")

        assertEquals(1, generator.requests.size)
    }

    @Test
    fun `stopping closes the stream and keeps the text`() = runTest(UnconfinedTestDispatcher()) {
        val run = run()
        run.send("Go")
        generator.events.send(TextEvent.Delta("Smoke"))

        run.stop()

        assertTrue(generator.closed)
        assertEquals("Smoke", run.state.value.reply)
        assertEquals(RunStatus.Stopped, run.state.value.status)
    }

    @Test
    fun `a new prompt starts a fresh reply`() = runTest(UnconfinedTestDispatcher()) {
        val run = run()
        run.send("Go")
        generator.events.send(TextEvent.Delta("Old"))
        generator.events.send(TextEvent.Completed(usage = null, cutOff = false))
        generator.events = Channel(Channel.UNLIMITED)

        run.send("Again")

        assertEquals("", run.state.value.reply)
        assertEquals(RunStatus.Streaming, run.state.value.status)
    }

    @Test
    fun `leaving while idle leaves at once`() = runTest(UnconfinedTestDispatcher()) {
        val run = run()

        run.events.test {
            run.leave()

            assertEquals(TryEvent.Leave, awaitItem())
        }
    }

    @Test
    fun `leaving mid-stream asks first, and staying lets the reply go on`() = runTest(UnconfinedTestDispatcher()) {
        val run = run()
        run.send("Go")

        run.events.test {
            run.leave()
            assertTrue(run.state.value.confirmingLeave)
            expectNoEvents()

            run.stay()
            generator.events.send(TextEvent.Delta("More"))

            assertEquals(false, run.state.value.confirmingLeave)
            assertEquals("More", run.state.value.reply)
            assertEquals(false, generator.closed)
        }
    }

    @Test
    fun `confirming the leave stops the reply, then leaves`() = runTest(UnconfinedTestDispatcher()) {
        val run = run()
        run.send("Go")

        run.events.test {
            run.leave()
            run.confirmLeave()

            assertEquals(TryEvent.Leave, awaitItem())
        }
        assertTrue(generator.closed)
        assertEquals(RunStatus.Stopped, run.state.value.status)
    }

    @Test
    fun `without a chosen model nothing can be sent`() = runTest(UnconfinedTestDispatcher()) {
        val run = run(FakeModelCatalog())

        run.send("Go")

        assertTrue(run.state.value.noModel)
        assertEquals(emptyList(), generator.requests)
    }
}
