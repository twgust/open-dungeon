package dev.opendungeon.domain.story

import dev.opendungeon.domain.story.SentenceBuilder.Ending
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SentenceBuilderTest {

    private val builder = DefaultSentenceBuilder()

    private val full = StoryDraft(
        id = DraftId("d"),
        genres = listOf(Phrase.Offered("Gothic"), Phrase.Offered("Noir")),
        twist = Phrase.Offered("a heist"),
        protagonist = Character(id = CharacterId("you"), role = "a disgraced smuggler"),
        tone = Phrase.Offered("tenderly"),
        setting = Phrase.Own("a lighthouse on stilts"),
    )

    @Test
    fun `builds only the clauses before the current step`() {
        assertEquals("A gothic noir story,", builder.story(full, before = CreationStep.Twist, Ending.Comma).text)
        assertEquals(
            "A gothic noir story, twisted by a heist, starring a disgraced smuggler,",
            builder.story(full, before = CreationStep.Tone, Ending.Comma).text,
        )
        assertEquals(
            "A gothic noir story, twisted by a heist, starring a disgraced smuggler, told tenderly, " +
                "set in a lighthouse on stilts.",
            builder.story(full, before = CreationStep.Limits, Ending.Period).text,
        )
    }

    @Test
    fun `own words keep their case and are marked`() {
        val sentence = builder.story(full, before = CreationStep.Limits, Ending.None)
        val own = sentence.segments.filterIsInstance<Sentence.Segment.Chosen>().filter { it.own }
        assertEquals(listOf("a lighthouse on stilts"), own.map { it.text })
    }

    @Test
    fun `article follows the first genre`() {
        val draft = StoryDraft(id = DraftId("d"), genres = listOf(Phrase.Offered("Occult"), Phrase.Offered("Western")))
        assertEquals("An occult western story", builder.story(draft, CreationStep.Twist, Ending.None).text)
    }

    @Test
    fun `a nameless own protagonist reads as you`() {
        val draft = full.copy(protagonist = Character(id = CharacterId("you")))
        assertTrue("starring you" in builder.story(draft, CreationStep.Tone, Ending.None).text)
    }

    @Test
    fun `cast line lists only the people in the story`() {
        fun member(name: String?, inStory: Boolean) =
            CastMember(Character(id = CharacterId(name ?: "anon"), name = name), CastMember.Origin.Offered, inStory)
        val draft = full.copy(
            cast = listOf(member("Maren Kask", true), member("Ilse Brandt", false), member("Sister Ottilie", true), member(null, true)),
        )
        assertEquals(
            "Among them, Maren Kask, Sister Ottilie and someone of your own.",
            builder.cast(draft).text,
        )
        assertTrue(builder.cast(full).isEmpty())
    }
}
