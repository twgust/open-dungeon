package dev.opendungeon.feature.newstory.domain.policy

import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.domain.story.Take
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.Selection
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.StepPolicy
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** The auditioned openings. Only a finished take can be chosen, and the story begins from the chosen one. */
@Inject
@ContributesIntoSet(AppScope::class)
public class FirstTurnPolicy : StepPolicy {

    override val step: CreationStep = CreationStep.FirstTurn

    override fun body(draft: StoryDraft): StepBody = StepBody.Auditions(draft.takes, draft.chosenTake)

    override fun canAdvance(draft: StoryDraft): Boolean =
        draft.takes.any { it.id == draft.chosenTake && it.state == Take.State.Written }

    override fun select(draft: StoryDraft, choice: Choice): Selection? {
        if (choice !is Choice.Take || draft.takes.none { it.id == choice.id && it.state == Take.State.Written }) return null
        return Selection(draft.copy(chosenTake = choice.id), advance = false)
    }
}
