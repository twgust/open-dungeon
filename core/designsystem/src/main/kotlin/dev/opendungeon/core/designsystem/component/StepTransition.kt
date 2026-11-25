package dev.opendungeon.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private const val FADE_OUT_MILLIS = 450
private const val FADE_IN_MILLIS = 600

/**
 * The page between steps: the old step fades out completely before the new one fades in, so the
 * sentence reads as one page being rewritten rather than screens sliding past.
 */
@Composable
public fun <S> StepTransition(
    step: S,
    modifier: Modifier = Modifier,
    content: @Composable (S) -> Unit,
) {
    AnimatedContent(
        targetState = step,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS)) togetherWith fadeOut(tween(FADE_OUT_MILLIS))
        },
        label = "step",
    ) { shown -> content(shown) }
}
