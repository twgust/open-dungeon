package dev.opendungeon.feature.newstory.ui

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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons
import dev.opendungeon.core.designsystem.component.DieButton
import dev.opendungeon.core.designsystem.component.GhostIconButton
import dev.opendungeon.core.designsystem.component.NextArrow
import dev.opendungeon.core.designsystem.component.PaperNotices
import dev.opendungeon.core.designsystem.component.ProgressLine
import dev.opendungeon.core.designsystem.component.SentenceLead
import dev.opendungeon.core.designsystem.component.StepTransition
import dev.opendungeon.core.ui.CollectEffects
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.WizardFailure
import dev.opendungeon.feature.newstory.domain.WizardState
import dev.opendungeon.feature.newstory.ui.step.AuditionsBody
import dev.opendungeon.feature.newstory.ui.step.CastBody
import dev.opendungeon.feature.newstory.ui.step.FirstLineBody
import dev.opendungeon.feature.newstory.ui.step.LimitsBody
import dev.opendungeon.feature.newstory.ui.step.OwnWordsBar
import dev.opendungeon.feature.newstory.ui.step.PremiseBody
import dev.opendungeon.feature.newstory.ui.step.StarringBody
import dev.opendungeon.feature.newstory.ui.step.WordsBody
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun NewStoryRoute(onLeave: () -> Unit, viewModel: StoryWizardViewModel = metroViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notices = remember { SnackbarHostState() }
    val failures = failureMessages()
    LaunchedEffect(viewModel) { viewModel.onIntent(WizardIntent.Open) }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            WizardEffect.Leave -> onLeave()
            is WizardEffect.ShowFailure -> {
                notices.currentSnackbarData?.dismiss()
                notices.showSnackbar(failures.of(effect.failure))
            }
        }
    }
    NewStoryScreen(state = state, notices = notices, onIntent = viewModel::onIntent)
}

/** Stateless: one paper page that rewrites itself for each step. */
@Composable
fun NewStoryScreen(
    state: WizardUiState,
    notices: SnackbarHostState,
    onIntent: (WizardIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    BackHandler { onIntent(WizardIntent.Back) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            when (state) {
                WizardUiState.Loading -> ProgressLine(progress = 0f)
                is WizardUiState.Ready -> StepPage(state.wizard, onIntent)
            }
        }
        PaperNotices(notices, Modifier.align(Alignment.BottomCenter))
    }
}

/** The words for each failure, read while composing so effects can show them later. */
private class FailureMessages(
    private val storageFull: String,
    private val storage: String,
    private val unexpected: String,
) {
    fun of(failure: WizardFailure): String = when (failure) {
        is WizardFailure.Storage -> when (failure.error) {
            StorageError.Full -> storageFull
            is StorageError.Failed -> storage
        }
        is WizardFailure.Unexpected -> unexpected
    }
}

@Composable
private fun failureMessages() = FailureMessages(
    storageFull = stringResource(R.string.wizard_failure_storage_full),
    storage = stringResource(R.string.wizard_failure_storage),
    unexpected = stringResource(R.string.wizard_failure_unexpected),
)

@Composable
private fun ColumnScope.StepPage(state: WizardState, onIntent: (WizardIntent) -> Unit) {
    ProgressLine(progress = state.progress)
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.controls.canGoBack) {
            GhostIconButton(PaperIcons.Back, stringResource(R.string.wizard_back), onClick = {
                onIntent(WizardIntent.Back)
            })
        }
    }
    StepTransition(state = state, stepOf = WizardState::step, modifier = Modifier.weight(1f)) { shown ->
        Column(Modifier.fillMaxSize()) {
            Stage(shown, onIntent, Modifier.weight(1f))
            if (shown.controls.takesOwnWords) {
                OwnWordsBar(step = shown.step, onUse = { onIntent(WizardIntent.Own(it)) })
            }
        }
    }
    BottomBar(state, onIntent)
}

@Composable
private fun Stage(state: WizardState, onIntent: (WizardIntent) -> Unit, modifier: Modifier = Modifier) {
    val copy = stepCopy(state.step)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = copy.topPadding, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        val sentence = remember(state.lead) { state.lead.toAnnotatedString() }
        val lead = if (sentence.isEmpty() && copy.lead != null) AnnotatedString(stringResource(copy.lead)) else sentence
        SentenceLead(lead = lead, prompt = stringResource(copy.prompt))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            when (val body = state.body) {
                is StepBody.Words -> WordsBody(body, onIntent)
                is StepBody.Starring -> StarringBody(body, onIntent)
                is StepBody.Limits -> LimitsBody(body, onIntent)
                is StepBody.Premise -> PremiseBody(body, onIntent)
                is StepBody.Cast -> CastBody(body, onIntent)
                is StepBody.FirstLine -> FirstLineBody(body, onIntent)
                is StepBody.Auditions -> AuditionsBody(body, onIntent)
            }
        }
    }
}

@Composable
private fun BottomBar(state: WizardState, onIntent: (WizardIntent) -> Unit) {
    val controls = state.controls
    Box(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 20.dp)) {
        if (controls.canRoll) {
            DieButton(
                contentDescription = stringResource(stepCopy(state.step).die),
                onClick = { onIntent(WizardIntent.Roll) },
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (!controls.advancesOnPick && state.step.following != null) {
            NextArrow(
                contentDescription = stringResource(R.string.wizard_next),
                onClick = { onIntent(WizardIntent.Next) },
                enabled = controls.canAdvance,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/** The words each step puts around its body. */
internal data class StepCopy(
    val prompt: Int,
    val lead: Int? = null,
    val ownLabel: Int = R.string.wizard_own,
    val ownHint: Int? = null,
    val die: Int = R.string.wizard_die,
    val topPadding: Dp = 56.dp,
)

internal fun stepCopy(step: CreationStep): StepCopy = when (step) {
    CreationStep.Genre -> StepCopy(
        R.string.wizard_prompt_genre,
        lead = R.string.wizard_lead_genre,
        ownHint = R.string.wizard_own_hint_genre,
    )
    CreationStep.Twist -> StepCopy(R.string.wizard_prompt_twist, ownHint = R.string.wizard_own_hint_twist)
    CreationStep.Starring -> StepCopy(R.string.wizard_prompt_starring, topPadding = 32.dp)
    CreationStep.Tone -> StepCopy(R.string.wizard_prompt_tone, ownHint = R.string.wizard_own_hint_tone)
    CreationStep.Setting -> StepCopy(R.string.wizard_prompt_setting, ownHint = R.string.wizard_own_hint_setting)
    CreationStep.Limits -> StepCopy(
        R.string.wizard_prompt_limits,
        ownLabel = R.string.wizard_own_limits,
        ownHint = R.string.wizard_own_hint_limits,
    )
    CreationStep.Premise -> StepCopy(
        R.string.wizard_prompt_premise,
        ownLabel = R.string.wizard_own_premise,
        ownHint = R.string.wizard_own_hint_premise,
        die = R.string.wizard_die_premise,
    )
    CreationStep.Cast -> StepCopy(
        R.string.wizard_prompt_cast,
        ownLabel = R.string.wizard_own_cast,
        die = R.string.wizard_die_cast,
        topPadding = 24.dp,
    )
    CreationStep.FirstLine -> StepCopy(R.string.wizard_prompt_first_line)
    CreationStep.FirstTurn -> StepCopy(R.string.wizard_prompt_first_turn, topPadding = 20.dp)
}
