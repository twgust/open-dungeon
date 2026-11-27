package dev.opendungeon.feature.newstory.ui.choice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opendungeon.core.mvi.MviViewModel
import dev.opendungeon.feature.newstory.domain.CreationPaths
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.launch

/** The two ways into a story. Both are always offered; the custom one says whether it resumes a draft. */
data class ChoiceUiState(val customResumes: Boolean = false)

sealed interface ChoiceIntent {
    /** The screen is showing. */
    data object Open : ChoiceIntent

    data object Seeded : ChoiceIntent

    data object Custom : ChoiceIntent

    data object Back : ChoiceIntent
}

sealed interface ChoiceEffect {
    data object OpenSeeded : ChoiceEffect

    data object OpenCustom : ChoiceEffect

    data object Leave : ChoiceEffect
}

/** Asks which path resumes what, and turns taps into navigation. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class NewStoryChoiceViewModel(private val paths: CreationPaths) :
    MviViewModel<ChoiceUiState, ChoiceIntent, ChoiceEffect>(ChoiceUiState()) {

    override fun onIntent(intent: ChoiceIntent) = when (intent) {
        ChoiceIntent.Open -> {
            viewModelScope.launch {
                val resumes = paths.customResumes()
                reduce { copy(customResumes = resumes) }
            }
            Unit
        }
        ChoiceIntent.Seeded -> sendEffect(ChoiceEffect.OpenSeeded)
        ChoiceIntent.Custom -> sendEffect(ChoiceEffect.OpenCustom)
        ChoiceIntent.Back -> sendEffect(ChoiceEffect.Leave)
    }
}
