package dev.opendungeon.feature.play.domain

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.domain.drafting.BeatAsk
import dev.opendungeon.domain.drafting.DraftingError
import dev.opendungeon.domain.drafting.DraftingModel
import dev.opendungeon.domain.drafting.ResolveDraftingModel
import dev.opendungeon.domain.drafting.WriteBeat
import dev.opendungeon.domain.drafting.WritingProgress
import dev.opendungeon.domain.openrouter.testing.aiModel
import dev.opendungeon.domain.story.Beat
import dev.opendungeon.domain.story.BeatId
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.IdGenerator
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.Story
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StoryRepository
import dev.opendungeon.domain.story.StorySetup
import dev.opendungeon.domain.story.StorySummary
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

internal val storyteller = DraftingModel(aiModel("acme/storyteller", name = "Storyteller"), Tuning.Untouched)

internal val opening = Beat(BeatId("b-open"), Beat.Kind.Opening, "Rain falls on the salt flats.")
internal val turn = Beat(BeatId("b-turn"), Beat.Kind.Turn, "You lay your hand on the key.")
internal val narration = Beat(BeatId("b-told"), Beat.Kind.Narration, "Maren does not pull away.")

/** A story that ends on a beat a model wrote, so it can be written again. */
internal val salt = Story(StoryId("salt"), StorySetup(), listOf(opening, turn, narration))

internal class CountingIds(private val prefix: String = "id") : IdGenerator {
    private var next = 0

    override fun next(): String = "$prefix-${++next}"
}

/**
 * Stories in a map. [failReads] and [failSaves] make that many of the next reads or saves fail with
 * [StorageError.Full], so tests can walk the failure paths without a database.
 */
internal class InMemoryStories(vararg stories: Story) : StoryRepository {
    val byId = MutableStateFlow(stories.associateBy(Story::id))
    val saved = mutableListOf<Story>()
    var failReads = 0
    var failSaves = 0

    fun stored(id: StoryId = salt.id): Story = byId.value.getValue(id)

    override suspend fun story(id: StoryId): Outcome<Story?, StorageError> {
        if (failReads > 0) {
            failReads--
            return Outcome.Failure(StorageError.Full)
        }
        return Outcome.Success(byId.value[id])
    }

    override val stories: Flow<List<StorySummary>> = byId.map { stories ->
        stories.values.map { StorySummary(it.id, it.title, it.setup.genres, Instant.DISTANT_PAST) }
    }

    override suspend fun begin(story: Story, from: DraftId): Outcome<Unit, StorageError> = save(story)

    override suspend fun save(story: Story): Outcome<Unit, StorageError> {
        if (failSaves > 0) {
            failSaves--
            return Outcome.Failure(StorageError.Full)
        }
        saved += story
        byId.value += story.id to story
        return Outcome.Success(Unit)
    }

    override suspend fun rename(id: StoryId, title: String): Outcome<Unit, StorageError> = error("Play never names")

    override suspend fun nameIfUntitled(id: StoryId, title: String): Outcome<Unit, StorageError> =
        error("Play never names")

    override suspend fun trash(id: StoryId): Outcome<Unit, StorageError> = error("Play never deletes")

    override suspend fun restore(id: StoryId): Outcome<Unit, StorageError> = error("Play never deletes")

    override suspend fun emptyTrash(): Outcome<Unit, StorageError> = error("Play never deletes")
}

/** The drafting model a test sets. */
internal class FakeDrafter(model: DraftingModel? = storyteller) : ResolveDraftingModel {
    val model = MutableStateFlow(model)

    override fun invoke(): Flow<DraftingModel?> = model
}

/** Beats the test writes by hand: every run waits for what the test sends it. */
internal class ScriptedBeats : WriteBeat {
    val runs = mutableListOf<Run>()
    val last: Run get() = runs.last()

    class Run(val story: Story, val ask: BeatAsk, val model: DraftingModel) {
        private val progress = Channel<WritingProgress>(Channel.UNLIMITED)
        var cancelled = false
            internal set

        fun writing(text: String) = progress.trySend(WritingProgress.Writing(text))

        fun written(text: String) = finish(WritingProgress.Written(text))

        fun failed(error: DraftingError) = finish(WritingProgress.Failed(error))

        internal fun flow() = progress.consumeAsFlow()

        private fun finish(last: WritingProgress) {
            progress.trySend(last)
            progress.close()
        }
    }

    override fun invoke(story: Story, ask: BeatAsk, model: DraftingModel): Flow<WritingProgress> = flow {
        val run = Run(story, ask, model)
        runs += run
        emitAll(run.flow().onCompletion { cause -> if (cause is CancellationException) run.cancelled = true })
    }
}
