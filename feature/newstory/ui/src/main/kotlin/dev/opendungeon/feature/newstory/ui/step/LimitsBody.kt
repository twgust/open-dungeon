package dev.opendungeon.feature.newstory.ui.step

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons
import dev.opendungeon.core.designsystem.component.IntensityRadioRow
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.ui.R

/** The two scales and the player's own note. Both wizards show it, so it speaks in choices, not intents. */
@Composable
internal fun LimitsBody(limits: ContentLimits, onPick: (Choice) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        val desire = ContentLimits.Desire.entries
        Scale(
            label = stringResource(R.string.wizard_dial_desire),
            glyph = PaperIcons.Chili,
            levels = desire.map { stringResource(it.labelRes()) },
            picked = desire.indexOf(limits.desire),
            onPick = { onPick(Choice.Desire(desire[it])) },
        )
        val violence = ContentLimits.Violence.entries
        Scale(
            label = stringResource(R.string.wizard_dial_violence),
            glyph = PaperIcons.Drop,
            levels = violence.map { stringResource(it.labelRes()) },
            picked = violence.indexOf(limits.violence),
            onPick = { onPick(Choice.Violence(violence[it])) },
        )
        limits.note?.let { note ->
            Text(
                stringResource(R.string.wizard_limits_note, note),
                style = Paper.type.own,
                color = Paper.colors.lead,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One limit as a heading over its levels, mildest first, each drawn as more of [glyph]. */
@Composable
private fun Scale(label: String, glyph: ImageVector, levels: List<String>, picked: Int, onPick: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = Paper.type.label.copy(fontSize = Paper.type.caption.fontSize),
            color = Paper.colors.muted,
            modifier = Modifier.semantics {
                heading()
            },
        )
        IntensityRadioRow(
            glyph = glyph,
            levels = levels,
            picked = picked,
            onPick = onPick,
            modifier = Modifier
                .padding(top = 10.dp)
                .width(SCALE_WIDTH),
        )
    }
}

/** Three options of 88dp each: wide enough for three glyphs and well past the 48dp touch target. */
private val SCALE_WIDTH = 264.dp
