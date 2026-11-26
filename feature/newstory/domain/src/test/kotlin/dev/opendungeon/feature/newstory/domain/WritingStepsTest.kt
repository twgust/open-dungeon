package dev.opendungeon.feature.newstory.domain

import app.cash.turbine.test
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.drafting.DraftingError
import dev.opendungeon.domain.drafting.DraftingModel
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.DefaultSentenceBuilder
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.Secret
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.feature.newstory.domain.policy.CastPolicy
import dev.opendungeon.feature.newstory.domain.policy.FirstLinePolicy
import dev.opendungeon.feature.newstory.domain.policy.FirstTurnPolicy
import dev.opendungeon.feature.newstory.domain.policy.GenrePolicy
import dev.opendungeon.feature.newstory.domain.policy.LimitsPolicy
import dev.opendungeon.feature.newstory.domain.policy.PremisePolicy
import dev.opendungeon.feature.newstory.domain.policy.SettingPolicy
import dev.opendungeon.feature.newstory.domain.policy.StarringPolicy
import dev.opendungeon.feature.newstory.domain.policy.TonePolicy
import dev.opendungeon.feature.newstory.domain.policy.TwistPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The steps a model writes: the premise, streamed, and the cast, in one answer. */
@OptIn(ExperimentalCoroutinesApi::class)
class WritingStepsTest {

    private val premise = ScriptedPremise()
    private val cast = ScriptedCast()
    private val drafter = FakeDrafter(storyteller)

    private val beforePremise = StoryDraft(
        DraftId("d"),
        step = CreationStep.Limits,
        genres = listOf(Phrase.Offered("Gothic")),
    )

    private fun policies(premisePolicy: StepPolicy = PremisePolicy(premise)): Set<StepPolicy> = setOf(
        GenrePolicy(TestVocabulary, FirstWordsRoll()),
        TwistPolicy(TestVocabulary, FirstWordsRoll()),
        StarringPolicy(TestVocabulary, FirstWordsRoll(), CountingIds("you")),
        TonePolicy(TestVocabulary, FirstWordsRoll()),
        SettingPolicy(TestVocabulary, FirstWordsRoll()),
        LimitsPolicy(),
        premisePolicy,
        CastPolicy(cast),
        FirstLinePolicy(),
        FirstTurnPolicy(),
    )

    private fun wizard(
        scope: CoroutineScope,
        repository: InMemoryDraftRepository,
        policies: Set<StepPolicy> = policies(),
    ): StoryWizard = DefaultStoryWizard(
        scope,
        repository,
        policies,
        DefaultSentenceBuilder(),
        CountingIds("person"),
        drafter,
    )

    private suspend fun StoryWizard.current(): WizardState = state.filterNotNull().first()

    private fun test(
        draft: StoryDraft = beforePremise,
        block: suspend TestScope.(StoryWizard, InMemoryDraftRepository) -> Unit,
    ) = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryDraftRepository(draft)
        val wizard = wizard(backgroundScope, repository)
        wizard.start()
        block(wizard, repository)
    }

    @Test
    fun `arriving on the premise writes it, showing it as it comes, and keeps it once written`() = test {
        wizard,
        repository,
        ->
        wizard.next()

        val run = premise.runs.single()
        assertEquals(storyteller, run.model)
        assertEquals(ModelLine(model = "Storyteller", writing = true), wizard.current().writer)

        run.writing("You owe")
        assertEquals(StepBody.Premise("You owe", own = false), wizard.current().body)
        assertNull(repository.stored.premise, "what is being written is never saved")

        run.written("You owe the salt.")
        assertEquals(Phrase.Offered("You owe the salt."), repository.stored.premise)
        assertEquals(ModelLine(model = "Storyteller", writing = false), wizard.current().writer)
        assertTrue(wizard.current().controls.canAdvance)
    }

    @Test
    fun `while a model writes, every control but stop and back waits`() = test { wizard, repository ->
        wizard.next()
        premise.last.writing("You owe")
        val saves = repository.saves

        wizard.edit(DraftEdit.Premise("my words"))
        wizard.submitOwn("my words")
        wizard.roll()
        wizard.next()

        val controls = wizard.current().controls
        assertFalse(controls.canAdvance || controls.canRoll || controls.takesOwnWords)
        assertEquals(StepBody.Premise("You owe", own = false), wizard.current().body)
        assertEquals(1, premise.runs.size, "the die does not start a second run")
        assertEquals(saves, repository.saves)
    }

    @Test
    fun `stopping keeps what was there before, and the stopped run's words never land`() =
        test(beforePremise.copy(step = CreationStep.Premise, premise = Phrase.Own("A bell rings."))) {
            wizard,
            repository,
            ->
            wizard.roll()
            val run = premise.last
            assertEquals("A bell rings.", run.draft.premise?.text, "the writer sees the premise to move away from")
            run.writing("You owe")

            wizard.stop()

            assertTrue(run.cancelled)
            assertEquals(StepBody.Premise("A bell rings.", own = true), wizard.current().body)
            assertEquals(ModelLine(model = "Storyteller", writing = false), wizard.current().writer)
            run.written("You owe the salt.")
            assertEquals(Phrase.Own("A bell rings."), repository.stored.premise)
        }

    @Test
    fun `back while writing asks first, and staying lets the writing go on`() = test { wizard, _ ->
        wizard.next()
        premise.last.writing("You owe")

        wizard.back()

        assertTrue(wizard.current().confirmingLeave)
        assertEquals(CreationStep.Premise, wizard.current().step)
        assertFalse(premise.last.cancelled)

        wizard.stay()

        assertFalse(wizard.current().confirmingLeave)
        premise.last.written("You owe the salt.")
        assertEquals(StepBody.Premise("You owe the salt.", own = false), wizard.current().body)
    }

    @Test
    fun `leaving after all stops the writing and steps back, with nothing of it saved`() = test { wizard, repository ->
        wizard.next()
        premise.last.writing("You owe")
        wizard.back()

        wizard.confirmLeave()

        assertTrue(premise.last.cancelled)
        assertEquals(CreationStep.Limits, wizard.current().step)
        assertFalse(wizard.current().confirmingLeave)
        assertNull(repository.stored.premise)
        assertEquals(CreationStep.Limits, repository.stored.step)
    }

    @Test
    fun `writing that finishes while the player is asked needs no answer`() = test { wizard, _ ->
        wizard.next()
        wizard.back()

        premise.last.written("You owe the salt.")

        assertFalse(wizard.current().confirmingLeave)
        wizard.confirmLeave()
        assertEquals(CreationStep.Premise, wizard.current().step, "a late answer to a closed question does nothing")
    }

    @Test
    fun `a model that cannot write leaves the step as it was and says why`() = test { wizard, repository ->
        wizard.next()
        premise.last.writing("You owe")

        wizard.outcomes.test {
            val error = DraftingError.Generation(GenerationError.Refused)
            premise.last.failed(error)
            assertEquals(WizardOutcome.Failed(WizardFailure.Drafting(error)), awaitItem())
        }
        assertEquals(StepBody.Premise("", own = false), wizard.current().body)
        assertTrue(wizard.current().controls.canRoll, "the die can try again")
        assertNull(repository.stored.premise)
    }

    @Test
    fun `a written result that cannot be saved is taken back and reported`() = test { wizard, repository ->
        wizard.next()

        wizard.outcomes.test {
            repository.failSaves = 1
            premise.last.written("You owe the salt.")
            assertIs<WizardFailure.Storage>(assertIs<WizardOutcome.Failed>(awaitItem()).failure)
        }
        assertEquals(StepBody.Premise("", own = false), wizard.current().body)
    }

    @Test
    fun `with no model nothing writes, the die says so, and choosing one writes`() =
        runTest(UnconfinedTestDispatcher()) {
            drafter.model.value = null
            val wizard = wizard(backgroundScope, InMemoryDraftRepository(beforePremise))
            wizard.start()
            wizard.next()

            assertTrue(premise.runs.isEmpty())
            assertEquals(ModelLine(model = null, writing = false), wizard.current().writer)
            wizard.outcomes.test {
                wizard.roll()
                assertEquals(WizardOutcome.Failed(WizardFailure.Drafting(DraftingError.NoModel)), awaitItem())
            }

            drafter.model.value = storyteller

            assertEquals(1, premise.runs.size, "the empty step writes once a model can")
            assertEquals(ModelLine(model = "Storyteller", writing = true), wizard.current().writer)
        }

    @Test
    fun `a step with something to show does not write by itself`() =
        test(beforePremise.copy(premise = Phrase.Own("A bell rings."))) { wizard, _ ->
            wizard.next()

            assertTrue(premise.runs.isEmpty())
            assertNull(wizard.current().writer?.takeIf { it.writing })
        }

    @Test
    fun `steps no model writes have no model line`() = test { wizard, _ ->
        assertNull(wizard.current().writer)
    }

    @Test
    fun `a resumed empty step writes on opening`() = runTest(UnconfinedTestDispatcher()) {
        val wizard = wizard(backgroundScope, InMemoryDraftRepository(beforePremise.copy(step = CreationStep.Premise)))

        wizard.start()

        assertEquals(1, premise.runs.size)
    }

    @Test
    fun `leaving the screen mid-writing keeps the draft as it was before`() = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryDraftRepository(beforePremise)
        val screen = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val wizard = wizard(screen, repository)
        wizard.start()
        wizard.next()
        premise.last.writing("You owe")

        screen.cancel()

        assertTrue(premise.last.cancelled)
        assertNull(repository.stored.premise)
        assertEquals(CreationStep.Premise, repository.stored.step)
    }

    @Test
    fun `a writer that throws is reported, and the step works again`() = runTest(UnconfinedTestDispatcher()) {
        val real = PremisePolicy(premise)
        val broken = object : StepPolicy by real, StepWriter by real {
            override val writer: StepWriter get() = this

            override fun write(draft: StoryDraft, model: DraftingModel): Flow<WriterEvent> =
                flow { error("a broken writer") }
        }
        val wizard = wizard(backgroundScope, InMemoryDraftRepository(beforePremise), policies(broken))
        wizard.start()

        wizard.outcomes.test {
            wizard.next()
            val failure = assertIs<WizardFailure.Unexpected>(assertIs<WizardOutcome.Failed>(awaitItem()).failure)
            assertEquals("a broken writer", failure.cause.message)
        }
        assertFalse(wizard.current().writer!!.writing)

        wizard.edit(DraftEdit.Premise("A bell"))
        assertEquals(StepBody.Premise("A bell", own = true), wizard.current().body, "verbs run again")
    }

    @Test
    fun `arriving on the cast casts people, on offer until taken`() =
        test(beforePremise.copy(step = CreationStep.Premise, premise = Phrase.Own("A bell."))) { wizard, repository ->
            wizard.next()
            assertEquals(1, cast.calls.size)
            assertTrue(wizard.current().writer!!.writing)

            cast.answer(Outcome.Success(listOf(person("ida", "Ida"), person("bo", "Bo"))))

            val body = assertIs<StepBody.Cast>(wizard.current().body)
            assertEquals(listOf("Ida", "Bo"), body.people.map { it.name })
            assertTrue(body.people.none { it.inStory })
            assertTrue(body.people.all { it.secret == ShownSecret.Sealed })
            assertFalse(wizard.current().controls.canAdvance, "nobody is in the story yet")
            assertEquals(
                listOf(CastMember.Origin.Offered, CastMember.Origin.Offered),
                repository.stored.cast.map {
                    it.origin
                },
            )
        }

    @Test
    fun `the cast's die keeps everyone in the story and the player's own, and swaps the rest`() {
        val kept = CastMember(person("maren", "Maren"), CastMember.Origin.Offered, inStory = true)
        val own = CastMember(person("ottilie", "Ottilie"), CastMember.Origin.Own, inStory = false)
        val passed = CastMember(person("cy", "Cy"), CastMember.Origin.Offered, inStory = false)
        val draft = beforePremise.copy(step = CreationStep.Cast, cast = listOf(kept, own, passed))

        test(draft) { wizard, repository ->
            wizard.roll()
            cast.answer(Outcome.Success(listOf(person("ida", "Ida"))))

            assertEquals(listOf("Maren", "Ottilie", "Ida"), repository.stored.cast.map { it.character.name })
            assertEquals(listOf(kept, own), repository.stored.cast.take(2))
        }
    }

    @Test
    fun `while people are cast, the ones on screen cannot be taken`() {
        val offered = CastMember(person("cy", "Cy"), CastMember.Origin.Offered, inStory = false)

        test(beforePremise.copy(step = CreationStep.Cast, cast = listOf(offered))) { wizard, repository ->
            wizard.roll()

            wizard.select(Choice.CastMember(CharacterId("cy")))

            assertFalse(repository.stored.cast.single().inStory)
        }
    }

    private fun person(id: String, name: String) =
        Character(CharacterId(id), name = name, wants = "Out", secret = Secret.Sealed("$name's secret"))
}
