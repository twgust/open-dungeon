package dev.opendungeon.domain.story

/**
 * What the player chose for a story: the parts its sentence is built from and every prompt reads. A
 * draft holds them while creation runs; a story keeps them, fixed, once it begins.
 */
public interface StoryChoices {
    public val genres: List<Phrase>
    public val twist: Phrase?
    public val protagonist: Character?
    public val tone: Phrase?
    public val setting: Phrase?
    public val limits: ContentLimits
    public val premise: Phrase?

    /** The people in the story, in the order the player cast them. */
    public val castInStory: List<Character>
}
