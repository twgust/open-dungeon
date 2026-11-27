package dev.opendungeon.feature.newstory.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons
import dev.opendungeon.core.designsystem.component.DieButton
import dev.opendungeon.core.designsystem.component.GhostIconButton
import dev.opendungeon.core.designsystem.component.GhostTextButton
import dev.opendungeon.core.designsystem.component.NextArrow
import dev.opendungeon.core.designsystem.component.PaperNotices
import dev.opendungeon.core.designsystem.component.ProgressLine
import dev.opendungeon.core.designsystem.component.SentenceLead
import dev.opendungeon.core.designsystem.component.SheetButton
import dev.opendungeon.core.designsystem.component.StepTransition
import dev.opendungeon.core.ui.CollectEffects
import dev.opendungeon.core.ui.ErrorMessages
import dev.opendungeon.core.ui.errorMessages
import dev.opendungeon.domain.drafting.DraftingError
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.feature.newstory.domain.LeaveAnswer
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.WizardFailure
import dev.opendungeon.feature.newstory.domain.WizardState
import dev.opendungeon.feature.newstory.ui.step.CastBody
import dev.opendungeon.feature.newstory.ui.step.LimitsBody
import dev.opendungeon.feature.newstory.ui.step.ModelLineText
import dev.opendungeon.feature.newstory.ui.step.OpeningBar
import dev.opendungeon.feature.newstory.ui.step.OpeningStage
import dev.opendungeon.feature.newstory.ui.step.OwnWordsBar
import dev.opendungeon.feature.newstory.ui.step.PremiseBody
import dev.opendungeon.feature.newstory.ui.step.StarringBody
import dev.opendungeon.feature.newstory.ui.step.WordsBody
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun NewStoryRoute(
    onLeave: () -> Unit,
    onModels: () -> Unit,
    onBegin: (StoryId) -> Unit,
    viewModel: StoryWizardViewModel = metroViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notices = remember { SnackbarHostState() }
    val failures = failureMessages()
    LaunchedEffect(viewModel) { viewModel.onIntent(WizardIntent.Open) }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            WizardEffect.Leave -> onLeave()
            is WizardEffect.Begin -> onBegin(effect.story)
            WizardEffect.OpenModels -> onModels()
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
internal class FailureMessages(
    private val storageFull: String,
    private val storage: String,
    private val unexpected: String,
    private val noModel: String,
    private val unusable: String,
    private val noProvider: String,
    private val refused: String,
    private val interrupted: String,
    private val schemaMismatch: String,
    private val access: ErrorMessages,
) {
    fun of(failure: WizardFailure): String = when (failure) {
        is WizardFailure.Storage -> when (failure.error) {
            StorageError.Full -> storageFull
            is StorageError.Failed -> storage
        }
        is WizardFailure.Drafting -> of(failure.error)
        is WizardFailure.Unexpected -> unexpected
    }

    private fun of(error: DraftingError): String = when (error) {
        DraftingError.NoModel -> noModel
        DraftingError.Unusable -> unusable
        is DraftingError.Generation -> when (val cause = error.error) {
            is GenerationError.Access -> access.of(cause.error)
            GenerationError.NoProvider -> noProvider
            GenerationError.Refused -> refused
            GenerationError.Interrupted -> interrupted
            is GenerationError.SchemaMismatch -> schemaMismatch
        }
    }
}

@Composable
internal fun failureMessages() = FailureMessages(
    storageFull = stringResource(R.string.wizard_failure_storage_full),
    storage = stringResource(R.string.wizard_failure_storage),
    unexpected = stringResource(R.string.wizard_failure_unexpected),
    noModel = stringResource(R.string.wizard_failure_no_model),
    unusable = stringResource(R.string.wizard_failure_unusable),
    noProvider = stringResource(R.string.wizard_failure_no_provider),
    refused = stringResource(R.string.wizard_failure_refused),
    interrupted = stringResource(R.string.wizard_failure_interrupted),
    schemaMismatch = stringResource(R.string.wizard_failure_schema_mismatch),
    access = errorMessages(),
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
            val body = shown.body
            if (body is StepBody.Opening) {
                OpeningStage(shown, body, onIntent, Modifier.weight(1f))
            } else {
                Stage(shown, onIntent, Modifier.weight(1f))
            }
            if (shown.controls.takesOwnWords) {
                OwnWordsBar(step = shown.step, onUse = { onIntent(WizardIntent.Own(it)) })
            }
        }
    }
    val body = state.body
    if (body is StepBody.Opening) OpeningBar(state, body, onIntent) else BottomBar(state, onIntent)
    if (state.confirmingLeave) {
        LeaveSheet(note = stepCopy(state.step).leaveNote, onAnswer = { onIntent(WizardIntent.AnswerLeave(it)) })
    }
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
            val writing = state.writer?.writing == true
            when (val body = state.body) {
                is StepBody.Words -> WordsBody(body, onIntent)
                is StepBody.Starring -> StarringBody(body, onIntent)
                is StepBody.Limits -> LimitsBody(body.limits, onPick = { onIntent(WizardIntent.Pick(it)) })
                is StepBody.Premise -> PremiseBody(body, writing, onIntent)
                is StepBody.Cast -> CastBody(body, writing, onIntent)
                // The opening lays out its own page, around a text that fills it.
                is StepBody.Opening -> Unit
            }
        }
        state.writer?.let { ModelLineText(it, onChoose = { onIntent(WizardIntent.ChooseModel) }) }
    }
}

/**
 * Leaving a step a model is writing stops it, so the player says so first, on a sheet from the bottom.
 * Dismissing the sheet is staying.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LeaveSheet(note: Int?, onAnswer: (LeaveAnswer) -> Unit) {
    val colors = Paper.colors
    ModalBottomSheet(
        onDismissRequest = { onAnswer(LeaveAnswer.Stay) },
        containerColor = colors.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.hairline) },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.wizard_leave_title),
                style = Paper.type.cardName.copy(fontSize = 30.sp, lineHeight = 1.1.em),
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            note?.let { Text(stringResource(it), style = Paper.type.button, color = colors.muted) }
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                SheetButton(stringResource(R.string.wizard_stay), onClick = { onAnswer(LeaveAnswer.Stay) })
                SheetButton(
                    stringResource(R.string.wizard_leave),
                    onClick = { onAnswer(LeaveAnswer.Leave) },
                    ink = true,
                )
            }
        }
    }
}

@Composable
private fun BottomBar(state: WizardState, onIntent: (WizardIntent) -> Unit) {
    val controls = state.controls
    Box(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 20.dp)) {
        if (state.writer?.writing == true) {
            GhostTextButton(
                stringResource(R.string.wizard_stop),
                onClick = { onIntent(WizardIntent.Stop) },
                emphasised = true,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (controls.canRoll) {
            DieButton(
                contentDescription = stringResource(stepCopy(state.step).die),
                onClick = { onIntent(WizardIntent.Roll()) },
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (!controls.advancesOnPick && !controls.begins) {
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
    /** What leaving while a model writes costs, when the sheet's question alone does not say it. */
    val leaveNote: Int? = null,
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
    CreationStep.Opening -> StepCopy(R.string.wizard_prompt_opening, leaveNote = R.string.wizard_leave_note_opening)
}
