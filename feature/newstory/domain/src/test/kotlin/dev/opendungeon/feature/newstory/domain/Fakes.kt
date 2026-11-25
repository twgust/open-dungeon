package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.IdGenerator
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.domain.story.StoryDraftRepository
import dev.opendungeon.feature.newstory.domain.vocabulary.RollChoice
import dev.opendungeon.feature.newstory.domain.vocabulary.VocabularyList
import dev.opendungeon.feature.newstory.domain.vocabulary.WizardVocabulary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

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

internal class InMemoryDraftRepository(vararg drafts: StoryDraft) : StoryDraftRepository {
    val drafts = MutableStateFlow(drafts.associateBy(StoryDraft::id))
    var saves = 0
        private set

    override suspend fun openDraft(): StoryDraft? = drafts.value.values.firstOrNull()

    override fun observe(id: DraftId): Flow<StoryDraft?> = drafts.map { it[id] }

    override suspend fun save(draft: StoryDraft) {
        saves++
        drafts.value = mapOf(draft.id to draft)
    }

    override suspend fun discard(id: DraftId) {
        drafts.value -= id
    }
}

internal val emptyDraft = StoryDraft(DraftId("draft"))
