package dev.opendungeon.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons

/**
 * A card on the paper: a small-capitals [label], then [content]. With [onToggleEdit] it carries an
 * edit pen that becomes "done" while [editing]. Used for the player's card and for each person in the cast.
 */
@Composable
public fun CharacterCard(
    label: String,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    onToggleEdit: (() -> Unit)? = null,
    editLabel: String = "",
    doneLabel: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Paper.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.hairline, shape)
            .padding(start = 18.dp, top = 6.dp, end = 4.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Paper.type.label, color = colors.muted, modifier = Modifier.weight(1f))
            if (onToggleEdit != null) {
                AnimatedContent(editing, transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                }, label = "card pen") { isEditing ->
                    if (isEditing) {
                        GhostTextButton(doneLabel, onToggleEdit, emphasised = true)
                    } else {
                        GhostIconButton(PaperIcons.Pen, editLabel, onToggleEdit, iconSize = 16)
                    }
                }
            }
        }
        content()
    }
}

/** One labelled line of a card: "WANTS  to clear a debt that isn't yours", with an optional [trailing] die. */
@Composable
public fun CardLine(
    label: String,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 62.dp,
    trailing: (@Composable () -> Unit)? = null,
    value: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = Paper.type.label, color = Paper.colors.muted, modifier = Modifier.width(labelWidth))
        Box(Modifier.weight(1f)) { value() }
        trailing?.invoke()
    }
}

/** A sealed secret: always redacted, never its words. Only story prompts read what it says. */
@Composable
public fun SealedSecret(contentDescription: String, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(3.dp))
            .semantics { this.contentDescription = contentDescription }
            .drawBehind {
                val dark = 6.dp.toPx()
                val light = 2.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawRect(colors.redactDark, Offset(x, 0f), Size(dark, size.height))
                    drawRect(colors.redactLight, Offset(x + dark, 0f), Size(light, size.height))
                    x += dark + light
                }
            },
    )
}
