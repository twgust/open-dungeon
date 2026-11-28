package dev.opendungeon.feature.newstory.ui.seeded

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opendungeon.core.mvi.MviViewModel
import dev.opendungeon.feature.newstory.domain.WizardOutcome
import dev.opendungeon.feature.newstory.domain.seeded.SeededWizard
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * A translator, like the custom wizard's: intents go to the seeded wizard as verbs, its state comes back
 * as UI state and its outcomes as effects. Nothing here decides what is allowed.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class SeededViewModel(wizardFactory: SeededWizard.Factory) :
    MviViewModel<SeededUiState, SeededIntent, SeededEffect>(SeededUiState.Loading) {

    private val wizard = wizardFactory.create(viewModelScope)

    init {
        wizard.state.filterNotNull().onEach { state -> reduce { SeededUiState.Ready(state) } }.launchIn(viewModelScope)
        wizard.outcomes.onEach { sendEffect(it.toEffect()) }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: SeededIntent) = when (intent) {
        SeededIntent.Open -> wizard.start()
        is SeededIntent.PickBlank -> wizard.pick(intent.blank)
        is SeededIntent.FillBlank -> wizard.fill(intent.blank, intent.words)
        is SeededIntent.MoreWords -> wizard.suggestMore(intent.blank)
        SeededIntent.RollSeed -> wizard.rollSeed()
        is SeededIntent.PickShelf -> wizard.pickShelf(intent.shelf)
        is SeededIntent.Describe -> wizard.describe(intent.person, intent.text)
        is SeededIntent.Pick -> wizard.select(intent.choice)
        is SeededIntent.Own -> wizard.submitOwn(intent.text)
        SeededIntent.Next -> wizard.next()
        SeededIntent.Back -> wizard.back()
        SeededIntent.Stop -> wizard.stop()
        SeededIntent.Begin -> wizard.begin()
        is SeededIntent.AnswerLeave -> wizard.answerLeave(intent.answer)
        // Choosing a model is the models screen's work; going there is navigation, not a rule.
        SeededIntent.ChooseModel -> sendEffect(SeededEffect.OpenModels)
    }

    private fun WizardOutcome.toEffect(): SeededEffect = when (this) {
        WizardOutcome.Left -> SeededEffect.Leave
        is WizardOutcome.Begun -> SeededEffect.Begin(story)
        is WizardOutcome.Failed -> SeededEffect.ShowFailure(failure)
    }
}
