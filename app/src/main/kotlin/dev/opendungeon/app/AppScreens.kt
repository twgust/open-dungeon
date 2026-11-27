package dev.opendungeon.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.feature.connect.ui.ConnectRoute
import dev.opendungeon.feature.library.ui.LibraryRoute
import dev.opendungeon.feature.models.ui.picker.ModelPickerRoute
import dev.opendungeon.feature.models.ui.trial.TryRoute
import dev.opendungeon.feature.models.ui.tuner.ModelTunerRoute
import dev.opendungeon.feature.newstory.ui.NewStoryRoute
import dev.opendungeon.feature.play.ui.PlayRoute
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

/** A story being played. Holds the id's text, since that is what survives the process. */
@Serializable
private data class Play(val story: String) : NavKey

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
                    library = { modifier ->
                        LibraryRoute(
                            onOpen = { story -> backStack.add(Play(story.value)) },
                            onNewStory = { backStack.add(NewStory) },
                            modifier = modifier,
                        )
                    },
                )
            }
            entry<Models> { ModelPickerRoute(onBack = backStack::pop, onOpenTuning = { backStack.add(Tuning) }) }
            entry<Tuning> { ModelTunerRoute(onBack = backStack::pop, onTry = { backStack.add(Trial) }) }
            entry<Trial> { TryRoute(onLeave = backStack::pop) }
            entry<NewStory> {
                NewStoryRoute(
                    onLeave = backStack::pop,
                    onModels = { backStack.add(Models) },
                    // The draft is now a story, so back from play goes home, not to a draft that is gone.
                    onBegin = { story -> backStack.replaceTop(Play(story.value)) },
                )
            }
            entry<Play> { key ->
                PlayRoute(
                    story = StoryId(key.story),
                    onLeave = backStack::pop,
                    onModels = { backStack.add(Models) },
                )
            }
        },
    )
}

/** Puts [key] in place of the top screen, so back skips the screen it replaced. */
private fun NavBackStack<NavKey>.replaceTop(key: NavKey) {
    if (size > 1) removeAt(lastIndex)
    add(key)
}

/** Leaves the top screen; the first one stays, since leaving it is leaving the app. */
private fun NavBackStack<NavKey>.pop() {
    if (size > 1) removeAt(lastIndex)
}
