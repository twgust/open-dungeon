package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Sentence

/** Everything the wizard shows for the current step, already decided. Screens only render it. */
public data class WizardState(
    val draft: DraftId,
    val step: CreationStep,
    /** How far through creation the current step is, from just above 0 to 1 on the last step. */
    val progress: Float,
    /** The sentence so far, shown above the step's prompt. Empty where the prompt stands alone. */
    val lead: Sentence,
    val body: StepBody,
    val controls: StepControls,
)

/** Which of the step's fixed controls are live. */
public data class StepControls(
    val canGoBack: Boolean,
    val canAdvance: Boolean,
    /** Picking completes the step, so it shows no next arrow; tapping the current pick moves on. */
    val advancesOnPick: Boolean,
    /** The step has a die, and rolling it would change something. */
    val canRoll: Boolean,
    /** The step takes the player's own words in its "my own" pill. */
    val takesOwnWords: Boolean,
)

/** Something the wizard decided that is not part of its state. */
public sealed interface WizardOutcome {
    /** Back from the first step: creation is left, and the draft is kept to resume. */
    public data object Left : WizardOutcome
}
