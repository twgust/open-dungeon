package dev.opendungeon.feature.newstory.domain.seeded

import app.cash.turbine.test
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.drafting.DraftingError
import dev.opendungeon.domain.drafting.Seed
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.CountingIds
import dev.opendungeon.feature.newstory.domain.FakeBeginStory
import dev.opendungeon.feature.newstory.domain.FakeDrafter
import dev.opendungeon.feature.newstory.domain.InMemoryDraftRepository
import dev.opendungeon.feature.newstory.domain.LeaveAnswer
import dev.opendungeon.feature.newstory.domain.ModelLine
import dev.opendungeon.feature.newstory.domain.ScriptedWriting
import dev.opendungeon.feature.newstory.domain.WizardFailure
import dev.opendungeon.feature.newstory.domain.WizardOutcome
import dev.opendungeon.feature.newstory.domain.policy.LimitsPolicy
import dev.opendungeon.feature.newstory.domain.storyteller
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSeededWizardTest {

    private class Rig(
        val wizard: SeededWizard,
        val work: ScriptedSeedWork,
        val opening: ScriptedWriting,
        val begin: FakeBeginStory,
        val drafter: FakeDrafter,
        val drafts: InMemoryDraftRepository,
    )

    private fun test(
        deck: SeedDeck = FixedDeck(listOf(caught)),
        model: Boolean = true,
        block: suspend TestScope.(Rig) -> Unit,
    ) = runTest(UnconfinedTestDispatcher()) {
        val work = ScriptedSeedWork()
        val opening = ScriptedWriting()
        val drafts = InMemoryDraftRepository()
        val begin = FakeBeginStory(drafts)
        val drafter = FakeDrafter(storyteller.takeIf { model })
        val wizard = DefaultSeededWizard(
            scope = backgroundScope,
            deck = deck,
            writeSeeds = work,
            fillBlank = work,
            castSeed = work,
            writeOpening = opening,
            beginStory = begin,
            limits = LimitsPolicy(),
            ids = CountingIds("seeded"),
            random = Random(SHUFFLE),
            drafter = drafter,
        )
        block(Rig(wizard, work, opening, begin, drafter, drafts))
    }

    private suspend fun SeededWizard.current(): SeededState = state.filterNotNull().first()

    private suspend fun SeededWizard.seedBody(): SeededBody.Seed = assertIs(current().body)

    /** The sentence as shown, each empty blank as "_". */
    private suspend fun SeededWizard.sentence(): String = seedBody().parts.joinToString("") { part ->
        when (part) {
            is SeedPart.Text -> part.text
            is SeedPart.Blank -> part.words ?: "_"
        }
    }

    private suspend fun SeededWizard.suggestions(): Suggestions = assertNotNull(seedBody().suggestions)

    private suspend fun SeededWizard.blank(key: String): SeedPart.Blank =
        seedBody().parts.filterIsInstance<SeedPart.Blank>().single { it.key == key }

    /** Starts, and fills every blank with its first suggestion. */
    private suspend fun SeededWizard.startFilled() {
        start()
        while (true) {
            val shown = seedBody().suggestions ?: break
            fill(shown.blank, shown.words.first())
        }
    }

    /** Starts, and walks to the limits with the player's words about the two people. */
    private suspend fun SeededWizard.toLimits(you: String = "", them: String = "") {
        startFilled()
        next()
        describe(Person.You, you)
        describe(Person.Them, them)
        next()
    }

    @Test
    fun `starting deals a seed on the first step, with nothing stored`() = test { rig ->
        rig.wizard.start()

        val state = rig.wizard.current()
        assertEquals(SeededStep.Seed, state.step)
        assertEquals(0.25f, state.progress)
        assertEquals("Caught lying by your _.", rig.wizard.sentence())
        assertEquals("Comedy", rig.wizard.seedBody().genre)
        assertTrue(state.controls.canRoll && !state.controls.canAdvance && !state.controls.canStop)
        assertEquals(null, state.writer, "nothing is writing, so no model is named")
        assertTrue(rig.drafts.drafts.value.isEmpty())
    }

    @Test
    fun `starting twice deals once`() = test(deck = FixedDeck(listOf(caught, knocking))) { rig ->
        rig.wizard.start()
        val first = rig.wizard.sentence()
        rig.wizard.start()

        assertEquals(first, rig.wizard.sentence())
    }

    @Test
    fun `an empty deck asks a model for the first seed`() = test(deck = FixedDeck(emptyList())) { rig ->
        rig.wizard.start()

        val call = rig.work.seedCalls.single()
        assertEquals(emptyList(), call.args)
        call.answer.complete(Outcome.Success(listOf(knocking)))

        assertEquals("The _ keeps knocking.", rig.wizard.sentence())
    }

    @Test
    fun `a deck that throws is reported, and starting again deals once it reads`() {
        val deck = FlakyDeck(listOf(caught))
        test(deck = deck) { rig ->
            rig.wizard.outcomes.test {
                rig.wizard.start()
                val failure = assertIs<WizardOutcome.Failed>(awaitItem()).failure
                assertIs<WizardFailure.Unexpected>(failure)
            }
            assertEquals(null, rig.wizard.state.value)

            rig.wizard.start()

            assertEquals("Caught lying by your _.", rig.wizard.sentence())
        }
    }

    /** A deck whose first read throws, as a bug in reading it would. */
    private class FlakyDeck(private val whole: List<Seed>) : SeedDeck {
        private var reads = 0
        override val seeds: List<Seed>
            get() = if (reads++ == 0) error("unreadable deck") else whole
    }

    @Test
    fun `a seed is dealt with its blanks empty, the first picked, and next waits for every one`() =
        test(deck = FixedDeck(listOf(twoBlanks))) { rig ->
            rig.wizard.start()

            assertEquals("_ got you caught by your _.", rig.wizard.sentence())
            assertEquals(
                listOf(
                    SeedPart.Blank("act", Seed.Kind.Person, words = null, picked = true),
                    SeedPart.Text(" got you caught by your "),
                    SeedPart.Blank("who", Seed.Kind.Person, words = null, picked = false),
                    SeedPart.Text("."),
                ),
                rig.wizard.seedBody().parts,
            )
            assertEquals(
                Suggestions("act", Seed.Kind.Person, listOf("lying", "forging a note"), null, false, true),
                rig.wizard.suggestions(),
            )
            val controls = rig.wizard.current().controls
            assertFalse(controls.canAdvance)
            assertTrue(controls.takesOwnWords)
            assertEquals(null, rig.wizard.current().writer, "nothing is written by a model")

            rig.wizard.next()
            assertEquals(SeededStep.Seed, rig.wizard.current().step)
        }

    @Test
    fun `filling a blank picks the next empty one, and the last opens the way on`() =
        test(deck = FixedDeck(listOf(twoBlanks))) { rig ->
            rig.wizard.start()

            rig.wizard.fill("act", "forging a note")
            assertEquals("Forging a note got you caught by your _.", rig.wizard.sentence())
            assertEquals("who", rig.wizard.suggestions().blank)

            rig.wizard.fill("who", "boss")
            assertEquals("Forging a note got you caught by your boss.", rig.wizard.sentence())
            assertNull(rig.wizard.seedBody().suggestions)
            assertTrue(rig.wizard.current().controls.canAdvance)
            assertFalse(rig.wizard.current().controls.takesOwnWords)
            assertTrue(rig.work.fillCalls.isEmpty())
        }

    @Test
    fun `picking a filled blank shows its words with the chosen one, and changing it keeps the rest`() =
        test(deck = FixedDeck(listOf(twoBlanks))) { rig ->
            rig.wizard.start()
            rig.wizard.fill("act", "lying")
            rig.wizard.fill("who", "boss")

            rig.wizard.pick("act")
            assertEquals("lying", rig.wizard.suggestions().chosen)
            assertTrue(rig.wizard.seedBody().parts.filterIsInstance<SeedPart.Blank>().first().picked)

            rig.wizard.fill("act", "forging a note")
            assertEquals("Forging a note got you caught by your boss.", rig.wizard.sentence())
            assertNull(rig.wizard.seedBody().suggestions)
        }

    @Test
    fun `the player's own words fill a blank, cleaned to fit it`() = test { rig ->
        rig.wizard.start()

        rig.wizard.fill("who", " {great} aunt\n")
        assertEquals("Caught lying by your great aunt.", rig.wizard.sentence())

        rig.wizard.pick("who")
        rig.wizard.fill("who", "   ")
        assertEquals("Caught lying by your great aunt.", rig.wizard.sentence(), "nothing to put in it")

        rig.wizard.fill("who", "x".repeat(Seed.MAX_FILL_LENGTH + 10))
        assertEquals(Seed.MAX_FILL_LENGTH, rig.wizard.blank("who").words?.length)
    }

    @Test
    fun `picking or filling a blank that is not there changes nothing`() = test { rig ->
        rig.wizard.start()
        rig.wizard.pick("where")
        rig.wizard.fill("where", "a pub")

        assertEquals("who", rig.wizard.suggestions().blank)
        assertEquals("Caught lying by your _.", rig.wizard.sentence())
    }

    @Test
    fun `asking for more words asks the model once, telling it every word offered, and adds the new ones`() =
        test { rig ->
            rig.wizard.start()
            rig.wizard.suggestMore("who")
            rig.wizard.suggestMore("who")

            val call = rig.work.fillCalls.single()
            assertEquals("who", call.args.second)
            val asked = assertNotNull(call.args.first.blank("who"))
            assertEquals(setOf("grandmother", "boss"), (asked.shown + asked.unused).toSet())
            assertTrue(rig.wizard.suggestions().pending)
            assertFalse(rig.wizard.suggestions().canSuggestMore)
            assertEquals(ModelLine("Storyteller", writing = true), rig.wizard.current().writer)
            assertTrue(rig.wizard.current().controls.canStop)
            assertFalse(rig.wizard.current().controls.canRoll)

            rig.wizard.fill("who", "boss")
            assertEquals("Caught lying by your boss.", rig.wizard.sentence(), "filling goes on while it writes")

            call.answer.complete(Outcome.Success(call.args.first.with(asked.withMore(listOf("landlord", "Boss")))))

            rig.wizard.pick("who")
            assertEquals(listOf("grandmother", "boss", "landlord"), rig.wizard.suggestions().words)
            assertFalse(rig.wizard.suggestions().pending)
            assertEquals("Caught lying by your boss.", rig.wizard.sentence(), "new words never fill a blank")
        }

    @Test
    fun `words that could not be written, or none new, leave the suggestions as they were and say why`() = test { rig ->
        rig.wizard.start()

        rig.wizard.outcomes.test {
            rig.wizard.suggestMore("who")
            rig.work.fillCalls.single().answer.complete(Outcome.Failure(DraftingError.Unusable))
            assertEquals(WizardOutcome.Failed(WizardFailure.Drafting(DraftingError.Unusable)), awaitItem())

            rig.wizard.suggestMore("who")
            val call = rig.work.fillCalls.last()
            call.answer.complete(Outcome.Success(call.args.first))
            assertEquals(WizardOutcome.Failed(WizardFailure.Drafting(DraftingError.Unusable)), awaitItem())
        }
        assertEquals(listOf("grandmother", "boss"), rig.wizard.suggestions().words)
        assertTrue(rig.wizard.suggestions().canSuggestMore)
    }

    @Test
    fun `with no model, asking for more words says to choose one`() = test(model = false) { rig ->
        rig.wizard.start()

        rig.wizard.outcomes.test {
            rig.wizard.suggestMore("who")
            assertEquals(WizardOutcome.Failed(WizardFailure.Drafting(DraftingError.NoModel)), awaitItem())
        }
        assertEquals(listOf("grandmother", "boss"), rig.wizard.suggestions().words)
    }

    @Test
    fun `stopping keeps the suggestions and drops a late answer`() = test { rig ->
        rig.wizard.start()
        rig.wizard.suggestMore("who")

        rig.wizard.stop()
        val call = rig.work.fillCalls.single()
        val asked = assertNotNull(call.args.first.blank("who"))
        call.answer.complete(Outcome.Success(call.args.first.with(asked.withMore(listOf("landlord")))))

        assertEquals(listOf("grandmother", "boss"), rig.wizard.suggestions().words)
        assertFalse(rig.wizard.current().controls.canStop)
    }

    @Test
    fun `the die deals every bundled seed once before a model writes more, avoiding all of them`() =
        test(deck = FixedDeck(listOf(caught, knocking))) { rig ->
            rig.wizard.start()
            rig.wizard.fill("who", "boss")
            val first = rig.wizard.sentence()
            rig.wizard.rollSeed()
            val second = rig.wizard.sentence()
            assertEquals(setOf("Caught lying by your boss.", "The _ keeps knocking."), setOf(first, second))
            assertFalse(rig.wizard.current().controls.canAdvance, "a new seed starts empty")

            rig.wizard.rollSeed()

            val call = rig.work.seedCalls.single()
            assertEquals(setOf(caught, knocking), call.args.toSet())
            assertTrue(rig.wizard.seedBody().replacing)
            assertFalse(rig.wizard.current().controls.canRoll)

            val written = seed("Your {who} sold the car.", "who" to listOf("aunt"))
            call.answer.complete(Outcome.Success(listOf(written)))

            assertEquals("Your _ sold the car.", rig.wizard.sentence())
            assertEquals(listOf("aunt"), rig.wizard.suggestions().words)
            assertFalse(rig.wizard.seedBody().replacing)
        }

    @Test
    fun `seeds that could not be written leave the seed on screen`() = test { rig ->
        rig.wizard.start()

        rig.wizard.outcomes.test {
            rig.wizard.rollSeed()
            rig.work.seedCalls.single().answer.complete(
                Outcome.Failure(DraftingError.Generation(GenerationError.Interrupted)),
            )
            assertIs<WizardOutcome.Failed>(awaitItem())
        }
        assertEquals("Caught lying by your _.", rig.wizard.sentence())
        assertTrue(rig.wizard.current().controls.canRoll)
    }

    @Test
    fun `moving on while a model writes stops it and keeps what is shown`() = test { rig ->
        rig.wizard.start()
        rig.wizard.fill("who", "boss")
        rig.wizard.suggestMore("who")

        rig.wizard.next()

        val people = assertIs<SeededBody.People>(rig.wizard.current().body)
        assertEquals("Caught lying by your boss.", people.premise)
        assertEquals("your boss", people.counterpart)
        assertTrue(rig.work.fillCalls.single().answer.isActive, "the answer is never waited for")
    }

    @Test
    fun `back from the seed leaves, and from later steps steps back`() = test { rig ->
        rig.wizard.toLimits()
        rig.wizard.back()
        assertEquals(SeededStep.People, rig.wizard.current().step)
        rig.wizard.back()
        assertEquals(SeededStep.Seed, rig.wizard.current().step)

        rig.wizard.outcomes.test {
            rig.wizard.back()
            assertEquals(WizardOutcome.Left, awaitItem())
        }
    }

    @Test
    fun `the player's words about the two people are kept as typed, up to a limit`() = test { rig ->
        rig.wizard.startFilled()
        rig.wizard.next()

        rig.wizard.describe(Person.You, " a nurse ")
        rig.wizard.describe(Person.Them, "x".repeat(DefaultSeededWizard.MAX_DESCRIPTION_LENGTH + 5))

        val people = assertIs<SeededBody.People>(rig.wizard.current().body)
        assertEquals(" a nurse ", people.you)
        assertEquals(DefaultSeededWizard.MAX_DESCRIPTION_LENGTH, people.them.length)
    }

    @Test
    fun `the limits are the custom wizard's, and Begin is the way forward`() = test { rig ->
        rig.wizard.toLimits()

        rig.wizard.select(Choice.Desire(ContentLimits.Desire.Unspoken))
        rig.wizard.submitOwn("  no spiders ")

        val state = rig.wizard.current()
        assertEquals(
            ContentLimits(ContentLimits.Desire.Unspoken, ContentLimits.Violence.Felt, note = "no spiders"),
            assertIs<SeededBody.Limits>(state.body).limits,
        )
        assertTrue(state.controls.begins && state.controls.canAdvance && state.controls.takesOwnWords)
        assertEquals(ModelLine("Storyteller", writing = false), state.writer)
        rig.wizard.next()
        assertEquals(SeededStep.Limits, rig.wizard.current().step, "next is not Begin")
    }

    @Test
    fun `verbs of other steps are ignored`() = test { rig ->
        rig.wizard.startFilled()
        rig.wizard.describe(Person.You, "a nurse")
        rig.wizard.select(Choice.Desire(ContentLimits.Desire.AllTheWay))
        rig.wizard.begin()
        rig.wizard.next()
        rig.wizard.pick("who")
        rig.wizard.fill("who", "boss")
        rig.wizard.suggestMore("who")
        rig.wizard.rollSeed()

        val people = assertIs<SeededBody.People>(rig.wizard.current().body)
        assertEquals("", people.you)
        assertEquals("Caught lying by your grandmother.", people.premise)
    }

    @Test
    fun `beginning with no model says to choose one and stays on the limits`() = test(model = false) { rig ->
        rig.wizard.toLimits()

        rig.wizard.outcomes.test {
            rig.wizard.begin()
            assertEquals(WizardOutcome.Failed(WizardFailure.Drafting(DraftingError.NoModel)), awaitItem())
        }
        assertEquals(SeededStep.Limits, rig.wizard.current().step)
    }

    @Test
    fun `beginning casts from the player's words, writes the opening, and begins the story`() = test { rig ->
        rig.wizard.toLimits(you = "  a nurse ", them = "   ")
        rig.wizard.select(Choice.Violence(ContentLimits.Violence.Offstage))

        rig.wizard.outcomes.test {
            rig.wizard.begin()

            val making = rig.wizard.current()
            assertEquals(SeededStep.Making, making.step)
            assertTrue(making.controls.canStop && !making.controls.canAdvance)
            assertEquals(ModelLine("Storyteller", writing = true), making.writer)
            val cast = rig.work.castCalls.single()
            assertEquals(ScriptedSeedWork.CastArgs(cast.args.draft, "your grandmother", "a nurse", null), cast.args)
            assertEquals(listOf(Phrase.Offered("Comedy")), cast.args.draft.genres)
            assertEquals(Phrase.Offered("wryly"), cast.args.draft.tone)
            assertEquals(Phrase.Offered("Caught lying by your grandmother."), cast.args.draft.premise)
            assertEquals(ContentLimits.Violence.Offstage, cast.args.draft.limits.violence)

            cast.answer.complete(Outcome.Success(twoPeople))
            val cast2 = assertIs<SeededBody.Making>(rig.wizard.current().body)
            assertEquals(SeededBody.Cast("Noor", "night-shift nurse"), cast2.you)
            assertEquals(SeededBody.Cast("Salma", "grandmother"), cast2.them)
            assertEquals("your grandmother", cast2.counterpart)

            val run = rig.opening.last
            assertEquals(noor, run.draft.protagonist)
            assertEquals(listOf(CastMember(salma, CastMember.Origin.Offered, inStory = true)), run.draft.cast)
            run.writing("The kitchen light")
            assertEquals("The kitchen light", assertIs<SeededBody.Making>(rig.wizard.current().body).opening)
            run.written("The kitchen light is on.")

            assertEquals(WizardOutcome.Begun(StoryId("story-1")), awaitItem())
        }
        val begun = rig.begin.begun.single()
        assertEquals("The kitchen light is on.", begun.opening)
        assertEquals(noor, begun.protagonist)
        assertEquals(listOf(salma), begun.castInStory)

        rig.wizard.back()
        rig.wizard.begin()
        assertEquals(1, rig.begin.begun.size, "a begun story closes the wizard")
    }

    @Test
    fun `the player's words about the other person make them the player's own`() = test { rig ->
        rig.wizard.toLimits(them = "kind but nosy")
        rig.wizard.begin()
        rig.work.castCalls.single().answer.complete(Outcome.Success(twoPeople))

        assertEquals(CastMember.Origin.Own, rig.opening.last.draft.cast.single().origin)
    }

    @Test
    fun `a cast that fails goes back to the limits, keeping every choice`() = test { rig ->
        rig.wizard.toLimits(you = "a nurse")

        rig.wizard.outcomes.test {
            rig.wizard.begin()
            rig.work.castCalls.single().answer.complete(Outcome.Failure(DraftingError.Unusable))
            assertEquals(WizardOutcome.Failed(WizardFailure.Drafting(DraftingError.Unusable)), awaitItem())
        }
        assertEquals(SeededStep.Limits, rig.wizard.current().step)
        rig.wizard.back()
        assertEquals("a nurse", assertIs<SeededBody.People>(rig.wizard.current().body).you)
    }

    @Test
    fun `after a failed opening, trying again keeps the people unless what they came from changed`() = test { rig ->
        rig.wizard.toLimits()
        rig.wizard.begin()
        rig.work.castCalls.single().answer.complete(Outcome.Success(twoPeople))
        rig.opening.last.failed(DraftingError.Unusable)
        assertEquals(SeededStep.Limits, rig.wizard.current().step)

        rig.wizard.begin()
        assertEquals(1, rig.work.castCalls.size, "the people still fit")
        assertEquals(2, rig.opening.runs.size)
        assertEquals(noor, rig.opening.last.draft.protagonist)
        assertEquals(
            SeededBody.Cast("Noor", "night-shift nurse"),
            assertIs<SeededBody.Making>(rig.wizard.current().body).you,
        )

        rig.wizard.stop()
        rig.wizard.back()
        rig.wizard.describe(Person.Them, "very tall")
        rig.wizard.next()
        rig.wizard.begin()
        assertEquals(2, rig.work.castCalls.size, "new words, new people")
    }

    @Test
    fun `back while the story is written asks first, staying goes on and leaving stops it`() = test { rig ->
        rig.wizard.toLimits()
        rig.wizard.begin()
        rig.work.castCalls.single().answer.complete(Outcome.Success(twoPeople))

        rig.wizard.back()
        assertTrue(rig.wizard.current().confirmingLeave)
        rig.wizard.answerLeave(LeaveAnswer.Stay)
        assertFalse(rig.wizard.current().confirmingLeave)
        assertEquals(SeededStep.Making, rig.wizard.current().step)

        rig.wizard.back()
        rig.wizard.answerLeave(LeaveAnswer.Leave)

        assertEquals(SeededStep.Limits, rig.wizard.current().step)
        assertTrue(rig.opening.last.cancelled)
        rig.opening.last.written("Too late.")
        assertTrue(rig.begin.begun.isEmpty())
    }

    @Test
    fun `a story that could not be stored goes back to the limits and says so`() = test { rig ->
        rig.begin.fails = 1
        rig.wizard.toLimits()

        rig.wizard.outcomes.test {
            rig.wizard.begin()
            rig.work.castCalls.single().answer.complete(Outcome.Success(twoPeople))
            rig.opening.last.written("It begins.")
            assertEquals(
                WizardOutcome.Failed(WizardFailure.Storage(StorageError.Full)),
                awaitItem(),
            )
        }
        assertEquals(SeededStep.Limits, rig.wizard.current().step)
    }

    @Test
    fun `the model line follows the chosen model`() = test(model = false) { rig ->
        rig.wizard.toLimits()
        assertEquals(ModelLine(model = null, writing = false), rig.wizard.current().writer)

        rig.drafter.model.value = storyteller

        assertEquals(ModelLine("Storyteller", writing = false), rig.wizard.current().writer)
    }

    private companion object {
        const val SHUFFLE = 7

        val twoBlanks = seed(
            "{act} got you caught by your {who}.",
            "act" to listOf("lying", "forging a note"),
            "who" to listOf("grandmother", "boss"),
        )
    }
}
