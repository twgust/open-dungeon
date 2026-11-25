package dev.opendungeon.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dev.opendungeon.core.designsystem.PaperTheme
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.ActivityKey
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

/** Constructor-injected through `MetroAppComponentFactory`, declared in the manifest. */
@Inject
@ActivityKey
@ContributesIntoMap(AppScope::class, binding = binding<Activity>())
class MainActivity(private val viewModelFactory: MetroViewModelFactory) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalMetroViewModelFactory provides viewModelFactory) {
                PaperTheme {
                    AppScreens()
                }
            }
        }
    }
}
