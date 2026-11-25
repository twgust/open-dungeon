package dev.opendungeon.feature.newstory.domain.policy

import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.Selection
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.StepPolicy
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** "Among them": the people on offer, each in or out of the story. At least one has to be in. */
@Inject
@ContributesIntoSet(AppScope::class)
public class CastPolicy : StepPolicy {

    override val step: CreationStep = CreationStep.Cast

    override fun body(draft: StoryDraft): StepBody = StepBody.Cast(draft.cast)

    override fun canAdvance(draft: StoryDraft): Boolean = draft.cast.any { it.inStory }

    override fun select(draft: StoryDraft, choice: Choice): Selection? {
        if (choice !is Choice.CastMember || draft.cast.none { it.character.id == choice.id }) return null
        val cast = draft.cast.map { if (it.character.id == choice.id) it.copy(inStory = !it.inStory) else it }
        return Selection(draft.copy(cast = cast), advance = false)
    }
}
