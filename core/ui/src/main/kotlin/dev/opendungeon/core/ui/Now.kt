package dev.opendungeon.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The time now, read again every [every], so text such as "2 hours ago" keeps up while it is on screen.
 * [clock] is there for previews and tests.
 */
@Composable
public fun rememberNow(every: Duration = 1.minutes, clock: Clock = Clock.System): Instant {
    var millis by remember(clock) { mutableLongStateOf(clock.now().toEpochMilliseconds()) }
    LaunchedEffect(clock, every) {
        while (true) {
            delay(every)
            millis = clock.now().toEpochMilliseconds()
        }
    }
    return Instant.fromEpochMilliseconds(millis)
}
