package dev.opendungeon.feature.newstory.domain.policy

import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.Selection
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.StepPolicy
import dev.opendungeon.feature.newstory.domain.vocabulary.RollChoice
import dev.opendungeon.feature.newstory.domain.vocabulary.VocabularyList
import dev.opendungeon.feature.newstory.domain.vocabulary.WizardVocabulary
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Up to two genres, offered or the player's own. Tapping a picked genre drops it; the die picks two and moves on. */
@Inject
@ContributesIntoSet(AppScope::class)
public class GenrePolicy(
    private val vocabulary: WizardVocabulary,
    private val rollChoice: RollChoice,
) : StepPolicy {

    override val step: CreationStep = CreationStep.Genre

    private val offered: List<String> get() = vocabulary[VocabularyList.Genres]

    override fun body(draft: StoryDraft): StepBody =
        StepBody.Words(wordOptions(offered, draft.genres), maxPicks = StoryDraft.MAX_GENRES)

    override fun canAdvance(draft: StoryDraft): Boolean = draft.genres.isNotEmpty()

    override fun canRoll(draft: StoryDraft): Boolean = true

    override fun takesOwnWords(draft: StoryDraft): Boolean = draft.genres.size < StoryDraft.MAX_GENRES

    override fun select(draft: StoryDraft, choice: Choice): Selection? {
        if (choice !is Choice.Word) return null
        val picked = draft.genres.firstOrNull { it.text == choice.text }
        val genres = when {
            picked != null -> draft.genres - picked
            choice.text in offered && takesOwnWords(draft) -> draft.genres + Phrase.Offered(choice.text)
            else -> return null
        }
        return Selection(draft.copy(genres = genres), advance = false)
    }

    override fun submitOwn(draft: StoryDraft, text: String): Selection? {
        if (!takesOwnWords(draft)) return null
        val genre = phraseFor(text, offered) ?: return null
        if (draft.genres.any { it.text.equals(genre.text, ignoreCase = true) }) return null
        return Selection(draft.copy(genres = draft.genres + genre), advance = false)
    }

    override suspend fun roll(draft: StoryDraft): Selection {
        val genres = rollChoice(VocabularyList.Genres, StoryDraft.MAX_GENRES, avoiding = draft.genres.map(Phrase::text))
        return Selection(draft.copy(genres = genres.map(Phrase::Offered)), advance = true)
    }
}
