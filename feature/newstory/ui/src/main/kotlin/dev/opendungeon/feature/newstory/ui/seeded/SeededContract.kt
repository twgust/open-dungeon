package dev.opendungeon.feature.newstory.ui.seeded

import dev.opendungeon.domain.story.Register
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.LeaveAnswer
import dev.opendungeon.feature.newstory.domain.WizardFailure
import dev.opendungeon.feature.newstory.domain.seeded.Person
import dev.opendungeon.feature.newstory.domain.seeded.SeededState

/** What the seeded screen renders: the seeded wizard's own state, already decided. */
sealed interface SeededUiState {
    data object Loading : SeededUiState

    data class Ready(val wizard: SeededState) : SeededUiState
}

/** Everything the player does on the seeded screen, said as it happened. */
sealed interface SeededIntent {
    /** The screen is showing. Deals the first seed the first time; later times change nothing. */
    data object Open : SeededIntent

    /** A blank of the seed was tapped. */
    data class PickBlank(val blank: String) : SeededIntent

    /** A suggestion was tapped for a blank, or the player used their own words for it. */
    data class FillBlank(val blank: String, val words: String) : SeededIntent

    /** The wand under a blank's suggestions: a model writes more. */
    data class MoreWords(val blank: String) : SeededIntent

    /** The die: another seed. */
    data object RollSeed : SeededIntent

    /** A shelf was tapped: seeds come from it from now on; `null` is the mixed one. */
    data class PickShelf(val shelf: Register?) : SeededIntent

    data class Describe(val person: Person, val text: String) : SeededIntent

    data class Pick(val choice: Choice) : SeededIntent

    /** The player's own limits. */
    data class Own(val text: String) : SeededIntent

    data object Next : SeededIntent

    data object Back : SeededIntent

    data object Stop : SeededIntent

    data object Begin : SeededIntent

    data class AnswerLeave(val answer: LeaveAnswer) : SeededIntent

    /** The model line was tapped: the player wants another model to write. */
    data object ChooseModel : SeededIntent
}

sealed interface SeededEffect {
    /** The seeded path was left from its first step. */
    data object Leave : SeededEffect

    /** The story began, and play starts from it. */
    data class Begin(val story: StoryId) : SeededEffect

    /** Something the player did could not be kept; the screen already shows it taken back. */
    data class ShowFailure(val failure: WizardFailure) : SeededEffect

    /** Open the models, to choose the one that writes. The seeded screen stays open beneath. */
    data object OpenModels : SeededEffect
}
