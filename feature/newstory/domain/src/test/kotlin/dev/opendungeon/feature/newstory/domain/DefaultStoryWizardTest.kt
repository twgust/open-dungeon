package dev.opendungeon.feature.newstory.domain

import app.cash.turbine.test
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.DefaultSentenceBuilder
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.feature.newstory.domain.policy.GenrePolicy
import dev.opendungeon.feature.newstory.domain.policy.LimitsPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStoryWizardTest {

    private val policies: Set<StepPolicy> = wizardPolicies()

    private fun wizard(
        scope: CoroutineScope,
        repository: InMemoryDraftRepository,
        policies: Set<StepPolicy> = this.policies,
    ) = DefaultStoryWizard(
        scope,
        repository,
        policies,
        DefaultSentenceBuilder(),
        CountingIds("draft"),
        FakeDrafter(),
        FakeBeginStory(repository),
    )

    private suspend fun StoryWizard.current(): WizardState = state.filterNotNull().first()

    private fun test(block: suspend TestScope.(StoryWizard, InMemoryDraftRepository) -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            val repository = InMemoryDraftRepository()
            block(wizard(backgroundScope, repository), repository)
        }

    @Test
    fun `starting with nothing open makes a new draft on the first step`() = test { wizard, repository ->
        wizard.start()

        val state = wizard.current()
        assertEquals(CreationStep.Genre, state.step)
        assertEquals(DraftId("draft-1"), state.draft)
        assertEquals(setOf(DraftId("draft-1")), repository.drafts.value.keys)
        assertTrue(state.lead.isEmpty(), "the genre step leads with its heading alone")
    }

    @Test
    fun `starting resumes the open draft on its saved step`() = runTest(UnconfinedTestDispatcher()) {
        val saved = StoryDraft(DraftId("old"), step = CreationStep.Tone, genres = listOf(Phrase.Offered("Gothic")))
        val wizard = wizard(backgroundScope, InMemoryDraftRepository(saved))

        wizard.start()

        assertEquals(CreationStep.Tone, wizard.current().step)
        assertEquals(DraftId("old"), wizard.current().draft)
    }

    @Test
    fun `a single pick saves the choice and moves to the next step`() = test { wizard, repository ->
        wizard.start()
        wizard.select(Choice.Word("Gothic"))
        wizard.next()
        wizard.select(Choice.Word("a heist"))

        val state = wizard.current()
        assertEquals(CreationStep.Starring, state.step)
        assertEquals("A gothic story, twisted by a heist.", state.lead.text)
        assertEquals(Phrase.Offered("a heist"), repository.stored.twist)
    }

    @Test
    fun `next does nothing until the step can be left`() = test { wizard, repository ->
        wizard.start()
        val saves = repository.saves

        wizard.next()

        assertEquals(CreationStep.Genre, wizard.current().step)
        assertFalse(wizard.current().controls.canAdvance)
        assertEquals(saves, repository.saves, "nothing changed, so nothing was written")
    }

    @Test
    fun `the die on genre picks two and moves on`() = test { wizard, _ ->
        wizard.start()
        wizard.roll()

        val state = wizard.current()
        assertEquals(CreationStep.Twist, state.step)
        assertEquals("A gothic noir story.", state.lead.text)
    }

    @Test
    fun `back steps back, and back from the first step leaves with the draft kept`() = test { wizard, repository ->
        wizard.start()
        wizard.roll()

        wizard.outcomes.test {
            wizard.back()
            assertEquals(CreationStep.Genre, wizard.current().step)
            wizard.back()
            assertEquals(WizardOutcome.Left, awaitItem())
        }
        assertEquals(1, repository.drafts.value.size)
    }

    @Test
    fun `controls follow the current policy`() = test { wizard, _ ->
        wizard.start()
        wizard.select(Choice.Word("Gothic"))
        wizard.select(Choice.Word("Noir"))

        val controls = wizard.current().controls
        assertTrue(controls.canAdvance)
        assertTrue(controls.canRoll)
        assertFalse(controls.takesOwnWords, "two genres are picked")
    }

    @Test
    fun `on the limits, the lead is the whole story clause`() = runTest(UnconfinedTestDispatcher()) {
        val draft = StoryDraft(
            DraftId("d"),
            step = CreationStep.Limits,
            genres = listOf(Phrase.Offered("Noir")),
            tone = Phrase.Offered("coldly"),
            setting = Phrase.Own("a lighthouse"),
        )
        val wizard = wizard(backgroundScope, InMemoryDraftRepository(draft))

        wizard.start()

        assertEquals("A noir story, told coldly, set in a lighthouse.", wizard.current().lead.text)
    }

    @Test
    fun `the last step moves forward by beginning, never by a next that leads nowhere`() =
        runTest(UnconfinedTestDispatcher()) {
            val draft = StoryDraft(DraftId("d"), step = CreationStep.Opening, opening = "Rain.")
            val wizard = wizard(backgroundScope, InMemoryDraftRepository(draft))

            wizard.start()
            wizard.next()

            assertTrue(wizard.current().controls.begins)
            assertTrue(wizard.current().controls.canAdvance)
            assertEquals(CreationStep.Opening, wizard.current().step)
        }

    @Test
    fun `every step but the last moves forward by next`() = test { wizard, _ ->
        wizard.start()

        assertFalse(wizard.current().controls.begins)
    }

    @Test
    fun `verbs apply in order, each to the draft the last one left`() = test { wizard, repository ->
        wizard.start()
        wizard.select(Choice.Word("Gothic"))
        wizard.submitOwn("salt-punk")
        wizard.next()
        wizard.submitOwn("a stolen saint")
        advanceUntilIdle()

        val draft = repository.stored
        assertEquals(listOf(Phrase.Offered("Gothic"), Phrase.Own("salt-punk")), draft.genres)
        assertEquals(Phrase.Own("a stolen saint"), draft.twist)
        assertEquals(CreationStep.Starring, draft.step)
    }

    @Test
    fun `every step needs exactly one policy`() = runTest {
        val withoutLimits = policies.filterNot { it is LimitsPolicy }.toSet()
        val twoGenres = policies + GenrePolicy(TestVocabulary, FirstWordsRoll())

        assertThrows<IllegalArgumentException> { wizard(backgroundScope, InMemoryDraftRepository(), withoutLimits) }
        assertThrows<IllegalArgumentException> { wizard(backgroundScope, InMemoryDraftRepository(), twoGenres) }
    }

    @Test
    fun `a pick that cannot be saved is taken back and reported`() = test { wizard, repository ->
        wizard.start()

        wizard.outcomes.test {
            repository.failSaves = 1
            wizard.select(Choice.Word("Gothic"))

            assertEquals(WizardOutcome.Failed(WizardFailure.Storage(StorageError.Full)), awaitItem())
        }
        val shown = assertIs<StepBody.Words>(wizard.current().body)
        assertTrue(shown.options.none { it.picked }, "the screen shows the draft as it was")
        assertEquals(emptyList(), repository.stored.genres)
    }

    @Test
    fun `a rule that throws changes nothing, and the wizard keeps working`() = runTest(UnconfinedTestDispatcher()) {
        val genre = GenrePolicy(TestVocabulary, FirstWordsRoll())
        val throwing = object : StepPolicy by genre {
            override fun select(draft: StoryDraft, choice: Choice): Selection = error("a broken rule")
        }
        val repository = InMemoryDraftRepository()
        val wizard = wizard(backgroundScope, repository, policies - policies.first { it is GenrePolicy } + throwing)
        wizard.start()
        val before = wizard.current()

        wizard.outcomes.test {
            wizard.select(Choice.Word("Gothic"))
            val failure = assertIs<WizardFailure.Unexpected>(assertIs<WizardOutcome.Failed>(awaitItem()).failure)
            assertEquals("a broken rule", failure.cause.message)
        }
        assertEquals(before, wizard.current())

        wizard.roll()
        assertEquals(CreationStep.Twist, wizard.current().step, "later verbs still run")
    }

    @Test
    fun `typing shows at once and is written once it settles`() = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryDraftRepository(StoryDraft(DraftId("d"), step = CreationStep.Premise))
        val wizard = wizard(backgroundScope, repository)
        wizard.start()
        val saves = repository.saves

        "A bell".scan("") { typed, letter -> typed + letter }.drop(1).forEach { wizard.edit(DraftEdit.Premise(it)) }

        assertEquals(StepBody.Premise("A bell", own = true), wizard.current().body)
        assertTrue(wizard.current().controls.canAdvance, "what was typed counts before it is written")
        assertEquals(saves, repository.saves)

        advanceTimeBy(DefaultStoryWizard.TYPING_SETTLES)
        runCurrent()

        assertEquals(saves + 1, repository.saves, "six keystrokes, one write")
        assertEquals(Phrase.Own("A bell"), repository.stored.premise)
    }

    @Test
    fun `a pick writes the typing before it in the same save`() = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryDraftRepository(StoryDraft(DraftId("d"), step = CreationStep.Limits))
        val wizard = wizard(backgroundScope, repository)
        wizard.start()
        val saves = repository.saves

        wizard.edit(DraftEdit.LimitsNote("no harm to animals"))
        wizard.select(Choice.Violence(ContentLimits.Violence.Offstage))
        advanceUntilIdle()

        assertEquals(saves + 1, repository.saves)
        assertEquals("no harm to animals", repository.stored.limits.note)
    }

    @Test
    fun `typing that cannot be saved stays on screen and goes with the next save`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = InMemoryDraftRepository(StoryDraft(DraftId("d"), step = CreationStep.Premise))
            val wizard = wizard(backgroundScope, repository)
            wizard.start()

            wizard.outcomes.test {
                repository.failSaves = 1
                wizard.edit(DraftEdit.Premise("A bell"))
                advanceTimeBy(DefaultStoryWizard.TYPING_SETTLES)
                runCurrent()

                assertEquals(WizardOutcome.Failed(WizardFailure.Storage(StorageError.Full)), awaitItem())
            }
            assertEquals(StepBody.Premise("A bell", own = true), wizard.current().body, "typing is never taken back")
            assertNull(repository.stored.premise)

            wizard.next()

            assertEquals(Phrase.Own("A bell"), repository.stored.premise)
            assertEquals(CreationStep.Cast, repository.stored.step)
        }

    @Test
    fun `typing still unsaved when the screen goes is saved on the way out`() = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryDraftRepository(StoryDraft(DraftId("d"), step = CreationStep.Starring))
        val screen = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val wizard = wizard(screen, repository)
        wizard.start()
        wizard.select(Choice.Word("a forger"))

        wizard.edit(DraftEdit.YouLine(CharacterLine.Wants, "To sleep "))
        screen.cancel()

        assertEquals("To sleep ", repository.stored.protagonist?.wants, "kept as typed, trailing space and all")
    }

    @Test
    fun `a draft that cannot be read is not opened, and back still leaves`() = test { wizard, repository ->
        wizard.outcomes.test {
            repository.failReads = 1
            wizard.start()
            assertEquals(WizardOutcome.Failed(WizardFailure.Storage(StorageError.Full)), awaitItem())
            assertNull(wizard.state.value)
            assertTrue(repository.drafts.value.isEmpty(), "a failed read never becomes a fresh draft")

            wizard.back()
            assertEquals(WizardOutcome.Left, awaitItem())
        }

        wizard.start()
        assertEquals(CreationStep.Genre, wizard.current().step, "starting again tries again")
    }

    @Test
    fun `a new draft that cannot be written is not opened`() = test { wizard, repository ->
        wizard.outcomes.test {
            repository.failSaves = 1
            wizard.start()
            assertEquals(WizardOutcome.Failed(WizardFailure.Storage(StorageError.Full)), awaitItem())
        }
        assertNull(wizard.state.value)
    }
}
