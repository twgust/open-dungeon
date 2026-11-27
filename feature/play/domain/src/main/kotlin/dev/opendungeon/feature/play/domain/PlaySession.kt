package dev.opendungeon.feature.play.domain

import dev.opendungeon.domain.story.BeatId
import dev.opendungeon.domain.story.StoryId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Playing a story. Callers state what the player did; the session decides whether it is allowed, what it
 * changes, and what the screen offers next. Verbs that mean nothing in the current state are ignored, so
 * a double tap or a tap that lands late changes nothing.
 *
 * Verbs run one at a time in the order they were called, each against the story the previous one left.
 * A verb shows its result at once; if it cannot be kept, the state goes back and [outcomes] says why.
 */
@Suppress("TooManyFunctions") // One verb per thing the player can do; grouping them would only hide that.
public interface PlaySession {

    public val state: StateFlow<Play>

    public val outcomes: Flow<PlayOutcome>

    /** Opens [story]. Does nothing while a story is open; after a failed open, tries again. */
    public fun start(story: StoryId)

    /** The player's next turn, as typed. Ignored while a model writes. */
    public fun typeTurn(text: String)

    /** Continue: the model writes the next beat, after the player's turn when they wrote one. */
    public fun advance()

    /** The model writes the last beat again, when a model wrote it. */
    public fun retry()

    /** Opens the direction for the next beat, holding the one already given, if any. */
    public fun openGuide()

    /** Opens the reason for writing the last beat again. */
    public fun openReason()

    /** Opens [beat] for editing in place. */
    public fun editBeat(beat: BeatId)

    /** The open composer's text, as typed. */
    public fun typeNote(text: String)

    /** Uses the open composer: keeps the direction, writes the last beat again for the reason, or keeps the edit. */
    public fun submit()

    /** Closes the open composer, keeping nothing of it. */
    public fun cancel()

    /** Takes back the direction for the next beat. */
    public fun removeGuide()

    /** Stops the model writing. What it wrote so far is kept, and can be taken back. */
    public fun stop()

    /** Takes back the last change a model or an edit made, as long as nothing changed since. */
    public fun undo()

    /** Asks again for what last failed. */
    public fun tryAgain()

    /** Puts away the failure, leaving the story as it is. */
    public fun dismissFailure()

    /** Leaves the story. While a model writes, it asks first: [PlayState.confirmingLeave]. */
    public fun back()

    /** Answers [PlayState.confirmingLeave]: staying lets the writing go on, leaving stops it and leaves. */
    public fun answerLeave(answer: LeaveAnswer)

    /** Makes a session whose work lives as long as [scope], typically a screen's. */
    public fun interface Factory {
        public fun create(scope: CoroutineScope): PlaySession
    }
}
