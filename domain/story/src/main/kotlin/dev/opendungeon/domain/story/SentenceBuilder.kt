package dev.opendungeon.domain.story

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** Turns a story's choices into the sentences story creation shows and prompts read. */
public interface SentenceBuilder {
    /**
     * The story clause, "A gothic noir story, twisted by a heist, starring …", built from the steps
     * before [before] that have a value. Ends with [ending].
     */
    public fun story(choices: StoryChoices, before: CreationStep, ending: Ending): Sentence

    /** "Among them, Maren Kask and Sister Ottilie." or [Sentence.Empty] when nobody is in the story yet. */
    public fun cast(choices: StoryChoices): Sentence

    /**
     * The whole story in one sentence, as the opening shows it: the story clause, then who is in it.
     * "A gothic noir story, twisted by a heist, …, set in a salt town, among Maren Kask and Sister Ottilie."
     */
    public fun whole(choices: StoryChoices): Sentence

    public enum class Ending(public val text: String) { Period("."), None("") }
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultSentenceBuilder : SentenceBuilder {

    override fun story(choices: StoryChoices, before: CreationStep, ending: SentenceBuilder.Ending): Sentence {
        val segments = mutableListOf<Sentence.Segment>()
        segments += genreClause(choices.genres)

        fun clause(step: CreationStep, connector: String, phrase: Phrase?) {
            if (step < before && phrase != null) {
                segments += Sentence.Segment.Fixed(", $connector ")
                segments += phrase.toSegment()
            }
        }
        clause(CreationStep.Twist, "twisted by", choices.twist)
        if (CreationStep.Starring < before) {
            choices.protagonist?.let { you ->
                segments += Sentence.Segment.Fixed(", starring ")
                segments += Sentence.Segment.Chosen(you.role ?: you.name ?: YOU, own = false)
            }
        }
        clause(CreationStep.Tone, "told", choices.tone)
        clause(CreationStep.Setting, "set in", choices.setting)
        if (ending != SentenceBuilder.Ending.None) segments += Sentence.Segment.Fixed(ending.text)
        return Sentence(segments)
    }

    override fun cast(choices: StoryChoices): Sentence {
        val names = names(choices)
        if (names.isEmpty()) return Sentence.Empty
        return Sentence(listOf(Sentence.Segment.Fixed("Among them, ")) + names + Sentence.Segment.Fixed("."))
    }

    override fun whole(choices: StoryChoices): Sentence {
        val story = story(choices, before = CreationStep.Opening, ending = SentenceBuilder.Ending.None)
        val names = names(choices)
        val among = if (names.isEmpty()) emptyList() else listOf(Sentence.Segment.Fixed(", among ")) + names
        return story + Sentence(among + Sentence.Segment.Fixed("."))
    }

    /** "Maren Kask, Sister Ottilie and someone of your own", or nothing when nobody is in the story yet. */
    private fun names(choices: StoryChoices): List<Sentence.Segment> {
        val names = choices.castInStory.map { it.name ?: SOMEONE_OF_YOUR_OWN }
        return buildList {
            names.forEachIndexed { index, name ->
                when (index) {
                    0 -> Unit
                    names.lastIndex -> add(Sentence.Segment.Fixed(" and "))
                    else -> add(Sentence.Segment.Fixed(", "))
                }
                add(Sentence.Segment.Chosen(name, own = false))
            }
        }
    }

    private fun genreClause(genres: List<Phrase>): List<Sentence.Segment> {
        if (genres.isEmpty()) return listOf(Sentence.Segment.Fixed("A story"))
        val words = genres.map { genre -> genre.toSegment(lowercase = genre is Phrase.Offered) }
        val article = if (words.first().text.firstOrNull()?.lowercaseChar() in VOWELS) "An " else "A "
        return buildList {
            add(Sentence.Segment.Fixed(article))
            words.forEachIndexed { index, word ->
                if (index > 0) add(Sentence.Segment.Fixed(" "))
                add(word)
            }
            add(Sentence.Segment.Fixed(" story"))
        }
    }

    private fun Phrase.toSegment(lowercase: Boolean = false): Sentence.Segment.Chosen = Sentence.Segment.Chosen(
        text = if (lowercase) text.lowercase() else text,
        own = this is Phrase.Own,
    )

    private companion object {
        const val YOU = "you"
        const val SOMEONE_OF_YOUR_OWN = "someone of your own"
        val VOWELS = setOf('a', 'e', 'i', 'o', 'u')
    }
}
