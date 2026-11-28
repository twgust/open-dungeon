package dev.opendungeon.domain.drafting

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.DefaultSentenceBuilder
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.Register
import dev.opendungeon.domain.story.Secret
import dev.opendungeon.domain.story.StoryDraft
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Writing seeds, more words for a blank, and a seeded story's two people. */
class SeedWritingTest {

    private val seed = assertNotNull(SeedsPayload.EXAMPLE.seeds.single().toSeed())

    private fun seedPayload(template: String, key: String = "who") = SeedPayload(
        genre = " Horror ",
        tone = "with dread",
        template = template,
        counterpart = "the {$key}",
        shelf = ShelfPayload.Dark,
        blanks = listOf(BlankPayload(key, BlankKindPayload.Person, listOf("child", "postman"))),
    )

    @Test
    fun `written seeds come back whole, leaving out broken ones and ones already seen`() = runTest {
        val generator = ScriptedStructuredGenerator(
            Outcome.Success(
                SeedsPayload(
                    listOf(
                        seedPayload("The {who} keeps knocking."),
                        seedPayload("Caught {act} by your {who}."),
                        seedPayload("Nobody comes.", key = "who"),
                        seedPayload("The {who} is at the window."),
                        seedPayload("The {who} is at the window."),
                    ),
                ),
            ),
        )

        val seeds = assertIs<Outcome.Success<List<Seed>>>(
            DefaultWriteSeeds(generator)(drafter, shelf = null, avoiding = listOf(seed), count = 4),
        ).value

        assertEquals(listOf("The {who} keeps knocking.", "The {who} is at the window."), seeds.map { it.template })
        assertEquals("Horror", seeds.first().genre)
        assertEquals(listOf("postman"), seeds.first().blank("who")?.unused)
        assertEquals(Register.Dark, seeds.first().shelf)
    }

    @Test
    fun `seeds asked for a shelf are on it, whatever the model called them`() = runTest {
        val generator =
            ScriptedStructuredGenerator(Outcome.Success(SeedsPayload(listOf(seedPayload("The {who} waits.")))))

        val seeds = assertIs<Outcome.Success<List<Seed>>>(
            DefaultWriteSeeds(generator)(drafter, shelf = Register.Everyday, avoiding = emptyList()),
        ).value

        assertEquals(listOf(Register.Everyday), seeds.map { it.shelf })
    }

    @Test
    fun `mixed seeds never land on an opt-in shelf`() = runTest {
        val adult = seedPayload("The {who} undresses.").copy(shelf = ShelfPayload.AfterDark)
        val generator =
            ScriptedStructuredGenerator(Outcome.Success(SeedsPayload(listOf(adult, seedPayload("The {who} waits.")))))

        val seeds = assertIs<Outcome.Success<List<Seed>>>(
            DefaultWriteSeeds(generator)(drafter, shelf = null, avoiding = emptyList()),
        ).value

        assertEquals(listOf("The {who} waits."), seeds.map { it.template })
    }

    @Test
    fun `seeds the player has seen are not written again`() = runTest {
        val generator = ScriptedStructuredGenerator(
            Outcome.Success(
                SeedsPayload(listOf(seedPayload("caught {act} by your {who}.").copy(template = seed.template))),
            ),
        )

        assertEquals(
            Outcome.Failure(DraftingError.Unusable),
            DefaultWriteSeeds(generator)(drafter, shelf = null, avoiding = listOf(seed)),
        )
    }

    @Test
    fun `a failed request for seeds keeps its reason`() = runTest {
        val generator = ScriptedStructuredGenerator(Outcome.Failure(GenerationError.Interrupted))

        assertEquals(
            Outcome.Failure(DraftingError.Generation(GenerationError.Interrupted)),
            DefaultWriteSeeds(generator)(drafter, shelf = null, avoiding = emptyList()),
        )
    }

    @Test
    fun `a seeds request goes to the drafting model, tuned, with a described schema and an example`() = runTest {
        val generator =
            ScriptedStructuredGenerator(Outcome.Success(SeedsPayload(listOf(seedPayload("The {who} waits.")))))

        DefaultWriteSeeds(generator)(drafter, shelf = null, avoiding = emptyList())

        val request = generator.requests.single()
        assertEquals(drafter.model, request.model)
        assertEquals(drafter.tuning, request.tuning)
        assertEquals(SeedsPayload.DESCRIPTIONS, request.descriptions)
        assertEquals(listOf(SeedsPayload.EXAMPLE), request.examples)
    }

    @Test
    fun `new words for a blank wait behind the ones it has, leaving out repeats`() = runTest {
        val generator =
            ScriptedStructuredGenerator(Outcome.Success(FillsPayload(listOf("Boss", "a landlord", "a landlord"))))

        val filled = assertIs<Outcome.Success<Seed>>(DefaultFillBlank(generator)(seed, "who", drafter)).value

        assertEquals(seed.blank("who")?.unused.orEmpty() + "a landlord", filled.blank("who")?.unused)
        assertEquals(seed.blank("act"), filled.blank("act"))
    }

    @Test
    fun `a blank the model wrote nothing new for fails`() = runTest {
        val generator = ScriptedStructuredGenerator(Outcome.Success(FillsPayload(listOf("grandmother", " "))))

        assertEquals(Outcome.Failure(DraftingError.Unusable), DefaultFillBlank(generator)(seed, "who", drafter))
    }

    @Test
    fun `a failed request for words keeps its reason`() = runTest {
        val generator = ScriptedStructuredGenerator(Outcome.Failure(GenerationError.NoProvider))

        assertEquals(
            Outcome.Failure(DraftingError.Generation(GenerationError.NoProvider)),
            DefaultFillBlank(generator)(seed, "who", drafter),
        )
    }

    private val seededDraft = StoryDraft(
        id = DraftId("seeded-1"),
        genres = listOf(Phrase.Offered(seed.genre)),
        tone = Phrase.Offered(seed.tone),
        premise = Phrase.Offered(seed.text),
        limits = fullDraft.limits,
    )

    private fun castSeed(answer: Outcome<Any, GenerationError>) = ScriptedStructuredGenerator(answer).let {
        it to
            DefaultCastSeed(
                it,
                CountingIds("person"),
                replaceWornNames(it),
                BundledNamingGuide(),
                DefaultSentenceBuilder(),
            )
    }

    @Test
    fun `the two people come back with fresh ids, and only the other person keeps a secret`() = runTest {
        val you = SeedCastPayload.EXAMPLE.you.copy(secret = "I sold the house")
        val (_, cast) = castSeed(Outcome.Success(SeedCastPayload.EXAMPLE.copy(you = you)))

        val people = assertIs<Outcome.Success<SeedCast>>(
            cast(seededDraft, "your grandmother", you = "a nurse", them = null, model = drafter),
        ).value

        assertEquals(CharacterId("person-1"), people.you.id)
        assertEquals("Noor Haddad", people.you.name)
        assertEquals(Secret.None, people.you.secret)
        assertEquals(CharacterId("person-2"), people.them.id)
        assertEquals(Secret.Sealed("She forged the relic herself, years ago"), people.them.secret)
    }

    @Test
    fun `a worn name is replaced, unless the player wrote it`() = runTest {
        val them = SeedCastPayload.EXAMPLE.them.copy(name = "Lyra Vance")
        val you = SeedCastPayload.EXAMPLE.you.copy(name = "Elara")
        val generator = QueuedStructuredGenerator(
            Outcome.Success(SeedCastPayload(you = you, them = them)),
            Outcome.Success(RenamesPayload(listOf(RenamePayload(1, "Agnes Pole")))),
        )
        val cast = DefaultCastSeed(
            generator,
            CountingIds("person"),
            replaceWornNames(generator),
            BundledNamingGuide(),
            DefaultSentenceBuilder(),
        )

        val people = assertIs<Outcome.Success<SeedCast>>(
            cast(seededDraft, "your grandmother", you = "a nurse called Elara", them = null, model = drafter),
        ).value

        assertEquals("Elara", people.you.name)
        assertEquals("Agnes Pole", people.them.name)
    }

    @Test
    fun `a worn name that cannot be replaced makes the cast unusable`() = runTest {
        val them = SeedCastPayload.EXAMPLE.them.copy(name = "Kael")
        val generator = QueuedStructuredGenerator(
            Outcome.Success(SeedCastPayload.EXAMPLE.copy(them = them)),
            Outcome.Success(RenamesPayload(listOf(RenamePayload(1, "Kaelen")))),
            Outcome.Success(RenamesPayload(listOf(RenamePayload(1, "Caelum")))),
        )
        val cast = DefaultCastSeed(
            generator,
            CountingIds("person"),
            replaceWornNames(generator),
            BundledNamingGuide(),
            DefaultSentenceBuilder(),
        )

        assertEquals(
            Outcome.Failure(DraftingError.Unusable),
            cast(seededDraft, "your grandmother", you = null, them = null, model = drafter),
        )
    }

    @Test
    fun `a nameless person makes the cast unusable`() = runTest {
        val them = SeedCastPayload.EXAMPLE.them.copy(name = " ")
        val (_, cast) = castSeed(Outcome.Success(SeedCastPayload.EXAMPLE.copy(them = them)))

        assertEquals(
            Outcome.Failure(DraftingError.Unusable),
            cast(seededDraft, "your grandmother", you = null, them = null, model = drafter),
        )
    }

    @Test
    fun `the cast request goes in the draft's session`() = runTest {
        val (generator, cast) = castSeed(Outcome.Success(SeedCastPayload.EXAMPLE))

        cast(seededDraft, "your grandmother", you = null, them = null, model = drafter)

        val request = generator.requests.single()
        assertEquals("seeded-1", request.sessionId)
        assertEquals(SeedCastPayload.DESCRIPTIONS, request.descriptions)
    }

    @Test
    fun `every description names a field its schema has, and every field is described`() {
        fun SerialDescriptor.names(): List<String> = elementNames.map { "$serialName.$it" }

        assertEquals(
            listOf(SeedsPayload.serializer(), SeedPayload.serializer(), BlankPayload.serializer())
                .flatMap { it.descriptor.names() }.toSet(),
            SeedsPayload.DESCRIPTIONS.keys,
        )
        assertEquals(FillsPayload.serializer().descriptor.names().toSet(), FillsPayload.DESCRIPTIONS.keys)
        assertEquals(
            (SeedCastPayload.serializer().descriptor.names() + PersonPayload.serializer().descriptor.names()).toSet() +
                "Cast.people",
            SeedCastPayload.DESCRIPTIONS.keys,
        )
    }

    @Test
    fun `the examples are valid answers`() {
        assertNotNull(SeedsPayload.EXAMPLE.seeds.single().toSeed())
        assertNull(SeedsPayload.EXAMPLE.seeds.single().copy(template = "No blanks.").toSeed())
        assertEquals("", SeedCastPayload.EXAMPLE.you.secret)
    }

    @Test
    fun `the prompt for new seeds`() {
        assertGolden("seeds", SeedsPrompt.messages(shelf = null, avoiding = listOf(seed), count = 4).transcript())
        assertGolden(
            "seeds-after-dark",
            SeedsPrompt.messages(Register.AfterDark, avoiding = emptyList(), count = 4).transcript(),
        )
    }

    @Test
    fun `the prompt for more words in a blank`() {
        val seen = seed.with(assertNotNull(seed.blank("who")?.next()))
        assertGolden("seed-fills", FillsPrompt.messages(seen, assertNotNull(seen.blank("who"))).transcript())
    }

    @Test
    fun `the prompt for a seeded cast, with the player's words and without`() {
        val prompt = SeedCastPrompt(StoryBrief(DefaultSentenceBuilder()), BundledNamingGuide())

        assertGolden(
            "seed-cast",
            prompt.messages(seededDraft, "your grandmother", you = "a nurse on her third night shift", them = null)
                .transcript(),
        )
    }
}
