package dev.opendungeon.feature.play.domain

import dev.opendungeon.domain.drafting.DraftingError
import dev.opendungeon.domain.story.Beat
import dev.opendungeon.domain.story.BeatId
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.StoryId

/** Where opening the story stands. */
public sealed interface Play {
    /** Reading the story. */
    public data object Opening : Play

    /** There is no story with this id, for example after it was removed. */
    public data object Missing : Play

    /** The story could not be read. Starting again tries again. */
    public data class Unreadable(val error: StorageError) : Play

    public data class Playing(val state: PlayState) : Play
}

/** Everything the play screen shows, already decided. Screens only render it. */
public data class PlayState(
    val story: StoryId,
    /** Oldest first, as shown: a turn being sent is already among them. */
    val beats: List<PlayBeat>,
    /** What a model is writing now: after the beats, or in place of the last one. */
    val writing: Writing?,
    val turn: TurnField,
    /** The direction waiting for the next beat, shown above the bar; `null` when there is none or it is being edited. */
    val guide: String?,
    /** The note or edit the player has open. An edit shows in place of its beat, a note above the bar. */
    val composer: ComposerState?,
    /** Retry and Retry with a reason are offered under the last beat. */
    val retryOffered: Boolean,
    /** The line above the bar. */
    val status: Status?,
    val secondary: Secondary,
    val primary: Primary,
    /** Back was pressed while a model writes: leaving stops it, so the player is asked first. */
    val confirmingLeave: Boolean = false,
)

/** A beat as shown. */
public data class PlayBeat(val id: BeatId, val kind: Beat.Kind, val text: String, val look: Look) {
    public enum class Look {
        Plain,

        /** Set back while the player edits another beat or says what was wrong with the last one. */
        Faded,

        /** Open for editing: the composer's text shows in its place. */
        Editing,

        /** Being written again: what the model writes shows in its place. */
        Rewritten,
    }
}

/** What a model is writing. [text] is `null` until its first words arrive. */
public data class Writing(val again: Boolean, val text: String?)

/** The player's next turn. [enabled] is false while a model writes, so a turn is never typed into a send. */
public data class TurnField(val text: String, val enabled: Boolean, val maxLength: Int)

public data class ComposerState(val kind: Kind, val text: String, val maxLength: Int) {
    public enum class Kind {
        /** A direction for the next beat. */
        Guide,

        /** What was wrong with the last beat. */
        Reason,

        /** A beat edited in place. */
        Edit,
    }
}

/** The line above the bar: what is under way, what just changed with the way back from it, or what failed. */
public sealed interface Status {
    public data class Writing(val again: Boolean) : Status

    /** [change] can be taken back with undo. */
    public data class Changed(val change: Change) : Status

    /** Writing failed and nothing in the story changed. [fix] is what the notice offers. */
    public data class Failed(val again: Boolean, val error: DraftingError, val fix: Fix) : Status

    public enum class Change { Added, Rewritten, Stopped, EditSaved, BeatDeleted }

    public enum class Fix {
        TryAgain,

        /** No model is chosen, or no provider of it takes the settings: another model or tuning helps. */
        ChooseModel,
    }
}

/** The bar's left button. */
public sealed interface Secondary {
    /** Opens the direction; [replacing] when one is already given. */
    public data class Guide(val replacing: Boolean, val enabled: Boolean) : Secondary

    /** Closes the open composer. */
    public data object Cancel : Secondary
}

/** The bar's main button, and what it does. */
public sealed interface Primary {
    /** Continue, or Send when the player wrote a turn. The model writes either way. */
    public data class Advance(val sends: Boolean) : Primary

    /** Uses the open composer. Faded until there is something to use. */
    public data class Submit(val action: SubmitAction, val enabled: Boolean) : Primary

    /** Stops the model writing. */
    public data object Stop : Primary
}

public enum class SubmitAction {
    AddGuide,
    UpdateGuide,

    /** Writes the last beat again, for the reason given. The model writes. */
    Retry,
    Save,

    /** Saving an emptied beat removes it. */
    DeleteBeat,
}

/** The player's answer to being asked whether to leave while a model writes. */
public enum class LeaveAnswer {
    /** The writing goes on. */
    Stay,

    /** The writing stops, keeping nothing of it, and the story is left. */
    Leave,
}

/** Something the session decided that is not part of its state. */
public sealed interface PlayOutcome {
    /** The story was left. Everything in it, typed text included, is saved. */
    public data object Left : PlayOutcome

    /** Something the player did could not be kept. The state already shows the story as it was before. */
    public data class Failed(val failure: PlayFailure) : PlayOutcome

    /** A tap that cannot be followed now, said back so it never goes unanswered. */
    public data class Refused(val reason: Refusal) : PlayOutcome
}

public sealed interface PlayFailure {
    /** The story could not be written. Typed text stays on screen and is saved with the next change. */
    public data class Storage(val error: StorageError) : PlayFailure

    /** A rule failed in a way no rule should. The verb changed nothing; [cause] is kept for diagnosis. */
    public data class Unexpected(val cause: Exception) : PlayFailure
}

public enum class Refusal {
    /** A beat was tapped while a model writes. */
    StopFirst,

    /** Another beat was tapped while an edit with changes is open. */
    FinishEditFirst,
}
