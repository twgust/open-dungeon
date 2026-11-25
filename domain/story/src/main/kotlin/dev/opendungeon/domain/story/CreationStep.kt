package dev.opendungeon.domain.story

/** The steps of story creation, in the order the sentence builds. */
public enum class CreationStep {
    Genre, Twist, Starring, Tone, Setting, Limits, Premise, Cast, FirstLine, FirstTurn;

    public val previous: CreationStep? get() = entries.getOrNull(ordinal - 1)

    public val following: CreationStep? get() = entries.getOrNull(ordinal + 1)
}
