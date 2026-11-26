package dev.opendungeon.feature.newstory.ui.step

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.component.ChoicePill
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.ui.R
import dev.opendungeon.feature.newstory.ui.WizardIntent

@Composable
internal fun LimitsBody(body: StepBody.Limits, onIntent: (WizardIntent) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Dial(
            label = stringResource(R.string.wizard_dial_desire),
            levels = ContentLimits.Desire.entries.map { level ->
                Level(stringResource(level.labelRes()), level == body.limits.desire, Choice.Desire(level))
            },
            onIntent = onIntent,
        )
        Dial(
            label = stringResource(R.string.wizard_dial_violence),
            levels = ContentLimits.Violence.entries.map { level ->
                Level(stringResource(level.labelRes()), level == body.limits.violence, Choice.Violence(level))
            },
            onIntent = onIntent,
        )
        body.limits.note?.let { note ->
            Text(
                stringResource(R.string.wizard_limits_note, note),
                style = Paper.type.own,
                color = Paper.colors.lead,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Dial(label: String, levels: List<Level>, onIntent: (WizardIntent) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Paper.type.label.copy(fontSize = Paper.type.caption.fontSize), color = Paper.colors.muted, modifier = Modifier.semantics { heading() })
        FlowRow(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            levels.forEach { level ->
                ChoicePill(level.text, picked = level.picked, onClick = { onIntent(WizardIntent.Pick(level.choice)) })
            }
        }
    }
}

private class Level(val text: String, val picked: Boolean, val choice: Choice)
