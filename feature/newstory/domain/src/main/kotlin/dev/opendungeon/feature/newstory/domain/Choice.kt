package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.ContentLimits

/** Something on the current step the player can tap. Screens pass back the choice an [Option] carries. */
public sealed interface Choice {
    /** A word pill: a genre, a twist, a role, a tone or a setting. */
    public data class Word(val text: String) : Choice

    public data class Desire(val level: ContentLimits.Desire) : Choice

    public data class Violence(val level: ContentLimits.Violence) : Choice

    /** Puts a cast member in the story, or takes them out. */
    public data class CastMember(val id: CharacterId) : Choice
}

/** A pill as the current step offers it. */
public data class Option(
    val choice: Choice,
    val text: String,
    val picked: Boolean,
    /** The player's own words rather than one the app offered. */
    val own: Boolean = false,
)
