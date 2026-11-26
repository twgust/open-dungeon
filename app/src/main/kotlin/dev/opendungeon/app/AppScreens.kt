package dev.opendungeon.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.opendungeon.feature.connect.ui.ConnectRoute
import dev.opendungeon.feature.models.ui.picker.ModelPickerRoute
import dev.opendungeon.feature.models.ui.trial.TryRoute
import dev.opendungeon.feature.models.ui.tuner.ModelTunerRoute
import dev.opendungeon.feature.newstory.ui.NewStoryRoute
import kotlinx.serialization.Serializable

/** The app's places. Keys are serializable, so the back stack survives the process being recreated. */
@Serializable
private data object Home : NavKey

@Serializable
private data object Models : NavKey

@Serializable
private data object Tuning : NavKey

@Serializable
private data object Trial : NavKey

@Serializable
private data object NewStory : NavKey

/**
 * One back stack for the whole app. Each entry gets its own ViewModel store, so a screen's work, a
 * streaming reply included, ends when the screen leaves the stack.
 */
@Composable
internal fun AppScreens() {
    val backStack = rememberNavBackStack(Home)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.pop() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Home> {
                ConnectRoute(
                    onModels = { backStack.add(Models) },
                    onTry = { backStack.add(Trial) },
                    onNewStory = { backStack.add(NewStory) },
                )
            }
            entry<Models> { ModelPickerRoute(onBack = backStack::pop, onOpenTuning = { backStack.add(Tuning) }) }
            entry<Tuning> { ModelTunerRoute(onBack = backStack::pop, onTry = { backStack.add(Trial) }) }
            entry<Trial> { TryRoute(onLeave = backStack::pop) }
            entry<NewStory> { NewStoryRoute(onLeave = backStack::pop, onModels = { backStack.add(Models) }) }
        },
    )
}

/** Leaves the top screen; the first one stays, since leaving it is leaving the app. */
private fun NavBackStack<NavKey>.pop() {
    if (size > 1) removeAt(lastIndex)
}
