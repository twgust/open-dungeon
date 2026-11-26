package dev.opendungeon.feature.newstory.ui.step

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.component.PaperField
import dev.opendungeon.feature.newstory.domain.DraftEdit
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.ui.R
import dev.opendungeon.feature.newstory.ui.WizardIntent

/** The premise, written straight onto the page and editable in place, except while a model writes it. */
@Composable
internal fun PremiseBody(body: StepBody.Premise, writing: Boolean, onIntent: (WizardIntent) -> Unit) {
    val text = rememberEditedText(body.text) { onIntent(WizardIntent.Edit(DraftEdit.Premise(it))) }
    PaperField(
        state = text,
        label = stringResource(R.string.wizard_premise_label),
        placeholder = stringResource(R.string.wizard_premise_hint),
        style = Paper.type.body.copy(fontSize = 24.sp, lineHeight = 1.35.em),
        textAlign = TextAlign.Center,
        readOnly = writing,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The player's optional first line, with a count toward the limit. */
@Composable
internal fun FirstLineBody(body: StepBody.FirstLine, onIntent: (WizardIntent) -> Unit) {
    val text = rememberEditedText(body.text) { onIntent(WizardIntent.Edit(DraftEdit.FirstLine(it))) }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PaperField(
            state = text,
            label = stringResource(R.string.wizard_first_line_label),
            placeholder = stringResource(R.string.wizard_first_line_hint),
            style = Paper.type.body.copy(fontSize = 24.sp, lineHeight = 1.35.em),
            textAlign = TextAlign.Center,
            maxLength = body.maxLength,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.wizard_first_line_count, text.text.length, body.maxLength),
            style = Paper.type.label.copy(fontSize = 11.sp),
            color = Paper.colors.placeholder,
        )
    }
}
