package dev.opendungeon.feature.newstory.ui

import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.DraftEdit
import dev.opendungeon.feature.newstory.domain.LeaveAnswer
import dev.opendungeon.feature.newstory.domain.WizardFailure
import dev.opendungeon.feature.newstory.domain.WizardState

/**
 * What the wizard screen renders: the wizard's own state, already decided. Every card in it is a
 * projection the step's policy made, so it never holds the draft, and never a sealed secret's words.
 */
sealed interface WizardUiState {
    data object Loading : WizardUiState

    data class Ready(val wizard: WizardState) : WizardUiState
}

/** Everything the player does on the wizard, said as it happened. */
sealed interface WizardIntent {
    /** The screen is showing. Opens the draft the first time; later times change nothing. */
    data object Open : WizardIntent

    data class Pick(val choice: Choice) : WizardIntent

    data class Own(val text: String) : WizardIntent

    data class Edit(val edit: DraftEdit) : WizardIntent

    /** The step's die, or with [line] the die beside that line of the player's card. */
    data class Roll(val line: CharacterLine? = null) : WizardIntent

    data object Next : WizardIntent

    data object Back : WizardIntent

    /** Stop the model writing the step, by its stop button or by tapping the text it writes. */
    data object Stop : WizardIntent

    /** Take back what the model last added. */
    data object Undo : WizardIntent

    /** Begin the story from the last step. */
    data object Begin : WizardIntent

    /** The answer to whether to leave a step a model is writing. */
    data class AnswerLeave(val answer: LeaveAnswer) : WizardIntent

    /** The model line was tapped: the player wants another model to write. */
    data object ChooseModel : WizardIntent
}

sealed interface WizardEffect {
    /** Creation was left from its first step; the draft is kept to resume. */
    data object Leave : WizardEffect

    /** The player began the story, which play starts from. */
    data class Begin(val story: StoryId) : WizardEffect

    /** Something the player did could not be kept; the screen already shows it taken back. */
    data class ShowFailure(val failure: WizardFailure) : WizardEffect

    /** Open the models, to choose the one that writes. The wizard stays open beneath. */
    data object OpenModels : WizardEffect
}
