package dev.opendungeon.feature.newstory.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.Secret
import dev.opendungeon.domain.story.Sentence
import dev.opendungeon.domain.story.Take
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.WizardState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.toImmutableList

/** Turns the wizard's state into what the screen draws. Pure mapping, no decisions. */
fun interface WizardUiMapper {
    operator fun invoke(state: WizardState): WizardUiState.Step
}

@Inject
@ContributesBinding(AppScope::class)
class DefaultWizardUiMapper : WizardUiMapper {

    override fun invoke(state: WizardState): WizardUiState.Step = WizardUiState.Step(
        step = state.step,
        progress = state.progress,
        lead = state.lead.toAnnotated(),
        body = state.body.toUi(),
        controls = state.controls,
    )

    private fun Sentence.toAnnotated(): AnnotatedString = buildAnnotatedString {
        segments.forEach { segment ->
            if (segment is Sentence.Segment.Chosen && segment.own) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(segment.text) }
            } else {
                append(segment.text)
            }
        }
    }

    private fun StepBody.toUi(): StepBodyUi = when (this) {
        is StepBody.Words -> StepBodyUi.Words(options.toImmutableList())
        is StepBody.Starring -> StepBodyUi.Starring(
            roles = roles.toImmutableList(),
            you = you?.let { character -> YouCardUi(lines.map { CardLineUi(it, character[it].orEmpty()) }.toImmutableList()) },
        )
        is StepBody.Limits -> StepBodyUi.Limits(limits.desire, limits.violence, limits.note)
        is StepBody.Premise -> StepBodyUi.Premise(text)
        is StepBody.Cast -> StepBodyUi.Cast(members.map { it.toCard() }.toImmutableList())
        is StepBody.FirstLine -> StepBodyUi.FirstLine(text, maxLength)
        is StepBody.Auditions -> StepBodyUi.Auditions(takes.map { it.toCard(chosen = it.id == chosen) }.toImmutableList())
    }

    private fun CastMember.toCard() = PersonCardUi(
        id = character.id,
        role = character.role,
        name = character.name,
        appearance = character.appearance,
        lines = PERSON_LINES.mapNotNull { line -> character[line]?.let { CardLineUi(line, it) } }.toImmutableList(),
        secret = character.secret.toUi(),
        inStory = inStory,
    )

    private fun Secret.toUi(): SecretUi = when (this) {
        Secret.None -> SecretUi.None
        is Secret.Known -> SecretUi.Known(text)
        is Secret.Sealed -> SecretUi.Sealed
    }

    private fun Take.toCard(chosen: Boolean) = TakeCardUi(
        id = id,
        model = model.value,
        text = text,
        writing = state == Take.State.Writing,
        interrupted = state == Take.State.Interrupted,
        chosen = chosen,
    )

    private companion object {
        /** What a cast card shows under the name, in this order. */
        val PERSON_LINES = listOf(CharacterLine.Wants, CharacterLine.Habit, CharacterLine.VoiceLine)
    }
}
