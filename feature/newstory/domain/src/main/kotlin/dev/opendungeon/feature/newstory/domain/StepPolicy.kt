package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.drafting.DraftingError
import dev.opendungeon.domain.drafting.DraftingModel
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.StoryDraft
import kotlinx.coroutines.flow.Flow

/**
 * Every rule of one creation step. The wizard routes the player's verbs to the policy of the current
 * step and persists what comes back; it decides nothing itself.
 *
 * A verb returns `null` when it means nothing on this step, and the draft stays as it is.
 */
public interface StepPolicy {
    public val step: CreationStep

    public fun body(draft: StoryDraft): StepBody

    /**
     * The body with the step's [writer] in it. Most steps show what is written so far as if it were
     * the draft; a step that tells the model's words apart from the player's overrides this.
     */
    public fun body(draft: StoryDraft, writer: WriterStatus): StepBody =
        body((writer as? WriterStatus.Writing)?.preview ?: draft)

    public fun canAdvance(draft: StoryDraft): Boolean

    public fun canRoll(draft: StoryDraft): Boolean = false

    /** A pick completes the step by itself, so the step needs no next arrow. */
    public val advancesOnPick: Boolean get() = false

    public fun takesOwnWords(draft: StoryDraft): Boolean = false

    public fun select(draft: StoryDraft, choice: Choice): Selection? = null

    public fun submitOwn(draft: StoryDraft, text: String): Selection? = null

    public fun edit(draft: StoryDraft, edit: DraftEdit): StoryDraft? = null

    /** The die, for steps whose die is local. A step with a [writer] rolls by writing instead. */
    public suspend fun roll(draft: StoryDraft): Selection? = null

    /** The die beside one line of a character card. */
    public suspend fun rollLine(draft: StoryDraft, line: CharacterLine): StoryDraft? = null

    /** How a model writes this step's content, or `null` when nothing on it is written by a model. */
    public val writer: StepWriter? get() = null
}

/** A changed draft, and whether the change also completes the step. */
public data class Selection(val draft: StoryDraft, val advance: Boolean)

/**
 * A step whose content a model writes, such as the premise or the cast. Writing takes seconds and can be
 * stopped, so the wizard runs it beside its verbs rather than as one, and keeps only what is finished.
 */
public interface StepWriter {
    /** Whether arriving on the step with [draft] starts writing by itself, because there is nothing to show yet. */
    public fun writesOnArrival(draft: StoryDraft): Boolean

    /**
     * A cold flow that writes the step's content into [draft] with [model]. It may show progress with
     * [WriterEvent.Preview] and ends with exactly one [WriterEvent.Done] or [WriterEvent.Failed].
     * Cancelling the collector cancels the request.
     */
    public fun write(draft: StoryDraft, model: DraftingModel): Flow<WriterEvent>

    /**
     * Stopping keeps what is written so far, as if the writing had ended there. Otherwise stopping
     * keeps what was there before it started. Leaving the step while it writes never keeps anything.
     */
    public val keepsWhenStopped: Boolean get() = false

    /** What the writing keeps can be taken back with [StoryWizard.undo], until the draft changes again. */
    public val undoable: Boolean get() = false

    /**
     * A failure shows on the step, which offers to try again, rather than as a passing notice. Either
     * way the draft stays as it was before the writing started.
     */
    public val failsInPlace: Boolean get() = false
}

/** The writer's part in what the current step shows, as the wizard hands it to the step's policy. */
public sealed interface WriterStatus {
    /** Nothing is writing, and the last writing left nothing to offer. */
    public data object Idle : WriterStatus

    /** A model is writing on [from]. [preview] is what it has so far, or `null` before its first words. */
    public data class Writing(val from: StoryDraft, val preview: StoryDraft?) : WriterStatus

    /** The last writing was kept and can still be taken back. [stopped] when the player cut it short. */
    public data class Kept(val stopped: Boolean) : WriterStatus

    /** The last writing failed. The draft is as it was before it started. */
    public data class Failed(val error: DraftingError) : WriterStatus
}

public sealed interface WriterEvent {
    /** What is written so far, to show while writing. It is never saved. */
    public data class Preview(val draft: StoryDraft) : WriterEvent

    /** The finished draft, to keep. */
    public data class Done(val draft: StoryDraft) : WriterEvent

    public data class Failed(val error: DraftingError) : WriterEvent
}
