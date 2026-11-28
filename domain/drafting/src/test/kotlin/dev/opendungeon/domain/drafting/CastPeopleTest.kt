package dev.opendungeon.domain.drafting

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.DefaultSentenceBuilder
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.Secret
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CastPeopleTest {

    private val draft = fullDraft.copy(premise = Phrase.Offered("You owe the salt-wardens a cargo you sank."))

    private fun person(name: String, secret: String = "Sold the map") = PersonPayload(
        role = " ferry pilot ",
        name = name,
        age = "",
        gender = "woman",
        appearance = "Tar on her sleeves.",
        personality = listOf("wry", " ", "loyal"),
        speech = "Clipped",
        voiceLine = "\"The river keeps no promises.\"",
        wants = "To leave town",
        flaw = "Owes everyone",
        habit = "Whistles when she lies",
        goodAt = "Reading water",
        secret = secret,
    )

    private fun cast(answer: Outcome<Any, GenerationError>) = ScriptedStructuredGenerator(answer).let { generator ->
        generator to DefaultCastPeople(
            generator,
            CountingIds("person"),
            replaceWornNames(generator),
            BundledWornNames(),
            BundledNamingGuide(),
            DefaultSentenceBuilder(),
        )
    }

    @Test
    fun `people come back as characters with fresh ids and sealed secrets`() = runTest {
        val (_, castPeople) = cast(Outcome.Success(CastPayload(listOf(person("Ottilie")))))

        val people = assertIs<Outcome.Success<List<Character>>>(castPeople(draft, drafter)).value

        assertEquals(
            Character(
                id = CharacterId("person-1"),
                role = "ferry pilot",
                name = "Ottilie",
                gender = "woman",
                appearance = "Tar on her sleeves.",
                personality = listOf("wry", "loyal"),
                speech = "Clipped",
                voiceLine = "The river keeps no promises.",
                wants = "To leave town",
                flaw = "Owes everyone",
                habit = "Whistles when she lies",
                goodAt = "Reading water",
                secret = Secret.Sealed("Sold the map"),
            ),
            people.single(),
        )
    }

    @Test
    fun `nameless, repeated and already cast people are left out, and no more than asked for come back`() = runTest {
        val alreadyIn =
            CastMember(Character(CharacterId("m"), name = "Maren"), CastMember.Origin.Offered, inStory = true)
        val (_, castPeople) = cast(
            Outcome.Success(
                CastPayload(
                    listOf(person(" "), person("maren"), person("Ida"), person("IDA"), person("Bo"), person("Cy")),
                ),
            ),
        )

        val people = assertIs<Outcome.Success<List<Character>>>(
            castPeople(draft.copy(cast = listOf(alreadyIn)), drafter, count = 2),
        ).value

        assertEquals(listOf("Ida", "Bo"), people.map { it.name })
    }

    private fun guarded(vararg answers: Outcome<Any, GenerationError>) = QueuedStructuredGenerator(*answers).let {
        it to DefaultCastPeople(
            it,
            CountingIds("person"),
            replaceWornNames(it),
            BundledWornNames(),
            BundledNamingGuide(),
            DefaultSentenceBuilder(),
        )
    }

    @Test
    fun `a worn name is replaced before anyone sees it`() = runTest {
        val (generator, castPeople) = guarded(
            Outcome.Success(CastPayload(listOf(person("Kael Voss"), person("Ida")))),
            Outcome.Success(RenamesPayload(listOf(RenamePayload(1, "Ottilie Brandt")))),
        )

        val people = assertIs<Outcome.Success<List<Character>>>(castPeople(draft, drafter)).value

        assertEquals(listOf("Ottilie Brandt", "Ida"), people.map { it.name })
        assertEquals(2, generator.requests.size)
    }

    @Test
    fun `someone whose worn name cannot be replaced is left out, and the rest are kept`() = runTest {
        val (_, castPeople) = guarded(
            Outcome.Success(CastPayload(listOf(person("Elara"), person("Ida")))),
            Outcome.Failure(GenerationError.Refused),
        )

        val people = assertIs<Outcome.Success<List<Character>>>(castPeople(draft, drafter)).value

        assertEquals(listOf("Ida"), people.map { it.name })
    }

    @Test
    fun `a cast of nothing but worn names that cannot be replaced fails with the reason`() = runTest {
        val (_, castPeople) = guarded(
            Outcome.Success(CastPayload(listOf(person("Elara")))),
            Outcome.Failure(GenerationError.Refused),
        )

        assertEquals(Outcome.Failure(DraftingError.Generation(GenerationError.Refused)), castPeople(draft, drafter))
    }

    @Test
    fun `a blank secret is no secret`() = runTest {
        val (_, castPeople) = cast(Outcome.Success(CastPayload(listOf(person("Ida", secret = "  ")))))

        val people = assertIs<Outcome.Success<List<Character>>>(castPeople(draft, drafter)).value

        assertEquals(Secret.None, people.single().secret)
    }

    @Test
    fun `an answer with nobody usable in it fails`() = runTest {
        val (_, castPeople) = cast(Outcome.Success(CastPayload(listOf(person("")))))

        assertEquals(Outcome.Failure(DraftingError.Unusable), castPeople(draft, drafter))
    }

    @Test
    fun `a failed request keeps its reason`() = runTest {
        val (_, castPeople) = cast(Outcome.Failure(GenerationError.Refused))

        assertEquals(Outcome.Failure(DraftingError.Generation(GenerationError.Refused)), castPeople(draft, drafter))
    }

    @Test
    fun `the request goes to the drafting model, tuned, in the draft's session, with a described schema`() = runTest {
        val (generator, castPeople) = cast(Outcome.Success(CastPayload(listOf(person("Ida")))))

        castPeople(draft, drafter)

        val request = generator.requests.single()
        assertEquals(drafter.model, request.model)
        assertEquals(drafter.tuning, request.tuning)
        assertEquals("draft-7", request.sessionId)
        assertEquals(CastPayload.DESCRIPTIONS, request.descriptions)
        assertEquals(listOf(CastPayload.EXAMPLE), request.examples)
    }

    @Test
    fun `every description names a field the schema has, and every field is described`() {
        val fields = listOf(CastPayload.serializer().descriptor, PersonPayload.serializer().descriptor)
            .flatMap { it.qualifiedNames() }

        assertEquals(fields.toSet(), CastPayload.DESCRIPTIONS.keys)
    }

    @Test
    fun `the example is a valid answer`() {
        assertTrue(CastPayload.EXAMPLE.people.all { it.name.isNotBlank() && it.secret.isNotBlank() })
    }

    @Test
    fun `the prompt for a first cast`() {
        val prompt = CastPrompt(StoryBrief(DefaultSentenceBuilder()), BundledNamingGuide())

        assertGolden("cast-first", prompt.messages(draft, count = 3).transcript())
    }

    @Test
    fun `the prompt for a recast names everyone already there`() {
        val prompt = CastPrompt(StoryBrief(DefaultSentenceBuilder()), BundledNamingGuide())
        val cast = listOf(
            CastMember(
                Character(
                    CharacterId("1"),
                    role = "reliquary keeper",
                    name = "Maren Kask",
                    secret = Secret.Sealed("x"),
                ),
                CastMember.Origin.Offered,
                inStory = true,
            ),
            CastMember(Character(CharacterId("2"), name = "Sister Ottilie"), CastMember.Origin.Own, inStory = false),
        )

        assertGolden("cast-recast", prompt.messages(draft.copy(cast = cast), count = 1).transcript())
    }

    private fun SerialDescriptor.qualifiedNames(): List<String> = elementNames.map { "$serialName.$it" }
}
