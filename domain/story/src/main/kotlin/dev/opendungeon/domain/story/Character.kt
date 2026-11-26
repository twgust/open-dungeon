package dev.opendungeon.domain.story

/**
 * A person in the story. One shape serves cast cards, the composer form and the player's own character,
 * so every field is optional and screens show what is filled.
 */
public data class Character(
    val id: CharacterId,
    /** What they are to the story, e.g. "a disgraced smuggler" or "reliquary keeper". */
    val role: String? = null,
    val name: String? = null,
    val age: String? = null,
    val gender: String? = null,
    val appearance: String? = null,
    val personality: List<String> = emptyList(),
    val speech: String? = null,
    /** One line of their dialogue that shows how they sound. */
    val voiceLine: String? = null,
    val wants: String? = null,
    val flaw: String? = null,
    /** A behaviour with a trigger, e.g. "counts the coins twice when she is lying". */
    val habit: String? = null,
    val goodAt: String? = null,
    val secret: Secret = Secret.None,
) {
    public operator fun get(line: CharacterLine): String? = when (line) {
        CharacterLine.Role -> role
        CharacterLine.Name -> name
        CharacterLine.Age -> age
        CharacterLine.Gender -> gender
        CharacterLine.Appearance -> appearance
        CharacterLine.Personality -> personality.takeIf { it.isNotEmpty() }?.joinToString(", ")
        CharacterLine.Speech -> speech
        CharacterLine.VoiceLine -> voiceLine
        CharacterLine.Wants -> wants
        CharacterLine.Flaw -> flaw
        CharacterLine.Habit -> habit
        CharacterLine.GoodAt -> goodAt
    }

    /**
     * Returns a copy with [line] set to [text], kept exactly as typed so a field being edited reads back
     * what it holds; blank text clears the line. [CharacterLine.Personality] is the exception: it is a
     * list, split at commas, so it reads back normalised.
     */
    public fun with(line: CharacterLine, text: String?): Character {
        val value = text?.takeIf(String::isNotBlank)
        return when (line) {
            CharacterLine.Role -> copy(role = value)
            CharacterLine.Name -> copy(name = value)
            CharacterLine.Age -> copy(age = value)
            CharacterLine.Gender -> copy(gender = value)
            CharacterLine.Appearance -> copy(appearance = value)
            CharacterLine.Personality -> copy(
                personality = value?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty(),
            )
            CharacterLine.Speech -> copy(speech = value)
            CharacterLine.VoiceLine -> copy(voiceLine = value)
            CharacterLine.Wants -> copy(wants = value)
            CharacterLine.Flaw -> copy(flaw = value)
            CharacterLine.Habit -> copy(habit = value)
            CharacterLine.GoodAt -> copy(goodAt = value)
        }
    }
}

/** The editable text lines of a [Character]. The secret is deliberately not one of them. */
public enum class CharacterLine {
    Role,
    Name,
    Age,
    Gender,
    Appearance,
    Personality,
    Speech,
    VoiceLine,
    Wants,
    Flaw,
    Habit,
    GoodAt,
}

/** What a character hides. A [Sealed] secret is never shown to the player; only story prompts read it. */
public sealed interface Secret {
    public data object None : Secret

    public data class Known(val text: String) : Secret

    public data class Sealed(val text: String) : Secret
}
