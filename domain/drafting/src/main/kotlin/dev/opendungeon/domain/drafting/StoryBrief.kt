package dev.opendungeon.domain.drafting

import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.SentenceBuilder
import dev.opendungeon.domain.story.StoryChoices

/**
 * The story so far, as every drafting prompt describes it to the model: the sentence, the player's
 * character and the limits. Only what the player chose goes in, so a prompt never invents a fact the
 * draft does not hold.
 */
internal class StoryBrief(private val sentences: SentenceBuilder) {

    fun of(choices: StoryChoices, withPremise: Boolean): String = buildString {
        append("Story: ")
        append(sentences.story(choices, before = CreationStep.Premise, ending = SentenceBuilder.Ending.Period).text)
        choices.protagonist?.let { you ->
            append("\n\nThe player's character, addressed as \"you\":")
            YOU_LINES.forEach { line -> you[line]?.let { append("\n- ${line.label}: $it") } }
        }
        if (withPremise) choices.premise?.let { append("\n\nPremise: ${it.text}") }
        append("\n\nLimits:")
        LimitsInstructions.of(choices.limits).forEach { append("\n- $it") }
    }

    private companion object {
        /**
         * What the model may know of the player's character. Every line but the secret, which the player's
         * own card never has.
         */
        val YOU_LINES: List<CharacterLine> = CharacterLine.entries
    }
}

/** How a line is named in prompts. Plain words, since the model reads them as a character sheet. */
internal val CharacterLine.label: String
    get() = when (this) {
        CharacterLine.Role -> "Role"
        CharacterLine.Name -> "Name"
        CharacterLine.Age -> "Age"
        CharacterLine.Gender -> "Gender"
        CharacterLine.Appearance -> "Looks"
        CharacterLine.Personality -> "Personality"
        CharacterLine.Speech -> "Speech"
        CharacterLine.VoiceLine -> "Says"
        CharacterLine.Wants -> "Wants"
        CharacterLine.Flaw -> "Flaw"
        CharacterLine.Habit -> "Habit"
        CharacterLine.GoodAt -> "Good at"
    }

/** "Maren Kask, reliquary keeper", or whichever of the two is known. */
internal fun Character.tag(): String? = listOfNotNull(name, role).joinToString(", ").ifEmpty { null }

/**
 * Everything the player can see of a person, as story prompts list them. Their secret stays out: the
 * player reads what the model writes, and a secret in the prompt is one the prose can give away.
 */
internal fun Character.sheet(): String = buildString {
    append(tag() ?: SOMEONE)
    SHEET_LINES.forEach { line -> this@sheet[line]?.let { append("\n  - ${line.label}: $it") } }
}

private const val SOMEONE = "Someone"

/** A person's lines after their name and role, which [tag] already gives. */
private val SHEET_LINES: List<CharacterLine> = CharacterLine.entries - CharacterLine.Name - CharacterLine.Role
