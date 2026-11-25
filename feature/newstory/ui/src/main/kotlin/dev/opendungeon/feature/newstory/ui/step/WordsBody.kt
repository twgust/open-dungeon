package dev.opendungeon.feature.newstory.ui.step

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.component.ChoicePill
import dev.opendungeon.feature.newstory.domain.Option
import dev.opendungeon.feature.newstory.ui.StepBodyUi
import dev.opendungeon.feature.newstory.ui.WizardIntent

@Composable
internal fun WordsBody(body: StepBodyUi.Words, onIntent: (WizardIntent) -> Unit) {
    PillRow(body.options, onIntent)
}

/** Pills centred and wrapping, the player's own words in italics. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PillRow(
    options: List<Option>,
    onIntent: (WizardIntent) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = Paper.type.pill,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { option ->
            ChoicePill(
                text = option.text,
                picked = option.picked,
                onClick = { onIntent(WizardIntent.Pick(option.choice)) },
                style = if (option.own) style.copy(fontStyle = FontStyle.Italic) else style,
            )
        }
    }
}
