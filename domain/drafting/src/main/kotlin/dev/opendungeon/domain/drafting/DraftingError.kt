package dev.opendungeon.domain.drafting

import dev.opendungeon.domain.generation.GenerationError

/** Why a model could not draft part of a story. Each case maps to something the UI can say or offer. */
public sealed interface DraftingError {
    /** No model is chosen, or the chosen one is no longer offered. Choosing one helps. */
    public data object NoModel : DraftingError

    /** The request itself failed. */
    public data class Generation(val error: GenerationError) : DraftingError

    /** The model answered, but nothing in the answer could be used, such as a blank premise or nameless people. */
    public data object Unusable : DraftingError
}
