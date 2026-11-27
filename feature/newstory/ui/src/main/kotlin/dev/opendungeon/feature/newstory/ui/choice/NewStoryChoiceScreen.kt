package dev.opendungeon.feature.newstory.ui.choice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons
import dev.opendungeon.core.designsystem.component.GhostIconButton
import dev.opendungeon.core.ui.CollectEffects
import dev.opendungeon.feature.newstory.ui.R
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun NewStoryChoiceRoute(
    onSeedPath: () -> Unit,
    onCustomPath: () -> Unit,
    onLeave: () -> Unit,
    viewModel: NewStoryChoiceViewModel = metroViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onIntent(ChoiceIntent.Open) }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ChoiceEffect.OpenSeeded -> onSeedPath()
            ChoiceEffect.OpenCustom -> onCustomPath()
            ChoiceEffect.Leave -> onLeave()
        }
    }
    NewStoryChoiceScreen(state, viewModel::onIntent)
}

/** Stateless: the two paths as two rows, the fast one first. */
@Composable
fun NewStoryChoiceScreen(state: ChoiceUiState, onIntent: (ChoiceIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    Column(modifier.fillMaxSize().background(colors.paper).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostIconButton(PaperIcons.Back, stringResource(R.string.wizard_back), onClick = {
                onIntent(ChoiceIntent.Back)
            })
        }
        Text(
            stringResource(R.string.choice_title),
            style = Paper.type.prompt,
            color = colors.ink,
            modifier = Modifier.padding(start = 28.dp, top = 56.dp, bottom = 28.dp).semantics { heading() },
        )
        HorizontalDivider(color = colors.hairline)
        Path(
            name = stringResource(R.string.choice_seeded),
            note = stringResource(R.string.choice_seeded_note),
            onClick = { onIntent(ChoiceIntent.Seeded) },
        )
        Path(
            name = stringResource(R.string.choice_custom),
            note = stringResource(
                if (state.customResumes) R.string.choice_custom_resume else R.string.choice_custom_new,
            ),
            onClick = { onIntent(ChoiceIntent.Custom) },
        )
    }
}

/** One path: a large name and one line on what it does, the whole row one button. */
@Composable
private fun Path(name: String, note: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 28.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(name, style = Paper.type.cardName.copy(fontSize = 34.sp, lineHeight = 1.1.em), color = colors.ink)
            Text(note, style = Paper.type.button, color = colors.muted)
        }
        HorizontalDivider(color = colors.hairline)
    }
}
