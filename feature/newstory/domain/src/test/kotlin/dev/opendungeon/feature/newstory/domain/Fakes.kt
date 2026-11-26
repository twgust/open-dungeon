package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.IdGenerator
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.domain.story.StoryDraftRepository
import dev.opendungeon.feature.newstory.domain.vocabulary.RollChoice
import dev.opendungeon.feature.newstory.domain.vocabulary.VocabularyList
import dev.opendungeon.feature.newstory.domain.vocabulary.WizardVocabulary
import kotlinx.coroutines.flow.MutableStateFlow

/** Short, recognisable lists, so assertions can name the words. */
internal object TestVocabulary : WizardVocabulary {
    private val lists = mapOf(
        VocabularyList.Genres to listOf("Gothic", "Noir", "Western", "Horror"),
        VocabularyList.Twists to listOf("a heist", "a curse", "a wedding"),
        VocabularyList.Roles to listOf("a disgraced smuggler", "a forger", "a ferry pilot"),
        VocabularyList.Tones to listOf("tenderly", "coldly"),
        VocabularyList.Settings to listOf("a salt-war border town", "a drowned city"),
        VocabularyList.Wants to listOf("To be forgiven", "To get even"),
        VocabularyList.Flaws to listOf("Lies easily", "Keeps every grudge"),
        VocabularyList.GoodAt to listOf("Picking locks", "Making strangers talk"),
    )

    override fun get(list: VocabularyList): List<String> = lists.getValue(list)
}

/** Rolls the first words that are not being avoided, in list order. */
internal class FirstWordsRoll(private val vocabulary: WizardVocabulary = TestVocabulary) : RollChoice {
    override fun invoke(list: VocabularyList, count: Int, avoiding: Collection<String>): List<String> {
        val (fresh, seen) = vocabulary[list].partition { it !in avoiding }
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
