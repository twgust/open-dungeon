package dev.opendungeon.feature.newstory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opendungeon.core.mvi.MviViewModel
import dev.opendungeon.feature.newstory.domain.StoryWizard
import dev.opendungeon.feature.newstory.domain.WizardOutcome
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * A translator: intents go to the wizard as verbs, the wizard's state comes back as UI state, its
 * outcomes as effects. Every rule and every choice of what to show lives in the wizard; nothing here
 * asks whether something is allowed.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class StoryWizardViewModel(wizardFactory: StoryWizard.Factory) :
    MviViewModel<WizardUiState, WizardIntent, WizardEffect>(WizardUiState.Loading) {

    private val wizard = wizardFactory.create(viewModelScope)

    init {
        wizard.state.filterNotNull().onEach { state -> reduce { WizardUiState.Ready(state) } }.launchIn(viewModelScope)
        wizard.outcomes.onEach { sendEffect(it.toEffect()) }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: WizardIntent) = when (intent) {
        WizardIntent.Open -> wizard.start()
        is WizardIntent.Pick -> wizard.select(intent.choice)
        is WizardIntent.Own -> wizard.submitOwn(intent.text)
        is WizardIntent.Edit -> wizard.edit(intent.edit)
        WizardIntent.Roll -> wizard.roll()
        is WizardIntent.RollLine -> wizard.rollLine(intent.line)
        WizardIntent.Next -> wizard.next()
        WizardIntent.Back -> wizard.back()
    }

    private fun WizardOutcome.toEffect(): WizardEffect = when (this) {
        WizardOutcome.Left -> WizardEffect.Leave
        is WizardOutcome.Failed -> WizardEffect.ShowFailure(failure)
    }
}
