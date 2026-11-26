package dev.opendungeon.domain.drafting

/** Story text a model is writing, as it streams. A run ends with exactly one [Written] or [Failed]. */
public sealed interface WritingProgress {
    /** The text so far. */
    public data class Writing(val text: String) : WritingProgress

    /** The finished text. */
    public data class Written(val text: String) : WritingProgress

    public data class Failed(val error: DraftingError) : WritingProgress
}
