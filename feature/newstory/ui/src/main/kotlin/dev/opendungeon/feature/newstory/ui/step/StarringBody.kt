package dev.opendungeon.feature.newstory.ui.step

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.component.CardLine
import dev.opendungeon.core.designsystem.component.CharacterCard
import dev.opendungeon.core.designsystem.component.LineDieButton
import dev.opendungeon.core.designsystem.component.PaperField
import dev.opendungeon.core.ui.rememberEditedText
import dev.opendungeon.feature.newstory.domain.DraftEdit
import dev.opendungeon.feature.newstory.domain.ShownLine
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.ui.R
import dev.opendungeon.feature.newstory.ui.WizardIntent

@Composable
internal fun StarringBody(body: StepBody.Starring, onIntent: (WizardIntent) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // The card leads once dealt: the roles under it run long, and the card is what the player edits.
        AnimatedVisibility(visible = body.you != null, enter = fadeIn()) {
            body.you?.let { you -> YouCard(you.lines, onIntent) }
        }
        PillGroups(body.roles, onIntent)
    }
}

@Composable
private fun YouCard(lines: List<ShownLine>, onIntent: (WizardIntent) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    CharacterCard(
        label = stringResource(R.string.wizard_you),
        editing = editing,
        onToggleEdit = { editing = !editing },
        editLabel = stringResource(R.string.wizard_edit),
        doneLabel = stringResource(R.string.wizard_done),
        modifier = Modifier.fillMaxWidth(),
    ) {
        lines.forEach { line ->
            val label = stringResource(line.line.labelRes())
            CardLine(
                label = label,
                trailing = {
                    LineDieButton(stringResource(R.string.wizard_reroll_line, label), onClick = {
                        onIntent(WizardIntent.Roll(line.line))
                    })
                },
            ) {
                if (editing) {
                    val text =
                        rememberEditedText(line.text) { onIntent(WizardIntent.Edit(DraftEdit.YouLine(line.line, it))) }
                    PaperField(text, label = label, underlined = true, singleLine = true)
                } else {
                    Text(line.text, style = Paper.type.body, color = Paper.colors.ink)
                }
            }
        }
    }
}
