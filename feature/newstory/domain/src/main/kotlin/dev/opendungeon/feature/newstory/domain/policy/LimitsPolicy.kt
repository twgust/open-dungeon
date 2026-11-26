package dev.opendungeon.feature.newstory.domain.policy

import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.DraftEdit
import dev.opendungeon.feature.newstory.domain.Selection
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.StepPolicy
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Two dials and the player's own note. Every setting is a valid answer, so the step can always be left. */
@Inject
@ContributesIntoSet(AppScope::class)
public class LimitsPolicy : StepPolicy {

    override val step: CreationStep = CreationStep.Limits

    override fun body(draft: StoryDraft): StepBody = StepBody.Limits(draft.limits)

    override fun canAdvance(draft: StoryDraft): Boolean = true

    override fun takesOwnWords(draft: StoryDraft): Boolean = true

    override fun select(draft: StoryDraft, choice: Choice): Selection? {
        val limits = when (choice) {
            is Choice.Desire -> draft.limits.copy(desire = choice.level)
            is Choice.Violence -> draft.limits.copy(violence = choice.level)
            else -> return null
        }
        return Selection(draft.copy(limits = limits), advance = false)
    }

    override fun submitOwn(draft: StoryDraft, text: String): Selection =
        Selection(draft.withNote(text.trim()), advance = false)

    /** Kept as typed, so the field being edited reads back exactly what it holds. */
    override fun edit(draft: StoryDraft, edit: DraftEdit): StoryDraft? =
        (edit as? DraftEdit.LimitsNote)?.let { draft.withNote(it.text) }

    /** Blank clears the note. */
    private fun StoryDraft.withNote(text: String) = copy(limits = limits.copy(note = text.takeIf(String::isNotBlank)))
}
