package dev.opendungeon.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.opendungeon.core.designsystem.Paper

/** A 2 dp ink line across the top of the page that grows with each step. */
@Composable
public fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(durationMillis = 800), label = "progress")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(colors.track)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(colors.ink))
    }
}
