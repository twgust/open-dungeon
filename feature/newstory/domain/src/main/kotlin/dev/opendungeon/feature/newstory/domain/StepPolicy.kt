package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.StoryDraft

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

    /** The die. Suspends because some steps roll by asking a model. */
    public suspend fun roll(draft: StoryDraft): Selection? = null

    /** The die beside one line of a character card. */
    public suspend fun rollLine(draft: StoryDraft, line: CharacterLine): StoryDraft? = null
}

/** A changed draft, and whether the change also completes the step. */
public data class Selection(val draft: StoryDraft, val advance: Boolean)
