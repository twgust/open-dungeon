package dev.opendungeon.data.models

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.opendungeon.core.database.OpenDungeonDatabase
import dev.opendungeon.core.database.model.ModelTuningEntity
import dev.opendungeon.core.database.model.TuningColumns
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.model.Reasoning
import dev.opendungeon.core.model.ReasoningEffort
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.core.model.Verbosity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class DefaultModelSetupRepositoryTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        OpenDungeonDatabase::class.java,
    ).allowMainThreadQueries().build()

    private val repository = DefaultModelSetupRepository(database.setupDao())

    private val hermes = ModelId("nous/hermes-4-405b")
    private val claude = ModelId("anthropic/claude-sonnet-4.5")

    @After
    fun close() = database.close()

    @Test
    fun `the chosen model is remembered and replaced`() = runTest {
        repository.chosenModel.test {
            assertNull(awaitItem())
            assertEquals(Outcome.Success(Unit), repository.choose(hermes))
            assertEquals(hermes, awaitItem())
            repository.choose(claude)
            assertEquals(claude, awaitItem())
        }
    }

    @Test
    fun `every tuning reads back exactly`() = runTest {
        val tunings = listOf(
            Tuning(
                temperature = 1.2,
                topP = 0.9,
                topK = 0,
                minP = 0.05,
                topA = 0.1,
                frequencyPenalty = -0.5,
                presencePenalty = 0.4,
                repetitionPenalty = 1.1,
                maxTokens = 900,
                seed = 9_007_199_254_740_993,
                stop = listOf("THE END", "\n\n"),
                verbosity = Verbosity.ExtraHigh,
                reasoning = Reasoning.On(ReasoningEffort("xhigh")),
            ),
            Tuning(reasoning = Reasoning.On(effort = null)),
            Tuning(reasoning = Reasoning.Off),
            Tuning(reasoning = Reasoning.Budget(4_096)),
            Tuning.Untouched,
        )

        for (tuning in tunings) {
            repository.saveTuning(hermes, tuning)
            assertEquals(tuning, repository.tuning(hermes).first())
        }
    }

    @Test
    fun `each model keeps its own tuning`() = runTest {
        repository.saveTuning(hermes, Tuning(temperature = 0.3))
        repository.saveTuning(claude, Tuning(temperature = 1.5))

        assertEquals(Tuning(temperature = 0.3), repository.tuning(hermes).first())
        assertEquals(Tuning(temperature = 1.5), repository.tuning(claude).first())
        assertEquals(Tuning.Untouched, repository.tuning(ModelId("never/tuned")).first())
    }

    @Test
    fun `a stored tuning that no longer passes the checks comes back untouched`() = runTest {
        database.setupDao().upsertTuning(ModelTuningEntity(hermes.value, TuningColumns(temperature = 7.0)))

        assertEquals(Tuning.Untouched, repository.tuning(hermes).first())
    }
}
