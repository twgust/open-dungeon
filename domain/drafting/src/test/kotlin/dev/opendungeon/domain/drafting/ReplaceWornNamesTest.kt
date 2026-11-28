package dev.opendungeon.domain.drafting

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.DefaultSentenceBuilder
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.Secret
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReplaceWornNamesTest {

    private val draft = fullDraft.copy(
        genres = listOf(Phrase.Offered("Slice of life")),
        premise = Phrase.Offered("You inherit your aunt's failing café."),
    )

    private val kael = Character(
        id = CharacterId("p-1"),
        role = "barista",
        name = "Kael Voss",
        gender = "man",
        habit = "Kael hums when the milk burns",
        secret = Secret.Sealed("Voss is not his real name; Kael Voss owes the landlord"),
    )
    private val ida = Character(id = CharacterId("p-2"), role = "regular", name = "Ida", gender = "woman")
    private val elara = Character(id = CharacterId("p-3"), role = "baker", name = "Elara", gender = "woman")

    private fun renames(vararg names: Pair<Int, String>): Outcome<Any, GenerationError> =
        Outcome.Success(RenamesPayload(names.map { (person, name) -> RenamePayload(person, name) }))

    @Test
    fun `nobody with a fresh name costs a request`() = runTest {
        val generator = QueuedStructuredGenerator()

        val named = replaceWornNames(generator)(listOf(ida), draft, drafter)

        assertEquals(Outcome.Success(listOf(ida)), named)
        assertEquals(emptyList(), generator.requests)
    }

    @Test
    fun `a worn name is replaced, everywhere the person's lines use it, and nobody else changes`() = runTest {
        val generator = QueuedStructuredGenerator(renames(1 to "Tom Berg"))

        val named = assertIs<Outcome.Success<List<Character>>>(
            replaceWornNames(generator)(listOf(kael, ida), draft, drafter),
        ).value

        assertEquals(
            listOf(
                kael.copy(
                    name = "Tom Berg",
                    habit = "Tom hums when the milk burns",
                    secret = Secret.Sealed("Berg is not his real name; Tom Berg owes the landlord"),
                ),
                ida,
            ),
            named,
        )
        assertEquals(1, generator.requests.size)
    }

    @Test
    fun `the request goes to the drafting model, tuned, in the draft's session, with a described schema`() = runTest {
        val generator = QueuedStructuredGenerator(renames(1 to "Tom Berg"))

        replaceWornNames(generator)(listOf(kael), draft, drafter)

        val request = generator.requests.single()
        assertEquals(drafter.model, request.model)
        assertEquals(drafter.tuning, request.tuning)
        assertEquals("draft-7", request.sessionId)
        assertEquals(RenamesPayload.DESCRIPTIONS, request.descriptions)
        assertEquals(listOf(RenamesPayload.EXAMPLE), request.examples)
    }

    @Test
    fun `a worn or taken answer is turned down and asked for again`() = runTest {
        val taken = CastMember(Character(CharacterId("m"), name = "Lisa"), CastMember.Origin.Own, inStory = true)
        val generator = QueuedStructuredGenerator(
            renames(1 to "Lyra", 2 to "lisa"),
            renames(1 to "Sanna", 2 to "Petra"),
        )

        val named = assertIs<Outcome.Success<List<Character>>>(
            replaceWornNames(generator)(listOf(elara, kael), draft.copy(cast = listOf(taken)), drafter),
        ).value

        assertEquals(listOf("Sanna", "Petra"), named.map { it.name })
        val retry = generator.requests[1].messages.filterIsInstance<ChatMessage.User>().single().text
        assertTrue("Turned down, so never use: Elara, Kael Voss, Lyra\n" in retry, retry)
        assertTrue("Already in the story: Lisa" in retry, retry)
    }

    @Test
    fun `two people are never given the same name`() = runTest {
        val generator = QueuedStructuredGenerator(renames(1 to "Sanna", 2 to "Sanna"), renames(1 to "Petra"))

        val named = assertIs<Outcome.Success<List<Character>>>(
            replaceWornNames(generator)(listOf(elara, kael), draft, drafter),
        ).value

        assertEquals(listOf("Sanna", "Petra"), named.map { it.name })
        val retry = generator.requests[1].messages.filterIsInstance<ChatMessage.User>().single().text
        assertTrue("1. Kael Voss, barista" in retry, retry)
        assertTrue("Already in the story: Sanna" in retry, retry)
    }

    @Test
    fun `a model that keeps missing makes the names unusable`() = runTest {
        val generator = QueuedStructuredGenerator(renames(1 to "Lyra"), renames(1 to "Kaelith"))

        assertEquals(
            Outcome.Failure(DraftingError.Unusable),
            replaceWornNames(generator)(listOf(elara), draft, drafter),
        )
        assertEquals(ReplaceWornNames.ATTEMPTS, generator.requests.size)
    }

    @Test
    fun `an answer for someone not on the list is ignored`() = runTest {
        val generator = QueuedStructuredGenerator(renames(2 to "Sanna"), renames(1 to "Petra"))

        val named = assertIs<Outcome.Success<List<Character>>>(
            replaceWornNames(generator)(listOf(elara), draft, drafter),
        ).value

        assertEquals(listOf("Petra"), named.map { it.name })
    }

    @Test
    fun `a worn name the player wrote is theirs and stays`() = runTest {
        val generator = QueuedStructuredGenerator(renames(1 to "Tom Berg"))

        val named = assertIs<Outcome.Success<List<Character>>>(
            replaceWornNames(generator)(listOf(elara, kael), draft, drafter, playerWords = listOf("my aunt Elara")),
        ).value

        assertEquals(listOf("Elara", "Tom Berg"), named.map { it.name })
    }

    @Test
    fun `a failed request keeps its reason`() = runTest {
        val generator = QueuedStructuredGenerator(Outcome.Failure(GenerationError.Refused))

        assertEquals(
            Outcome.Failure(DraftingError.Generation(GenerationError.Refused)),
            replaceWornNames(generator)(listOf(kael), draft, drafter),
        )
    }

    @Test
    fun `a rename swaps whole words only, and keeps a title`() {
        val doctor = Character(
            id = CharacterId("d"),
            name = "Dr. Aris Thorne",
            habit = "Thorne taps the Thornefield file",
            wants = "To make Aris proud",
        )

        val renamed = doctor.renamed("Dr. Hanna Berg")

        assertEquals("Dr. Hanna Berg", renamed.name)
        assertEquals("Berg taps the Thornefield file", renamed.habit)
        assertEquals("To make Aris proud", renamed.wants, "only the first and last words follow the rename")
    }

    @Test
    fun `the prompt for new names`() {
        val prompt = RenamePrompt(StoryBrief(DefaultSentenceBuilder()), BundledNamingGuide())

        assertGolden(
            "rename",
            prompt.messages(
                draft,
                listOf(kael, elara),
                turnedDown = listOf("Kael Voss", "Elara"),
                taken = listOf("Ida"),
            )
                .transcript(),
        )
    }

    @Test
    fun `no secret reaches the prompt for new names`() {
        val prompt = RenamePrompt(StoryBrief(DefaultSentenceBuilder()), BundledNamingGuide())

        val text = prompt.messages(draft, listOf(kael), listOf("Kael Voss"), emptyList()).transcript()

        assertTrue("landlord" !in text)
    }
}
