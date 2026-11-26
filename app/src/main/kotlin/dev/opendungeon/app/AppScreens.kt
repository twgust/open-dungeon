package dev.opendungeon.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.opendungeon.feature.connect.ui.ConnectRoute
import dev.opendungeon.feature.newstory.ui.NewStoryRoute

/** The app's two places for now. A real back stack arrives with the story list and play screen. */
private enum class Screen { Connect, NewStory }

@Composable
internal fun AppScreens() {
    var screen by rememberSaveable { mutableStateOf(Screen.Connect) }
    when (screen) {
        Screen.Connect -> ConnectRoute(onNewStory = { screen = Screen.NewStory })
        Screen.NewStory -> NewStoryRoute(onLeave = { screen = Screen.Connect })
    }
}
