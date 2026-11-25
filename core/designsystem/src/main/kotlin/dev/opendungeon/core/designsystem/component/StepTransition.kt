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
 * sentence reads as one page being rewritten rather than screens sliding past. Only a change of
 * [stepOf] fades; any other change to [state] redraws in place.
 */
@Composable
public fun <S> StepTransition(
    state: S,
    stepOf: (S) -> Any?,
    modifier: Modifier = Modifier,
    content: @Composable (S) -> Unit,
) {
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        contentKey = stepOf,
        transitionSpec = {
            fadeIn(tween(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS)) togetherWith fadeOut(tween(FADE_OUT_MILLIS))
        },
        label = "step",
    ) { shown -> content(shown) }
}
