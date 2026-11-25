package dev.opendungeon.feature.newstory.ui

import androidx.compose.ui.text.font.FontStyle
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Secret
import dev.opendungeon.domain.story.Sentence
import dev.opendungeon.domain.story.Take
import dev.opendungeon.domain.story.TakeId
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.StepControls
import dev.opendungeon.feature.newstory.domain.WizardState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class WizardUiMapperTest {

    private val mapper = DefaultWizardUiMapper()
    private val controls = StepControls(canGoBack = true, canAdvance = false, advancesOnPick = false, canRoll = false, takesOwnWords = false)

    private fun state(body: StepBody, lead: Sentence = Sentence.Empty) =
        WizardState(DraftId("d"), CreationStep.Cast, 0.8f, lead, body, controls)

    @Test
    fun `a sealed secret reaches the screen as a flag, never as words`() {
        val maren = Character(
            id = CharacterId("maren"),
            role = "your employer",
            name = "Maren Kask",
            wants = "Her mother's bones back.",
            secret = Secret.Sealed("She sold them herself."),
        )

        val ui = mapper(state(StepBody.Cast(listOf(CastMember(maren, CastMember.Origin.Offered, inStory = true)))))

        val person = assertIs<StepBodyUi.Cast>(ui.body).people.single()
        assertEquals(SecretUi.Sealed, person.secret)
        assertFalse("She sold them herself." in person.toString())
        assertEquals(listOf(CardLineUi(CharacterLine.Wants, "Her mother's bones back.")), person.lines)
    }

    @Test
    fun `own words in the lead are set in italics`() {
        val lead = Sentence(
            listOf(
                Sentence.Segment.Fixed("A "),
                Sentence.Segment.Chosen("salt-punk", own = true),
                Sentence.Segment.Fixed(" story,"),
            ),
        )

        val ui = mapper(state(StepBody.FirstLine("", 4000), lead))

        assertEquals("A salt-punk story,", ui.lead.text)
        val italic = ui.lead.spanStyles.single()
        assertEquals(FontStyle.Italic, italic.item.fontStyle)
        assertEquals("salt-punk", ui.lead.text.substring(italic.start, italic.end))
    }

    @Test
    fun `takes carry their state and whether they are chosen`() {
        val takes = listOf(
            Take(TakeId("a"), ModelId("nous/hermes-4-405b"), "Rain.", Take.State.Written),
            Take(TakeId("b"), ModelId("x-ai/grok-4"), "Mid", Take.State.Writing),
        )

        val ui = mapper(state(StepBody.Auditions(takes, chosen = TakeId("a"))))

        val cards = assertIs<StepBodyUi.Auditions>(ui.body).takes
        assertEquals(listOf(true, false), cards.map { it.chosen })
        assertEquals(listOf(false, true), cards.map { it.writing })
    }
}
