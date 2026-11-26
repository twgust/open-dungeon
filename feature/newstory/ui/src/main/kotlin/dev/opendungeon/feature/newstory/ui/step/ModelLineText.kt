package dev.opendungeon.feature.newstory.ui.step

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.feature.newstory.domain.ModelLine
import dev.opendungeon.feature.newstory.ui.R

/**
 * The quiet line under a step a model writes: which model, or that one is writing now. Tapped while
 * idle, it opens the models to choose another.
 */
@Composable
internal fun ModelLineText(line: ModelLine, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    val model = line.model
    val text = when {
        model == null -> stringResource(R.string.wizard_model_none)
        line.writing -> stringResource(R.string.wizard_model_writing, model)
        else -> stringResource(R.string.wizard_model_idle, model)
    }
    Text(
        text = text,
        style = Paper.type.caption,
        color = if (model == null) Paper.colors.ink else Paper.colors.muted,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = !line.writing, role = Role.Button, onClick = onChoose)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    )
}
