package dev.opendungeon.feature.newstory.domain.policy

import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.feature.newstory.domain.DraftEdit
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.StepPolicy
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** The player's optional first line. Left blank, the opening take writes the whole beginning. */
@Inject
@ContributesIntoSet(AppScope::class)
public class FirstLinePolicy : StepPolicy {

    override val step: CreationStep = CreationStep.FirstLine

    override fun body(draft: StoryDraft): StepBody =
        StepBody.FirstLine(draft.firstLine, StoryDraft.MAX_FIRST_LINE_LENGTH)

    override fun canAdvance(draft: StoryDraft): Boolean = true

    /** Kept as typed, line breaks and all, up to the limit. */
    override fun edit(draft: StoryDraft, edit: DraftEdit): StoryDraft? =
        (edit as? DraftEdit.FirstLine)?.let { draft.copy(firstLine = it.text.take(StoryDraft.MAX_FIRST_LINE_LENGTH)) }
}
