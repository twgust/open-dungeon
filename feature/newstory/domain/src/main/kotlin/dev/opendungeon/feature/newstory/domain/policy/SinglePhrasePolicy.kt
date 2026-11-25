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
import dev.zacsweers.metro.binding

/**
 * A step that takes exactly one phrase. Any pick completes it: a tap, the player's own words, or the
 * die. Tapping the current pick again moves on with it.
 */
public abstract class SinglePhrasePolicy(
    final override val step: CreationStep,
    private val list: VocabularyList,
    private val vocabulary: WizardVocabulary,
    private val rollChoice: RollChoice,
) : StepPolicy {

    protected abstract fun StoryDraft.phrase(): Phrase?

    protected abstract fun StoryDraft.withPhrase(phrase: Phrase): StoryDraft

    private val offered: List<String> get() = vocabulary[list]

    override fun body(draft: StoryDraft): StepBody = StepBody.Words(wordOptions(offered, listOfNotNull(draft.phrase())), maxPicks = 1)

    override fun canAdvance(draft: StoryDraft): Boolean = draft.phrase() != null

    override fun canRoll(draft: StoryDraft): Boolean = true

    override val advancesOnPick: Boolean get() = true

    override fun takesOwnWords(draft: StoryDraft): Boolean = true

    override fun select(draft: StoryDraft, choice: Choice): Selection? {
        if (choice !is Choice.Word) return null
        return when (choice.text) {
            draft.phrase()?.text -> Selection(draft, advance = true)
            in offered -> Selection(draft.withPhrase(Phrase.Offered(choice.text)), advance = true)
            else -> null
        }
    }

    override fun submitOwn(draft: StoryDraft, text: String): Selection? =
        phraseFor(text, offered)?.let { Selection(draft.withPhrase(it), advance = true) }

    override suspend fun roll(draft: StoryDraft): Selection {
        val word = rollChoice(list, avoiding = listOfNotNull(draft.phrase()?.text)).single()
        return Selection(draft.withPhrase(Phrase.Offered(word)), advance = true)
    }
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<StepPolicy>())
public class TwistPolicy(vocabulary: WizardVocabulary, rollChoice: RollChoice) :
    SinglePhrasePolicy(CreationStep.Twist, VocabularyList.Twists, vocabulary, rollChoice) {
    override fun StoryDraft.phrase(): Phrase? = twist
    override fun StoryDraft.withPhrase(phrase: Phrase): StoryDraft = copy(twist = phrase)
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<StepPolicy>())
public class TonePolicy(vocabulary: WizardVocabulary, rollChoice: RollChoice) :
    SinglePhrasePolicy(CreationStep.Tone, VocabularyList.Tones, vocabulary, rollChoice) {
    override fun StoryDraft.phrase(): Phrase? = tone
    override fun StoryDraft.withPhrase(phrase: Phrase): StoryDraft = copy(tone = phrase)
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<StepPolicy>())
public class SettingPolicy(vocabulary: WizardVocabulary, rollChoice: RollChoice) :
    SinglePhrasePolicy(CreationStep.Setting, VocabularyList.Settings, vocabulary, rollChoice) {
    override fun StoryDraft.phrase(): Phrase? = setting
    override fun StoryDraft.withPhrase(phrase: Phrase): StoryDraft = copy(setting = phrase)
}
