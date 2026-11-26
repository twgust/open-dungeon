package dev.opendungeon.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons

/** Disabled controls stay on the page, faded, so the layout never jumps. */
private const val DISABLED_ALPHA = 0.3f

internal fun Modifier.toggleableAsButton(
    interactions: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = interactions,
    indication = null,
    enabled = enabled,
    role = Role.Button,
    onClick = onClick,
)

/** The player's own words: a dashed, italic pill with a pen. */
@Composable
public fun OwnPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = Paper.colors
    PillFrame(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .dashedBorder(colors.dashed, CircleShape),
        onToggle = onClick,
        picked = null,
        enabled = enabled,
        padding = PaddingValues(start = 14.dp, top = 9.dp, end = 18.dp, bottom = 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PaperIcons.Pen, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
            Text(label, style = Paper.type.own, color = colors.muted)
        }
    }
}

/** A quiet 48 dp icon button: back, the dice, a card's pen. */
@Composable
public fun GhostIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Paper.colors.muted,
    iconSize: Int = 20,
) {
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize.dp))
    }
}

/** A quiet text button in the sans: "use", "done". */
@Composable
public fun GhostTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = Paper.colors
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (emphasised) Paper.type.button.copy(fontWeight = FontWeight.Medium) else Paper.type.button,
            color = if (emphasised) colors.ink else colors.muted,
        )
    }
}

/**
 * A hairline pill with an icon that asks for something to be done, such as the wand. [running] fills
 * it with ink, for the one thing that is under way and can be stopped from it.
 */
@Composable
public fun ActionPill(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    running: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = Paper.colors
    val fill = if (running) colors.ink else Color.Transparent
    val content = if (running) colors.onInk else colors.ink
    PillFrame(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .border(1.dp, if (running) colors.ink else colors.hairline, CircleShape)
            .background(fill, CircleShape),
        onToggle = onClick,
        picked = null,
        enabled = enabled,
        padding = PaddingValues(start = 14.dp, top = 12.dp, end = 18.dp, bottom = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Text(text, style = Paper.type.button, color = content)
        }
    }
}

/** The die in the step's bottom bar. */
@Composable
public fun DieButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GhostIconButton(PaperIcons.Die, contentDescription, onClick, modifier, enabled, iconSize = 24)
}

/** The small die beside one line of a card. */
@Composable
public fun LineDieButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GhostIconButton(
        PaperIcons.SmallDie,
        contentDescription,
        onClick,
        modifier,
        tint = Paper.colors.faint,
        iconSize = 18,
    )
}

/** The ink disc that moves to the next step. */
@Composable
public fun NextArrow(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Paper.colors
    Box(
        modifier = modifier
            .size(52.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .background(colors.ink)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(PaperIcons.Next, contentDescription = null, tint = colors.onInk, modifier = Modifier.size(20.dp))
    }
}

/** The last step's ink pill that starts the story. Faded until the story can start. */
@Composable
public fun BeginButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = Paper.colors
    PillFrame(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .border(1.dp, colors.ink, CircleShape)
            .background(colors.ink, CircleShape),
        onToggle = onClick,
        picked = null,
        enabled = enabled,
        padding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text,
            style = TextStyle(fontFamily = Paper.type.pill.fontFamily, fontSize = 21.sp, fontStyle = FontStyle.Italic),
            color = colors.onInk,
        )
    }
}
