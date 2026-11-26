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
}

public sealed interface WriterEvent {
    /** What is written so far, to show while writing. It is never saved. */
    public data class Preview(val draft: StoryDraft) : WriterEvent

    /** The finished draft, to keep. */
    public data class Done(val draft: StoryDraft) : WriterEvent

    public data class Failed(val error: DraftingError) : WriterEvent
}
