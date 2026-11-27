package dev.opendungeon.feature.newstory.ui.seeded

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons
import dev.opendungeon.core.designsystem.component.BeginButton
import dev.opendungeon.core.designsystem.component.DieButton
import dev.opendungeon.core.designsystem.component.GhostIconButton
import dev.opendungeon.core.designsystem.component.GhostTextButton
import dev.opendungeon.core.designsystem.component.NextArrow
import dev.opendungeon.core.designsystem.component.PaperNotices
import dev.opendungeon.core.designsystem.component.ProgressLine
import dev.opendungeon.core.designsystem.component.StepTransition
import dev.opendungeon.core.ui.CollectEffects
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.feature.newstory.domain.seeded.SeededBody
import dev.opendungeon.feature.newstory.domain.seeded.SeededState
import dev.opendungeon.feature.newstory.domain.seeded.SeededStep
import dev.opendungeon.feature.newstory.ui.LeaveSheet
import dev.opendungeon.feature.newstory.ui.R
import dev.opendungeon.feature.newstory.ui.failureMessages
import dev.opendungeon.feature.newstory.ui.step.ModelLineText
import dev.opendungeon.feature.newstory.ui.step.OwnWordsBar
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun SeededRoute(
    onLeave: () -> Unit,
    onModels: () -> Unit,
    onBegin: (StoryId) -> Unit,
    viewModel: SeededViewModel = metroViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notices = remember { SnackbarHostState() }
    val failures = failureMessages()
    LaunchedEffect(viewModel) { viewModel.onIntent(SeededIntent.Open) }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            SeededEffect.Leave -> onLeave()
            is SeededEffect.Begin -> onBegin(effect.story)
            SeededEffect.OpenModels -> onModels()
            is SeededEffect.ShowFailure -> {
                notices.currentSnackbarData?.dismiss()
                notices.showSnackbar(failures.of(effect.failure))
            }
        }
    }
    SeededScreen(state = state, notices = notices, onIntent = viewModel::onIntent)
}

/** Stateless: the same paper page as the custom wizard, rewritten for each seeded step. */
@Composable
fun SeededScreen(
    state: SeededUiState,
    notices: SnackbarHostState,
    onIntent: (SeededIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onIntent(SeededIntent.Back) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Paper.colors.paper)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            when (state) {
                SeededUiState.Loading -> ProgressLine(progress = 0f)
                is SeededUiState.Ready -> StepPage(state.wizard, onIntent)
            }
        }
        PaperNotices(notices, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ColumnScope.StepPage(state: SeededState, onIntent: (SeededIntent) -> Unit) {
    ProgressLine(progress = state.progress)
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GhostIconButton(PaperIcons.Back, stringResource(R.string.wizard_back), onClick = {
            onIntent(SeededIntent.Back)
        })
    }
    StepTransition(state = state, stepOf = SeededState::step, modifier = Modifier.weight(1f)) { shown ->
        Column(Modifier.fillMaxSize()) {
            Stage(shown, onIntent, Modifier.weight(1f))
            if (shown.controls.takesOwnWords) {
                // The limits are the custom wizard's step, so their own words are too.
                OwnWordsBar(step = CreationStep.Limits, onUse = { onIntent(SeededIntent.Own(it)) })
            }
        }
    }
    BottomBar(state, onIntent)
    if (state.confirmingLeave) {
        LeaveSheet(note = R.string.seeded_leave_note, onAnswer = { onIntent(SeededIntent.AnswerLeave(it)) })
    }
}

@Composable
private fun Stage(state: SeededState, onIntent: (SeededIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = state.step.topPadding, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        when (val body = state.body) {
            is SeededBody.Seed -> SeedBody(body, onIntent)
            is SeededBody.People -> PeopleBody(body, onIntent)
            is SeededBody.Limits -> SeededLimitsBody(body, onIntent)
            is SeededBody.Making -> MakingBody(body)
        }
        state.writer?.let { ModelLineText(it, onChoose = { onIntent(SeededIntent.ChooseModel) }) }
    }
}

@Composable
private fun BottomBar(state: SeededState, onIntent: (SeededIntent) -> Unit) {
    val controls = state.controls
    Box(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 20.dp)) {
        if (controls.canStop) {
            GhostTextButton(
                stringResource(R.string.wizard_stop),
                onClick = { onIntent(SeededIntent.Stop) },
                emphasised = true,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (state.step == SeededStep.Seed) {
            DieButton(
                contentDescription = stringResource(R.string.seeded_die),
                onClick = { onIntent(SeededIntent.RollSeed) },
                enabled = controls.canRoll,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        when {
            controls.begins -> BeginButton(
                stringResource(R.string.wizard_begin),
                onClick = { onIntent(SeededIntent.Begin) },
                enabled = controls.canAdvance,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
            state.step != SeededStep.Making -> NextArrow(
                contentDescription = stringResource(R.string.wizard_next),
                onClick = { onIntent(SeededIntent.Next) },
                enabled = controls.canAdvance,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/** The seed stands alone in the middle of the page; the later steps lead with it at the top. */
private val SeededStep.topPadding: Dp
    get() = when (this) {
        SeededStep.Seed -> 96.dp
        SeededStep.People, SeededStep.Limits -> 40.dp
        SeededStep.Making -> 24.dp
    }
