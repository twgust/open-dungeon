package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.domain.drafting.CastPeople
import dev.opendungeon.domain.drafting.DraftingError
import dev.opendungeon.domain.drafting.DraftingModel
import dev.opendungeon.domain.drafting.GeneratePremise
import dev.opendungeon.domain.drafting.ResolveDraftingModel
import dev.opendungeon.domain.drafting.WriteOpening
import dev.opendungeon.domain.drafting.WritingProgress
import dev.opendungeon.domain.openrouter.testing.aiModel
import dev.opendungeon.domain.story.BeginStory
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.IdGenerator
import dev.opendungeon.domain.story.Register
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.domain.story.StoryDraftRepository
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.feature.newstory.domain.policy.CastPolicy
import dev.opendungeon.feature.newstory.domain.policy.GenrePolicy
import dev.opendungeon.feature.newstory.domain.policy.LimitsPolicy
import dev.opendungeon.feature.newstory.domain.policy.OpeningPolicy
import dev.opendungeon.feature.newstory.domain.policy.PremisePolicy
import dev.opendungeon.feature.newstory.domain.policy.SettingPolicy
import dev.opendungeon.feature.newstory.domain.policy.StarringPolicy
import dev.opendungeon.feature.newstory.domain.policy.TonePolicy
import dev.opendungeon.feature.newstory.domain.policy.TwistPolicy
import dev.opendungeon.feature.newstory.domain.vocabulary.RollChoice
import dev.opendungeon.feature.newstory.domain.vocabulary.VocabularyList
import dev.opendungeon.feature.newstory.domain.vocabulary.WizardVocabulary
import dev.opendungeon.feature.newstory.domain.vocabulary.WordGroup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlin.coroutines.cancellation.CancellationException

/**
 * Short, recognisable lists, so assertions can name the words. Each list has one group per register
 * the tests need: a dark one first, as most tests' stories are, then an everyday and an after-dark one.
 */
internal object TestVocabulary : WizardVocabulary {
    private fun dark(vararg words: String) = WordGroup("Dark", setOf(Register.Dark), words.toList())

    private fun everyday(vararg words: String) = WordGroup("Everyday", setOf(Register.Everyday), words.toList())

    private fun afterDark(vararg words: String) = WordGroup("After dark", setOf(Register.AfterDark), words.toList())

    private val lists = mapOf(
        VocabularyList.Genres to listOf(
            dark("Gothic", "Noir", "Western", "Horror"),
            everyday("Slice of life"),
            afterDark("Erotic"),
        ),
        VocabularyList.Twists to
            listOf(dark("a heist", "a curse", "a wedding"), everyday("a first date"), afterDark("an affair")),
        VocabularyList.Roles to listOf(
            dark("a disgraced smuggler", "a forger", "a ferry pilot"),
            everyday("a nurse", "a barista"),
            afterDark("an escort"),
        ),
        VocabularyList.Tones to listOf(dark("tenderly", "coldly"), everyday("warmly"), afterDark("hungrily")),
        VocabularyList.Settings to listOf(
            dark("a salt-war border town", "a drowned city"),
            everyday("a launderette"),
            afterDark("a sauna"),
        ),
        VocabularyList.Wants to
            listOf(dark("To be forgiven", "To get even"), everyday("To make rent"), afterDark("To be wanted")),
        VocabularyList.Flaws to listOf(
            dark("Lies easily", "Keeps every grudge"),
            everyday("Says yes to everything"),
            afterDark("Can't say no to a dare"),
        ),
        VocabularyList.GoodAt to listOf(
            dark("Picking locks", "Making strangers talk"),
            everyday("Fixing things"),
            afterDark("Flirting"),
        ),
    )

    override fun groups(list: VocabularyList): List<WordGroup> = lists.getValue(list)
}

/** Rolls the first words that are not being avoided, in the order given. */
internal class FirstWordsRoll : RollChoice {
    override fun invoke(words: List<String>, count: Int, avoiding: Collection<String>): List<String> {
        val (fresh, seen) = words.partition { it !in avoiding }
        return (fresh + seen).take(count)
    }
}

internal class CountingIds(private val prefix: String = "id") : IdGenerator {
    private var next = 0

    override fun next(): String = "$prefix-${++next}"
}

/**
 * Drafts in a map. [failReads] and [failSaves] make that many of the next reads or saves fail with
 * [StorageError.Full], so tests can walk the failure paths without a database.
 */
internal class InMemoryDraftRepository(vararg drafts: StoryDraft) : StoryDraftRepository {
    val drafts = MutableStateFlow(drafts.associateBy(StoryDraft::id))
    var saves = 0
        private set

    var failReads = 0
    var failSaves = 0

    val stored: StoryDraft
        get() = drafts.value.values.single()

    override suspend fun openDraft(): Outcome<StoryDraft?, StorageError> = read { drafts.value.values.firstOrNull() }

    override suspend fun draft(id: DraftId): Outcome<StoryDraft?, StorageError> = read { drafts.value[id] }

    override suspend fun save(draft: StoryDraft): Outcome<Unit, StorageError> = write {
        saves++
        drafts.value = mapOf(draft.id to draft)
    }

    override suspend fun discard(id: DraftId): Outcome<Unit, StorageError> = write { drafts.value -= id }

    private inline fun <T> read(block: () -> T): Outcome<T, StorageError> {
        if (failReads > 0) {
            failReads--
            return Outcome.Failure(StorageError.Full)
        }
        return Outcome.Success(block())
    }

    private inline fun <T> write(block: () -> T): Outcome<T, StorageError> {
        if (failSaves > 0) {
            failSaves--
            return Outcome.Failure(StorageError.Full)
        }
        return Outcome.Success(block())
    }
}

internal val emptyDraft = StoryDraft(DraftId("draft"))

/** One policy per step, as the app binds them, with the model's work scripted. */
internal fun wizardPolicies(
    premise: GeneratePremise = ScriptedWriting(),
    cast: CastPeople = ScriptedCast(),
    opening: WriteOpening = ScriptedWriting(),
): Set<StepPolicy> = setOf(
    GenrePolicy(TestVocabulary, FirstWordsRoll()),
    TwistPolicy(TestVocabulary, FirstWordsRoll()),
    StarringPolicy(TestVocabulary, FirstWordsRoll(), CountingIds("you")),
    TonePolicy(TestVocabulary, FirstWordsRoll()),
    SettingPolicy(TestVocabulary, FirstWordsRoll()),
    LimitsPolicy(),
    PremisePolicy(premise),
    CastPolicy(cast),
    OpeningPolicy(opening),
)

internal val storyteller = DraftingModel(aiModel("acme/storyteller", name = "Storyteller"), Tuning.Untouched)

/** The drafting model a test sets; none until it does. */
internal class FakeDrafter(model: DraftingModel? = null) : ResolveDraftingModel {
    val model = MutableStateFlow(model)

    override fun invoke(): Flow<DraftingModel?> = model
}

/** Story text the test writes by hand, a premise or an opening: every run waits for what the test sends it. */
internal class ScriptedWriting :
    GeneratePremise,
    WriteOpening {
    val runs = mutableListOf<Run>()

    class Run(val draft: StoryDraft, val model: DraftingModel) {
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

    val last: Run get() = runs.last()

    override fun invoke(draft: StoryDraft, model: DraftingModel): Flow<WritingProgress> = flow {
        val run = Run(draft, model)
        runs += run
        emitAll(run.flow().onCompletion { cause -> if (cause is CancellationException) run.cancelled = true })
    }
}

/** Casts the test answers by hand: every call waits for its answer. */
internal class ScriptedCast : CastPeople {
    val calls = mutableListOf<Pair<StoryDraft, CompletableDeferred<Outcome<List<Character>, DraftingError>>>>()

    fun answer(outcome: Outcome<List<Character>, DraftingError>) {
        calls.last().second.complete(outcome)
    }

    override suspend fun invoke(
        draft: StoryDraft,
        model: DraftingModel,
        count: Int,
    ): Outcome<List<Character>, DraftingError> {
        val answer = CompletableDeferred<Outcome<List<Character>, DraftingError>>()
        calls += draft to answer
        return answer.await()
    }
}

/**
 * Begins stories as the app does, closing the draft in [drafts], and keeps what it began from. [fails]
 * makes that many of the next calls fail with [StorageError.Full], leaving the draft where it was.
 */
internal class FakeBeginStory(private val drafts: InMemoryDraftRepository) : BeginStory {
    val begun = mutableListOf<StoryDraft>()
    var fails = 0

    override suspend fun invoke(draft: StoryDraft): Outcome<StoryId, StorageError> {
        if (fails > 0) {
            fails--
            return Outcome.Failure(StorageError.Full)
        }
        begun += draft
        drafts.drafts.value -= draft.id
        return Outcome.Success(StoryId("story-${begun.size}"))
    }
}
