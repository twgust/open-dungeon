package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Sentence
import dev.opendungeon.domain.story.StorageError

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
    /** Back from the first step, or before a draft could be opened: creation is left, the draft kept. */
    public data object Left : WizardOutcome

    /** Something the player did could not be kept. The state already shows the draft as it was before. */
    public data class Failed(val failure: WizardFailure) : WizardOutcome
}

/** Why a verb could not be kept. */
public sealed interface WizardFailure {
    /** The draft could not be read or written. Typed text stays on screen and is saved with the next change. */
    public data class Storage(val error: StorageError) : WizardFailure

    /** A rule failed in a way no rule should. The verb changed nothing; [cause] is kept for diagnosis. */
    public data class Unexpected(val cause: Exception) : WizardFailure
}
