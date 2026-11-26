package dev.opendungeon.domain.story

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** Turns a draft into the sentences story creation shows and prompts read. */
public interface SentenceBuilder {
    /**
     * The story clause, "A gothic noir story, twisted by a heist, starring …", built from the steps
     * before [before] that have a value. Ends with [ending].
     */
    public fun story(draft: StoryDraft, before: CreationStep, ending: Ending): Sentence

    /** "Among them, Maren Kask and Sister Ottilie." or [Sentence.Empty] when nobody is in the story yet. */
    public fun cast(draft: StoryDraft): Sentence

    public enum class Ending(public val text: String) { Comma(","), Period("."), None("") }
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultSentenceBuilder : SentenceBuilder {

    override fun story(draft: StoryDraft, before: CreationStep, ending: SentenceBuilder.Ending): Sentence {
        val segments = mutableListOf<Sentence.Segment>()
        segments += genreClause(draft.genres)

        fun clause(step: CreationStep, connector: String, phrase: Phrase?) {
            if (step < before && phrase != null) {
                segments += Sentence.Segment.Fixed(", $connector ")
                segments += phrase.toSegment()
            }
        }
        clause(CreationStep.Twist, "twisted by", draft.twist)
        if (CreationStep.Starring < before) {
            draft.protagonist?.let { you ->
                segments += Sentence.Segment.Fixed(", starring ")
                segments += Sentence.Segment.Chosen(you.role ?: you.name ?: YOU, own = false)
            }
        }
        clause(CreationStep.Tone, "told", draft.tone)
        clause(CreationStep.Setting, "set in", draft.setting)
        if (ending != SentenceBuilder.Ending.None) segments += Sentence.Segment.Fixed(ending.text)
        return Sentence(segments)
    }

    override fun cast(draft: StoryDraft): Sentence {
        val names = draft.castInStory.map { it.name ?: SOMEONE_OF_YOUR_OWN }
        if (names.isEmpty()) return Sentence.Empty
        val segments = mutableListOf<Sentence.Segment>(Sentence.Segment.Fixed("Among them, "))
        names.forEachIndexed { index, name ->
            when (index) {
                0 -> Unit
                names.lastIndex -> segments += Sentence.Segment.Fixed(" and ")
                else -> segments += Sentence.Segment.Fixed(", ")
            }
            segments += Sentence.Segment.Chosen(name, own = false)
        }
        segments += Sentence.Segment.Fixed(".")
        return Sentence(segments)
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
