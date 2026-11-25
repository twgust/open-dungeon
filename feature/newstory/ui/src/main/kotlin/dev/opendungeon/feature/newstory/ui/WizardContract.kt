package dev.opendungeon.feature.newstory.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.TakeId
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.DraftEdit
import dev.opendungeon.feature.newstory.domain.Option
import dev.opendungeon.feature.newstory.domain.StepControls
import kotlinx.collections.immutable.ImmutableList

/** What the wizard screen renders. It never holds the draft, and never a sealed secret's words. */
sealed interface WizardUiState {
    data object Loading : WizardUiState

    @Immutable
    data class Step(
        val step: CreationStep,
        val progress: Float,
        /** The sentence so far, own words in italics. Empty where the prompt stands alone. */
        val lead: AnnotatedString,
        val body: StepBodyUi,
        val controls: StepControls,
    ) : WizardUiState
}

@Immutable
sealed interface StepBodyUi {
    data class Words(val options: ImmutableList<Option>) : StepBodyUi

    data class Starring(val roles: ImmutableList<Option>, val you: YouCardUi?) : StepBodyUi

    data class Limits(val desire: ContentLimits.Desire, val violence: ContentLimits.Violence, val note: String?) : StepBodyUi

    data class Premise(val text: String) : StepBodyUi

    data class Cast(val people: ImmutableList<PersonCardUi>) : StepBodyUi

    data class FirstLine(val text: String, val maxLength: Int) : StepBodyUi

    data class Auditions(val takes: ImmutableList<TakeCardUi>) : StepBodyUi
}

/** The player's card on the starring step: one line per die. */
@Immutable
data class YouCardUi(val lines: ImmutableList<CardLineUi>)

@Immutable
data class CardLineUi(val line: CharacterLine, val text: String)

/** A person on offer. A sealed secret is only a flag here: its words never reach the screen. */
@Immutable
data class PersonCardUi(
    val id: CharacterId,
    val role: String?,
    val name: String?,
    val appearance: String?,
    val lines: ImmutableList<CardLineUi>,
    val secret: SecretUi,
    val inStory: Boolean,
)

@Immutable
sealed interface SecretUi {
    data object None : SecretUi

    data object Sealed : SecretUi

    data class Known(val text: String) : SecretUi
}

@Immutable
data class TakeCardUi(
    val id: TakeId,
    val model: String,
    val text: String,
    val writing: Boolean,
    val interrupted: Boolean,
    val chosen: Boolean,
)

/** Everything the player does on the wizard, said as it happened. */
sealed interface WizardIntent {
    data class Pick(val choice: Choice) : WizardIntent

    data class Own(val text: String) : WizardIntent

    data class Edit(val edit: DraftEdit) : WizardIntent

    data object Roll : WizardIntent

    data class RollLine(val line: CharacterLine) : WizardIntent

    data object Next : WizardIntent

    data object Back : WizardIntent
}

sealed interface WizardEffect {
    /** Creation was left from its first step; the draft is kept to resume. */
    data object Leave : WizardEffect
}
