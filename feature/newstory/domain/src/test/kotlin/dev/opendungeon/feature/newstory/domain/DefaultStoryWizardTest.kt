package dev.opendungeon.feature.newstory.domain

import app.cash.turbine.test
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.DefaultSentenceBuilder
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Phrase
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStoryWizardTest {

    private val policies: Set<StepPolicy> = setOf(
        GenrePolicy(TestVocabulary, FirstWordsRoll()),
        TwistPolicy(TestVocabulary, FirstWordsRoll()),
        StarringPolicy(TestVocabulary, FirstWordsRoll(), CountingIds("you")),
        TonePolicy(TestVocabulary, FirstWordsRoll()),
        SettingPolicy(TestVocabulary, FirstWordsRoll()),
        LimitsPolicy(),
        PremisePolicy(),
        CastPolicy(),
        FirstLinePolicy(),
        FirstTurnPolicy(),
    )

    private fun wizard(scope: CoroutineScope, repository: InMemoryDraftRepository, policies: Set<StepPolicy> = this.policies) =
        DefaultStoryWizard(scope, repository, policies, DefaultSentenceBuilder(), CountingIds("draft"))

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
        assertTrue(state.lead.isEmpty(), "the genre step leads with its prompt alone")
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
        assertEquals("A gothic story, twisted by a heist,", state.lead.text)
        assertEquals(Phrase.Offered("a heist"), repository.drafts.value.values.single().twist)
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
        assertEquals("A gothic noir story,", state.lead.text)
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
    fun `the last step never offers a next that leads nowhere`() = runTest(UnconfinedTestDispatcher()) {
        val wizard = wizard(backgroundScope, InMemoryDraftRepository(StoryDraft(DraftId("d"), step = CreationStep.FirstTurn)))

        wizard.start()
        wizard.next()

        assertFalse(wizard.current().controls.canAdvance)
        assertEquals(CreationStep.FirstTurn, wizard.current().step)
    }

    @Test
    fun `verbs apply in order, each to the draft the last one left`() = test { wizard, repository ->
        wizard.start()
        wizard.select(Choice.Word("Gothic"))
        wizard.submitOwn("salt-punk")
        wizard.next()
        wizard.submitOwn("a stolen saint")
        advanceUntilIdle()

        val draft = repository.drafts.value.values.single()
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
}
