package dev.opendungeon.feature.models.ui.trial

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperTheme
import dev.opendungeon.core.designsystem.component.GhostTextButton
import dev.opendungeon.core.designsystem.component.NextArrow
import dev.opendungeon.core.designsystem.component.PaperField
import dev.opendungeon.core.ui.CollectEffects
import dev.opendungeon.core.ui.errorMessages
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.generation.Usage
import dev.opendungeon.feature.models.domain.RunStatus
import dev.opendungeon.feature.models.domain.TryState
import dev.opendungeon.feature.models.ui.BusyLine
import dev.opendungeon.feature.models.ui.PageHeader
import dev.opendungeon.feature.models.ui.R
import dev.opendungeon.feature.models.ui.SectionLabel
import dev.opendungeon.feature.models.ui.cost
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun TryRoute(onLeave: () -> Unit, viewModel: TryViewModel = metroViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            TryEffect.Leave -> onLeave()
        }
    }
    TryScreen(state = state, onIntent = viewModel::onIntent)
}

/** Stateless: a prompt, the reply as it streams, and what it cost. Back goes through the run, which may ask first. */
@Composable
fun TryScreen(state: TryState, onIntent: (TryIntent) -> Unit, modifier: Modifier = Modifier) {
    BackHandler { onIntent(TryIntent.Leave) }
    val streaming = state.status == RunStatus.Streaming
    Box(modifier.fillMaxSize().background(Paper.colors.paper).safeDrawingPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            if (streaming) BusyLine() else Spacer(Modifier.height(2.dp))
            PageHeader(
                title = stringResource(R.string.try_title),
                subtitle = state.model?.name ?: stringResource(R.string.try_no_model).takeIf { state.noModel },
                onBack = { onIntent(TryIntent.Leave) },
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.reasoning.isNotEmpty()) Reasoning(state.reasoning)
                if (state.reply.isNotEmpty()) Text(state.reply, style = Paper.type.body, color = Paper.colors.ink)
                StatusLine(state.status)
            }
            PromptBar(canSend = state.model != null && !streaming, streaming = streaming, onIntent = onIntent)
        }
    }
    if (state.confirmingLeave) LeaveDialog(onIntent)
}

/** The model's thinking, folded away until asked for; it can be long and is not the answer. */
@Composable
private fun Reasoning(text: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        GhostTextButton(stringResource(R.string.try_reasoning), onClick = { open = !open })
        if (open) Text(text, style = Paper.type.caption, color = Paper.colors.muted)
    }
}

@Composable
private fun StatusLine(status: RunStatus) {
    val text = when (status) {
        RunStatus.Idle, RunStatus.Streaming -> null
        RunStatus.Stopped -> stringResource(R.string.try_stopped)
        is RunStatus.Done -> listOfNotNull(
            stringResource(R.string.try_cut_off).takeIf { status.cutOff },
            status.usage?.let { usageLine(it) },
        ).joinToString(" · ").ifEmpty { null }
        is RunStatus.Failed -> status.error.message()
    }
    text?.let {
        Text(
            it,
            style = Paper.type.caption,
            color = if (status is RunStatus.Failed) Paper.colors.ink else Paper.colors.muted,
        )
    }
}

@Composable
private fun usageLine(usage: Usage): String {
    val tokens = usage.reasoningTokens?.let {
        stringResource(R.string.try_usage_reasoning, usage.promptTokens, usage.completionTokens, it)
    } ?: stringResource(R.string.try_usage, usage.promptTokens, usage.completionTokens)
    return listOfNotNull(tokens, usage.cost?.cost()).joinToString(" · ")
}

@Composable
private fun GenerationError.message(): String = when (this) {
    is GenerationError.Access -> errorMessages().of(error)
    GenerationError.NoProvider -> stringResource(R.string.generation_no_provider)
    GenerationError.Refused -> stringResource(R.string.generation_refused)
    GenerationError.Interrupted -> stringResource(R.string.generation_interrupted)
    is GenerationError.SchemaMismatch -> stringResource(R.string.generation_schema_mismatch)
}

@Composable
private fun PromptBar(canSend: Boolean, streaming: Boolean, onIntent: (TryIntent) -> Unit) {
    val prompt = rememberTextFieldState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionLabel(stringResource(R.string.try_prompt))
        Row(verticalAlignment = Alignment.Bottom) {
            PaperField(
                state = prompt,
                label = stringResource(R.string.try_prompt),
                placeholder = stringResource(R.string.try_prompt_placeholder),
                underlined = true,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            if (streaming) {
                GhostTextButton(stringResource(R.string.try_stop), onClick = {
                    onIntent(TryIntent.Stop)
                }, emphasised = true)
            } else {
                NextArrow(
                    contentDescription = stringResource(R.string.try_send),
                    onClick = { onIntent(TryIntent.Send(prompt.text.toString())) },
                    enabled = canSend && prompt.text.isNotBlank(),
                )
            }
        }
    }
}

@Composable
private fun LeaveDialog(onIntent: (TryIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(TryIntent.Stay) },
        title = { Text(stringResource(R.string.try_leave_title), style = Paper.type.cardText) },
        confirmButton = {
            GhostTextButton(stringResource(R.string.try_leave), onClick = {
                onIntent(TryIntent.ConfirmLeave)
            }, emphasised = true)
        },
        dismissButton = { GhostTextButton(stringResource(R.string.try_stay), onClick = { onIntent(TryIntent.Stay) }) },
        containerColor = Paper.colors.card,
    )
}

@Preview
@Composable
private fun TryPreview() = PaperTheme {
    TryScreen(state = TryState(reply = "Rain falls on the salt flats.", status = RunStatus.Stopped), onIntent = {})
}
