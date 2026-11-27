package dev.opendungeon.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.opendungeon.core.designsystem.Paper

/**
 * One setting with ordered levels, picked like radio buttons in a single capsule. [levels] holds each
 * level's name, mildest first; the option at index n shows n + 1 [glyph]s, so the row reads as a scale
 * without words on it. The picked level's name sits under the row for sighted readers. Screen readers
 * get the same name on each option instead, announced as a radio button with its place in the row.
 */
@Composable
public fun IntensityRadioRow(
    glyph: ImageVector,
    levels: List<String>,
    picked: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(picked in levels.indices) { "picked $picked is not one of ${levels.size} levels" }
    val colors = Paper.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .clip(CircleShape)
                .border(1.dp, colors.hairline, CircleShape)
                .selectableGroup()
                .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = levels.size) },
        ) {
            levels.forEachIndexed { index, name ->
                if (index > 0) Seam(touchesPick = picked == index || picked == index - 1)
                Option(
                    glyph = glyph,
                    index = index,
                    name = name,
                    selected = index == picked,
                    onClick = { onPick(index) },
                )
            }
        }
        // The options already carry their names, so this line is for the eye only.
        Crossfade(targetState = levels[picked], animationSpec = tween(INK_MILLIS), label = "picked level") { name ->
            Text(
                text = name,
                style = Paper.type.own,
                color = colors.lead,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clearAndSetSemantics {},
            )
        }
    }
}

/** A hairline between two options; it takes the ink beside the picked one so the fill reads as one piece. */
@Composable
private fun Seam(touchesPick: Boolean) {
    val colors = Paper.colors
    val color by animateColorAsState(
        if (touchesPick) colors.ink else colors.hairline,
        tween(INK_MILLIS),
        label = "seam",
    )
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(color),
    )
}

@Composable
private fun RowScope.Option(glyph: ImageVector, index: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Paper.colors
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val fill by animateColorAsState(
        when {
            selected -> colors.ink
            pressed -> colors.track
            else -> Color.Transparent
        },
        tween(INK_MILLIS),
        label = "option fill",
    )
    val tint by animateColorAsState(if (selected) colors.onInk else colors.faint, tween(INK_MILLIS), label = "glyph")
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, tween(PRESS_MILLIS), label = "option press")
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ).semantics {
                contentDescription = name
                collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = index, columnSpan = 1)
            }.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            repeat(index + 1) {
                Icon(glyph, contentDescription = null, tint = tint, modifier = Modifier.size(GLYPH_SIZE))
            }
        }
        if (focused) {
            // No ripple on paper, so keyboard focus needs a mark of its own.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(FOCUS_INSET)
                    .border(2.dp, if (selected) colors.onInk else colors.ink, CircleShape),
            )
        }
    }
}

private val ROW_HEIGHT = 52.dp
private val GLYPH_SIZE = 20.dp
private val FOCUS_INSET = 4.dp
private const val INK_MILLIS = 300
private const val PRESSED_SCALE = 0.96f
private const val PRESS_MILLIS = 150
