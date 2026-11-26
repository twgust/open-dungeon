package dev.opendungeon.feature.models.ui.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opendungeon.core.mvi.MviViewModel
import dev.opendungeon.feature.models.domain.ModelPicker
import dev.opendungeon.feature.models.domain.PickerEvent
import dev.opendungeon.feature.models.domain.PickerState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** A translator: intents go to the picker as verbs, its state comes back as UI state, its events as effects. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ModelPickerViewModel(pickerFactory: ModelPicker.Factory) :
    MviViewModel<PickerState, PickerIntent, PickerEffect>(PickerState()) {

    private val picker = pickerFactory.create(viewModelScope)

    init {
        picker.state.onEach { state -> reduce { state } }.launchIn(viewModelScope)
        picker.events.onEach { event ->
            sendEffect(
                when (event) {
                    is PickerEvent.Chosen -> PickerEffect.OpenChosen
                    is PickerEvent.Failed -> PickerEffect.ShowFailure(event.error)
                },
            )
        }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: PickerIntent) = when (intent) {
        is PickerIntent.Search -> picker.search(intent.query)
        is PickerIntent.SortBy -> picker.sortBy(intent.sort)
        is PickerIntent.ToggleInput -> picker.toggleInput(intent.modality)
        PickerIntent.ToggleFreeOnly -> picker.toggleFreeOnly()
        PickerIntent.ToggleTools -> picker.toggleTools()
        is PickerIntent.ToggleContext -> picker.toggleContext(intent.tokens)
        PickerIntent.Refresh -> picker.refresh()
        is PickerIntent.Choose -> picker.choose(intent.id)
    }
}
