package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.DraftId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Story creation. Callers state what the player did; the wizard decides whether it is allowed, what it
 * changes, and whether the step is done. Verbs that mean nothing on the current step are ignored.
 *
 * Verbs run one at a time in the order they were called, each against the draft the previous one left.
 * A verb shows its result at once; if it cannot be kept, the state goes back and [outcomes] says why.
 */
public interface StoryWizard {

    /** The current step, or `null` until [start] has opened a draft. */
    public val state: StateFlow<WizardState?>

    public val outcomes: Flow<WizardOutcome>

    /** Opens [draft], or else the open draft, or else a new one. Does nothing once a draft is open. */
    public fun start(draft: DraftId? = null)

    public fun select(choice: Choice)

    public fun submitOwn(text: String)

    public fun edit(edit: DraftEdit)

    public fun roll()

    public fun rollLine(line: CharacterLine)

    public fun next()

    public fun back()

    /** Makes a wizard whose work lives as long as [scope], typically a screen's. */
    public fun interface Factory {
        public fun create(scope: CoroutineScope): StoryWizard
    }
}
