package dev.opendungeon.feature.newstory.domain

import app.cash.turbine.test
import dev.opendungeon.domain.drafting.DraftingError
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.DefaultSentenceBuilder
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.feature.newstory.domain.StepBody.Opening.Wand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The opening: the wand that writes or carries on, stopping, undo, failure, leaving and Begin. */
@OptIn(ExperimentalCoroutinesApi::class)
class OpeningStepTest {

    private val opening = ScriptedWriting()
    private val drafter = FakeDrafter(storyteller)
    private lateinit var begin: FakeBeginStory

    private val onOpening = StoryDraft(
        DraftId("d"),
        step = CreationStep.Opening,
        genres = listOf(Phrase.Offered("Gothic")),
        premise = Phrase.Own("You owe the salt."),
        cast = listOf(
            CastMember(Character(CharacterId("m"), name = "Maren Kask"), CastMember.Origin.Offered, inStory = true),
        ),
    )

    private fun test(
        draft: StoryDraft = onOpening,
        block: suspend TestScope.(StoryWizard, InMemoryDraftRepository) -> Unit,
    ) = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryDraftRepository(draft)
        begin = FakeBeginStory(repository)
        val wizard = DefaultStoryWizard(
            backgroundScope,
            repository,
            wizardPolicies(opening = opening),
            DefaultSentenceBuilder(),
            CountingIds("person"),
            drafter,
            begin,
        )
        wizard.start()
        block(wizard, repository)
    }

    private suspend fun StoryWizard.current(): WizardState = state.filterNotNull().first()

    private suspend fun StoryWizard.body(): StepBody.Opening = assertIs<StepBody.Opening>(current().body)

    private fun idle(text: String, wand: Wand = if (text.isBlank()) Wand.WriteForMe else Wand.ContinueMyText) =
        StepBody.Opening(
            text = text,
            writing = null,
            wand = wand,
            addition = null,
            failed = false,
            maxLength = StoryDraft.MAX_OPENING_LENGTH,
        )

    @Test
    fun `the step opens empty, writes nothing by itself, and cannot begin yet`() = test { wizard, _ ->
        assertEquals(idle(""), wizard.body())
        assertTrue(opening.runs.isEmpty())
        val controls = wizard.current().controls
        assertTrue(controls.begins)
        assertFalse(controls.canAdvance)
        assertTrue(controls.canRoll, "the wand can write it")
    }

    @Test
    fun `the lead is the whole story, cast and all`() = test { wizard, _ ->
        assertEquals("A gothic story, among Maren Kask.", wizard.current().lead.text)
    }

    @Test
    fun `typing makes the wand carry on, and lets the story begin`() = test { wizard, _ ->
        wizard.edit(DraftEdit.Opening("You wait."))

        assertEquals(idle("You wait."), wizard.body())
        assertTrue(wizard.current().controls.canAdvance)
    }

    @Test
    fun `the wand on an empty field writes the opening, streaming it, and keeps it once written`() =
        test { wizard, repository ->
            wizard.roll()

            val run = opening.runs.single()
            assertEquals(storyteller, run.model)
            assertEquals(StepBody.Opening.Writing.Waiting, wizard.body().writing, "it answers before the first word")
            val waiting = wizard.current().controls
            assertFalse(waiting.canAdvance, "Begin waits while it writes")
            assertFalse(waiting.canRoll)
            assertEquals(ModelLine(model = "Storyteller", writing = true), wizard.current().writer)

            run.writing("Rain falls")
            assertEquals(StepBody.Opening.Writing.Adding("Rain falls"), wizard.body().writing)
            assertEquals("", wizard.body().text)
            assertEquals("", repository.stored.opening, "what is being written is never saved")

            run.written("Rain falls on the salt.")

            assertEquals(
                idle("Rain falls on the salt.").copy(addition = StepBody.Opening.Addition(stopped = false)),
                wizard.body(),
            )
            assertEquals("Rain falls on the salt.", repository.stored.opening)
            assertTrue(wizard.current().controls.canAdvance)
        }

    @Test
    fun `the wand on the player's text carries on after it, and never touches it`() =
        test(onOpening.copy(opening = "You wait.")) { wizard, repository ->
            wizard.roll()

            val run = opening.last
            assertEquals("You wait.", run.draft.opening, "the model reads the text to carry on from")
            run.writing(" Maren")
            assertEquals("You wait.", wizard.body().text)
            assertEquals(StepBody.Opening.Writing.Adding(" Maren"), wizard.body().writing)

            run.written(" Maren is late.")

            assertEquals("You wait. Maren is late.", repository.stored.opening)
        }

    @Test
    fun `undo takes back only what the model added`() = test(onOpening.copy(opening = "You wait.")) { wizard, repo ->
        wizard.roll()
        opening.last.written(" Maren is late.")

        wizard.undo()

        assertEquals(idle("You wait."), wizard.body())
        assertEquals("You wait.", repo.stored.opening)
    }

    @Test
    fun `stopping keeps what was written so far, says it stopped, and can be undone`() =
        test(onOpening.copy(opening = "You wait.")) { wizard, repository ->
            wizard.roll()
            val run = opening.last
            run.writing(" Maren is")

            wizard.stop()

            assertTrue(run.cancelled)
            assertEquals(
                idle("You wait. Maren is").copy(addition = StepBody.Opening.Addition(stopped = true)),
                wizard.body(),
            )
            assertEquals("You wait. Maren is", repository.stored.opening)
            run.written(" Maren is late.")
            assertEquals("You wait. Maren is", repository.stored.opening, "the stopped run's words never land")

            wizard.undo()
            assertEquals(idle("You wait."), wizard.body())
        }

    @Test
    fun `stopping before the first word keeps the text as it was, with nothing to undo`() =
        test(onOpening.copy(opening = "You wait.")) { wizard, _ ->
            wizard.roll()

            wizard.stop()

            assertEquals(idle("You wait."), wizard.body())
        }

    @Test
    fun `typing after the model wrote makes it all the player's, with nothing left to undo`() =
        test { wizard, repository ->
            wizard.roll()
            opening.last.written("Rain falls.")

            wizard.edit(DraftEdit.Opening("Rain falls. You wait."))
            wizard.undo()

            assertEquals(idle("Rain falls. You wait."), wizard.body())
            advanceTimeBy(DefaultStoryWizard.TYPING_SETTLES)
            runCurrent()
            assertEquals("Rain falls. You wait.", repository.stored.opening)
        }

    @Test
    fun `a failure leaves the text exactly as it was, says so on the step, and the wand tries again`() =
        test(onOpening.copy(opening = "You wait.")) { wizard, repository ->
            wizard.roll()
            opening.last.writing(" Maren")

            wizard.outcomes.test {
                opening.last.failed(DraftingError.Generation(GenerationError.Interrupted))
                expectNoEvents()
            }

            assertEquals(idle("You wait.").copy(failed = true), wizard.body())
            assertEquals("You wait.", repository.stored.opening)

            wizard.roll()
            assertEquals(2, opening.runs.size)
            assertFalse(wizard.body().failed, "trying again clears the failure")
            opening.last.written(" Maren is late.")
            assertEquals("You wait. Maren is late.", repository.stored.opening)
        }

    @Test
    fun `typing clears a failure`() = test { wizard, _ ->
        wizard.roll()
        opening.last.failed(DraftingError.Unusable)

        wizard.edit(DraftEdit.Opening("You"))

        assertEquals(idle("You"), wizard.body())
    }

    @Test
    fun `back while writing asks first, and staying lets it write on`() =
        test(onOpening.copy(opening = "You wait.")) { wizard, repository ->
            wizard.roll()
            opening.last.writing(" Maren")

            wizard.back()
            assertTrue(wizard.current().confirmingLeave)
            assertFalse(opening.last.cancelled)

            wizard.answerLeave(LeaveAnswer.Stay)
            opening.last.written(" Maren is late.")

            assertFalse(wizard.current().confirmingLeave)
            assertEquals("You wait. Maren is late.", repository.stored.opening)
        }

    @Test
    fun `stopping and going back drops what was generated and keeps the player's text`() =
        test(onOpening.copy(opening = "You wait.")) { wizard, repository ->
            wizard.roll()
            opening.last.writing(" Maren")
            wizard.back()

            wizard.answerLeave(LeaveAnswer.Leave)

            assertTrue(opening.last.cancelled)
            assertEquals(CreationStep.Cast, wizard.current().step)
            assertEquals("You wait.", repository.stored.opening)
            assertEquals(CreationStep.Cast, repository.stored.step)
        }

    @Test
    fun `Begin turns the draft into a story, typed text and all, and hands the story on`() =
        test { wizard, repository ->
            wizard.edit(DraftEdit.Opening("You wait."))

            wizard.outcomes.test {
                wizard.begin()
                assertEquals(WizardOutcome.Begun(StoryId("story-1")), awaitItem())
            }
            assertEquals("You wait.", begin.begun.single().opening, "typed text begins the story before it settles")
            assertTrue(repository.drafts.value.isEmpty(), "the draft is closed")
        }

    @Test
    fun `after Begin the wizard is done, and nothing it does saves the draft again`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = InMemoryDraftRepository(onOpening)
            begin = FakeBeginStory(repository)
            val scope = CoroutineScope(coroutineContext + Job())
            val wizard = DefaultStoryWizard(
                scope,
                repository,
                wizardPolicies(opening = opening),
                DefaultSentenceBuilder(),
                CountingIds("person"),
                drafter,
                begin,
            )
            wizard.start()
            wizard.edit(DraftEdit.Opening("You wait."))
            wizard.begin()
            val saves = repository.saves

            wizard.edit(DraftEdit.Opening("You wait. Rain."))
            wizard.roll()
            advanceTimeBy(DefaultStoryWizard.TYPING_SETTLES * 2)
            scope.cancel()
            runCurrent()

            assertEquals(saves, repository.saves)
            assertTrue(repository.drafts.value.isEmpty(), "the closed draft is never written back")
            assertTrue(opening.runs.isEmpty())
        }

    @Test
    fun `Begin does nothing while the opening is empty or being written`() = test { wizard, _ ->
        wizard.outcomes.test {
            wizard.begin()
            wizard.roll()
            opening.last.writing("Rain")
            wizard.begin()
            expectNoEvents()
        }
    }

    @Test
    fun `Begin that cannot store the story says why and stays on the step, draft and all`() =
        test(onOpening.copy(opening = "You wait.")) { wizard, repository ->
            begin.fails = 1

            wizard.outcomes.test {
                wizard.begin()
                assertIs<WizardFailure.Storage>(assertIs<WizardOutcome.Failed>(awaitItem()).failure)
                expectNoEvents()
            }
            assertEquals(CreationStep.Opening, wizard.current().step)
            assertEquals("You wait.", repository.stored.opening)

            wizard.outcomes.test {
                wizard.begin()
                assertEquals(WizardOutcome.Begun(StoryId("story-1")), awaitItem())
            }
        }

    @Test
    fun `Begin is only the last step's`() = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryDraftRepository(onOpening.copy(step = CreationStep.Premise))
        val wizard = DefaultStoryWizard(
            backgroundScope,
            repository,
            wizardPolicies(opening = opening),
            DefaultSentenceBuilder(),
            CountingIds("person"),
            drafter,
            FakeBeginStory(repository),
        )
        wizard.start()

        wizard.outcomes.test {
            wizard.begin()
            expectNoEvents()
        }
    }

    @Test
    fun `an undo that cannot be saved is taken back, and can be tried again`() =
        test(onOpening.copy(opening = "You wait.")) { wizard, repository ->
            wizard.roll()
            opening.last.written(" Maren is late.")
            repository.failSaves = 1

            wizard.outcomes.test {
                wizard.undo()
                assertIs<WizardOutcome.Failed>(awaitItem())
            }
            assertEquals(StepBody.Opening.Addition(stopped = false), wizard.body().addition)
            assertEquals("You wait. Maren is late.", wizard.body().text)

            wizard.undo()
            assertEquals("You wait.", repository.stored.opening)
        }

    @Test
    fun `with no model the wand says to choose one`() = test { wizard, _ ->
        drafter.model.value = null

        wizard.outcomes.test {
            wizard.roll()
            assertEquals(WizardOutcome.Failed(WizardFailure.Drafting(DraftingError.NoModel)), awaitItem())
        }
        assertTrue(opening.runs.isEmpty())
        assertNull(wizard.body().writing)
    }

    @Test
    fun `what was left by writing on the opening is gone after leaving the step`() =
        test(onOpening.copy(opening = "You wait.")) { wizard, _ ->
            wizard.roll()
            opening.last.written(" Maren is late.")

            wizard.back()
            wizard.next()

            assertNull(wizard.body().addition)
        }
}
