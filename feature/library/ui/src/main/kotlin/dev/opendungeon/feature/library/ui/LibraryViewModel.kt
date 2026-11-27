package dev.opendungeon.feature.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opendungeon.core.mvi.MviViewModel
import dev.opendungeon.feature.library.domain.Library
import dev.opendungeon.feature.library.domain.LibraryState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * A translator: intents go to the library as verbs, its state comes back as UI state, its failures as
 * effects. Opening a story and starting one are navigation, so they go straight out as effects.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class LibraryViewModel(libraryFactory: Library.Factory) :
    MviViewModel<LibraryUiState, LibraryIntent, LibraryEffect>(LibraryUiState(LibraryState())) {

    private val library = libraryFactory.create(viewModelScope)

    init {
        library.state.onEach { state -> reduce { LibraryUiState(state) } }.launchIn(viewModelScope)
        library.failures.onEach { sendEffect(LibraryEffect.ShowFailure(it)) }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: LibraryIntent) = when (intent) {
        is LibraryIntent.Open -> sendEffect(LibraryEffect.OpenStory(intent.story))
        LibraryIntent.NewStory -> sendEffect(LibraryEffect.StartStory)
        is LibraryIntent.Edit -> library.edit(intent.story)
        is LibraryIntent.TypeTitle -> library.typeTitle(intent.text)
        LibraryIntent.SaveTitle -> library.saveTitle()
        LibraryIntent.CancelEdit -> library.cancelEdit()
        LibraryIntent.Delete -> library.delete()
        LibraryIntent.Undo -> library.undoDelete()
    }
}
