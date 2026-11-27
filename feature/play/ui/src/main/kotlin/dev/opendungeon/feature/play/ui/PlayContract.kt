package dev.opendungeon.feature.play.ui

import dev.opendungeon.domain.story.BeatId
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.feature.play.domain.LeaveAnswer
import dev.opendungeon.feature.play.domain.Play
import dev.opendungeon.feature.play.domain.PlayFailure
import dev.opendungeon.feature.play.domain.Refusal

/** What the play screen renders: the session's own state, already decided. */
data class PlayUiState(val play: Play)

/** Everything the player does on the play screen, said as it happened. */
sealed interface PlayIntent {
    /** The screen is showing [story]. Opens it the first time, or again after it could not be read. */
    data class Open(val story: StoryId) : PlayIntent

    data class TypeTurn(val text: String) : PlayIntent

    /** Continue, or Send when a turn is typed. */
    data object Advance : PlayIntent

    data object Retry : PlayIntent

    data object OpenReason : PlayIntent

    data object OpenGuide : PlayIntent

    data class EditBeat(val beat: BeatId) : PlayIntent

    data class TypeNote(val text: String) : PlayIntent

    data object Submit : PlayIntent

    data object Cancel : PlayIntent

    data object RemoveGuide : PlayIntent

    /** By the Stop button or by tapping the text being written. */
    data object Stop : PlayIntent

    data object Undo : PlayIntent

    data object TryAgain : PlayIntent

    data object DismissFailure : PlayIntent

    /** The failure says another model would help. */
    data object ChooseModel : PlayIntent

    data object Back : PlayIntent

    data class AnswerLeave(val answer: LeaveAnswer) : PlayIntent
}

sealed interface PlayEffect {
    /** The story was left, everything in it saved. */
    data object Leave : PlayEffect

    /** Open the models, to choose the one that writes. The story stays open beneath. */
    data object OpenModels : PlayEffect

    /** Something the player did could not be kept; the screen already shows it taken back. */
    data class ShowFailure(val failure: PlayFailure) : PlayEffect

    /** A tap that cannot be followed now, said back briefly so it never goes unanswered. */
    data class ShowRefusal(val reason: Refusal) : PlayEffect
}
