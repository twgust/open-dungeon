package dev.opendungeon.feature.newstory.domain.policy

import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.feature.newstory.domain.DraftEdit
import dev.opendungeon.feature.newstory.domain.Selection
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.StepPolicy
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/**
 * "It begins": the premise, editable in place. The step needs text to be left. Writing a premise with
 * a model is the die, which arrives with the drafting use cases.
 */
@Inject
@ContributesIntoSet(AppScope::class)
public class PremisePolicy : StepPolicy {

    override val step: CreationStep = CreationStep.Premise

    override fun body(draft: StoryDraft): StepBody =
        StepBody.Premise(text = draft.premise?.text.orEmpty(), own = draft.premise is Phrase.Own)

    override fun canAdvance(draft: StoryDraft): Boolean = draft.premise != null

    override fun takesOwnWords(draft: StoryDraft): Boolean = true

    override fun submitOwn(draft: StoryDraft, text: String): Selection =
        Selection(draft.withPremise(text), advance = false)

    override fun edit(draft: StoryDraft, edit: DraftEdit): StoryDraft? =
        (edit as? DraftEdit.Premise)?.let { draft.withPremise(it.text) }

    /** Any edit makes the premise the player's own words, even one that started as a generated one. */
    private fun StoryDraft.withPremise(text: String) =
        copy(premise = text.trim().takeIf(String::isNotEmpty)?.let(Phrase::Own))
}
