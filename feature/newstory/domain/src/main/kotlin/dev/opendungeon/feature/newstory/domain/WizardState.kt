package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.drafting.DraftingError
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
    /** The model that writes this step, on steps a model writes; `null` on every other step. */
    val writer: ModelLine? = null,
    /** Back was pressed while a model writes: leaving stops it, so the player is asked first. */
    val confirmingLeave: Boolean = false,
)

/** The quiet line naming the model that writes the current step. */
public data class ModelLine(
    /** The model's name, or `null` when none is chosen: the line then offers to choose one. */
    val model: String?,
    /** A model is writing now. Every other control on the step waits, and the step offers to stop. */
    val writing: Boolean,
)

/** The player's answer to being asked whether to leave a step a model is writing. */
public enum class LeaveAnswer {
    /** The writing goes on. */
    Stay,

    /** The writing stops, keeping nothing of it, and the wizard steps back. */
    Leave,
}

/** Which of the step's fixed controls are live. */
public data class StepControls(
    val canGoBack: Boolean,
    /** The step can be left forward: by next, or on the last step by Begin. */
    val canAdvance: Boolean,
    /** This is the last step, so moving forward begins the story. */
    val begins: Boolean,
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

    /** The player began the story. [draft] is saved, opening and all, for play to start from. */
    public data class Begun(val draft: DraftId) : WizardOutcome

    /** Something the player did could not be kept. The state already shows the draft as it was before. */
    public data class Failed(val failure: WizardFailure) : WizardOutcome
}

/** Why a verb could not be kept. */
public sealed interface WizardFailure {
    /** The draft could not be read or written. Typed text stays on screen and is saved with the next change. */
    public data class Storage(val error: StorageError) : WizardFailure

    /** A model could not write the step. What was there before stays. */
    public data class Drafting(val error: DraftingError) : WizardFailure

    /** A rule failed in a way no rule should. The verb changed nothing; [cause] is kept for diagnosis. */
    public data class Unexpected(val cause: Exception) : WizardFailure
}
