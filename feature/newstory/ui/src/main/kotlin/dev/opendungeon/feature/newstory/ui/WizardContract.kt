package dev.opendungeon.feature.newstory.ui

import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.DraftEdit
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
    data class Pick(val choice: Choice) : WizardIntent

    data class Own(val text: String) : WizardIntent

    data class Edit(val edit: DraftEdit) : WizardIntent

    data object Roll : WizardIntent

    data class RollLine(val line: CharacterLine) : WizardIntent

    data object Next : WizardIntent

    data object Back : WizardIntent
}

sealed interface WizardEffect {
    /** Creation was left from its first step; the draft is kept to resume. */
    data object Leave : WizardEffect
}
