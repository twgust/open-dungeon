package dev.opendungeon.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.opendungeon.core.designsystem.Paper

/** Pills fill with ink when picked; the change is slow enough to read as ink, not as a switch. */
private const val INK_MILLIS = 300

/**
 * A word to pick: a hairline pill that fills with ink when [picked]. Announced as a toggle, so a
 * screen reader says whether it is on.
 */
@Composable
public fun ChoicePill(
    text: String,
    picked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = Paper.type.pill,
    enabled: Boolean = true,
) {
    val colors = Paper.colors
    val ink by animateColorAsState(if (picked) colors.onInk else colors.inkSoft, tween(INK_MILLIS), label = "pill text")
    val fill by animateColorAsState(
        if (picked) colors.ink else colors.paper.copy(alpha = 0f),
        tween(INK_MILLIS),
        label = "pill fill",
    )
    val edge by animateColorAsState(if (picked) colors.ink else colors.hairline, tween(INK_MILLIS), label = "pill edge")
    PillFrame(
        modifier = modifier
            .border(1.dp, edge, CircleShape)
            .background(fill, CircleShape),
        onToggle = onClick,
        picked = picked,
        enabled = enabled,
    ) {
        Text(text = text, style = style, color = ink, textAlign = TextAlign.Center)
    }
}

/** Shape, touch target, press scale and toggle semantics shared by every pill. */
@Composable
internal fun PillFrame(
    onToggle: () -> Unit,
    picked: Boolean?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(start = 16.dp, top = 9.dp, end = 16.dp, bottom = 10.dp),
    content: @Composable () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, tween(PRESS_MILLIS), label = "pill press")
    val action = if (picked == null) {
        Modifier.toggleableAsButton(interactions, enabled, onToggle)
    } else {
        Modifier.toggleable(
            value = picked,
            interactionSource = interactions,
            indication = null,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = { onToggle() },
        )
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.clip(CircleShape)
            .then(action)
            .heightIn(min = 48.dp)
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private const val PRESSED_SCALE = 0.96f
private const val PRESS_MILLIS = 150
