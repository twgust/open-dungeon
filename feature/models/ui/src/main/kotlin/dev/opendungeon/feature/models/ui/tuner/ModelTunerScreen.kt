package dev.opendungeon.feature.models.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperTheme
import dev.opendungeon.core.designsystem.component.GhostTextButton
import dev.opendungeon.core.designsystem.component.PaperNotices
import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DefaultParameters
import dev.opendungeon.core.model.ModelEndpoint
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelParameter
import dev.opendungeon.core.model.ModelPricing
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.core.ui.CollectEffects
import dev.opendungeon.core.ui.errorMessages
import dev.opendungeon.feature.models.domain.ParameterSheet
import dev.opendungeon.feature.models.domain.Providers
import dev.opendungeon.feature.models.domain.TunerState
import dev.opendungeon.feature.models.ui.BusyLine
import dev.opendungeon.feature.models.ui.PageHeader
import dev.opendungeon.feature.models.ui.R
import dev.opendungeon.feature.models.ui.SectionLabel
import dev.opendungeon.feature.models.ui.contextLabel
import dev.opendungeon.feature.models.ui.label
import dev.opendungeon.feature.models.ui.sliderValue
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun ModelTunerRoute(onBack: () -> Unit, onTry: () -> Unit, viewModel: ModelTunerViewModel = metroViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notices = remember { SnackbarHostState() }
    val messages = errorMessages()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is TunerEffect.ShowFailure -> {
                notices.currentSnackbarData?.dismiss()
                notices.showSnackbar(messages.of(effect.error))
            }
        }
    }
    ModelTunerScreen(state = state, notices = notices, onIntent = viewModel::onIntent, onBack = onBack, onTry = onTry)
}

/** Stateless: the chosen model's parameters, generated from its own list, and the providers serving it. */
@Composable
fun ModelTunerScreen(
    state: TunerState,
    notices: SnackbarHostState,
    onIntent: (TunerIntent) -> Unit,
    onBack: () -> Unit,
    onTry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Paper.colors.paper).safeDrawingPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            when (state) {
                TunerState.Loading -> {
                    BusyLine()
                    PageHeader(title = "", onBack = onBack)
                }
                TunerState.NoModel -> {
                    Spacer(Modifier.height(2.dp))
                    PageHeader(title = stringResource(R.string.tuner_no_model), onBack = onBack)
                }
                is TunerState.Ready -> {
                    Spacer(Modifier.height(2.dp))
                    PageHeader(title = state.model.name, subtitle = state.model.id.value, onBack = onBack) {
                        GhostTextButton(stringResource(R.string.tuner_try), onClick = onTry, emphasised = true)
                    }
                    Sheet(state, onIntent)
                }
            }
        }
        PaperNotices(notices, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Sheet(state: TunerState.Ready, onIntent: (TunerIntent) -> Unit) {
    val sheet = state.sheet
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "parameters") {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(stringResource(R.string.tuner_parameters), Modifier.weight(1f))
                GhostTextButton(
                    stringResource(R.string.tuner_reset_all),
                    onClick = { onIntent(TunerIntent.ResetAll) },
                    enabled = state.isTuned,
                )
            }
        }
        if (sheet.rows.isEmpty()) {
            item(key = "no-parameters") { Quiet(stringResource(R.string.tuner_no_parameters)) }
        }
        items(sheet.rows, key = { it.setting.name }) { row ->
            ParameterRowItem(row, onEdit = { onIntent(TunerIntent.Edit(it)) })
        }
        if (sheet.capabilities.isNotEmpty()) {
            item(key = "capabilities") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    SectionLabel(stringResource(R.string.tuner_capabilities))
                    Text(
                        sheet.capabilities.joinToString(" · "),
                        style = Paper.type.caption,
                        color = Paper.colors.muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        providers(state.providers, onRetry = { onIntent(TunerIntent.RetryProviders) })
    }
}

private fun LazyListScope.providers(providers: Providers, onRetry: () -> Unit) {
    item(key = "providers") {
        SectionLabel(
            stringResource(R.string.tuner_providers),
            Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    when (providers) {
        Providers.Loading -> item(key = "providers-loading") { BusyLine(Modifier.padding(horizontal = 20.dp)) }
        is Providers.Failed -> item(key = "providers-failed") {
            Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    errorMessages().of(providers.error),
                    style = Paper.type.caption,
                    color = Paper.colors.ink,
                    modifier = Modifier.weight(1f),
                )
                GhostTextButton(stringResource(R.string.tuner_retry), onClick = onRetry, emphasised = true)
            }
        }
        is Providers.Loaded -> items(providers.endpoints, key = { "provider-${it.tag}" }) { endpoint ->
            ProviderRow(endpoint)
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Paper.colors.divider)
        }
    }
}

/** One provider: who, at what precision, context and price, how reliable lately, and what it accepts. */
@Composable
private fun ProviderRow(endpoint: ModelEndpoint) {
    val colors = Paper.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(endpoint.providerName, style = Paper.type.cardText, color = colors.ink)
        Text(
            listOfNotNull(
                endpoint.quantization,
                endpoint.contextLength?.let { contextLabel(it) },
                endpoint.pricing.label(),
                endpoint.uptimeLastDay?.let { stringResource(R.string.tuner_uptime, it.sliderValue()) },
            ).joinToString(" · "),
            style = Paper.type.caption,
            color = colors.muted,
        )
        Text(
            endpoint.supportedParameters.joinToString(" · ", transform = ModelParameter::wireName),
            style = Paper.type.caption,
            color = colors.faint,
        )
    }
}

@Composable
private fun Quiet(text: String) {
    Text(
        text,
        style = Paper.type.body,
        color = Paper.colors.muted,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Preview
@Composable
private fun TunerPreview() = PaperTheme {
    val model = AiModel(
        id = ModelId("nous/hermes-4-405b"),
        name = "Hermes 4 405B",
        description = null,
        contextLength = 131_072,
        maxCompletionTokens = 8_192,
        pricing = ModelPricing.Free,
        inputModalities = setOf("text"),
        outputModalities = setOf("text"),
        supportedParameters = listOf("temperature", "top_k", "stop", "tools")
            .mapTo(linkedSetOf(), ModelParameter::fromWireName),
        defaultParameters = DefaultParameters(),
        reasoning = null,
        isModerated = false,
    )
    ModelTunerScreen(
        state = TunerState.Ready(model, ParameterSheet.of(model, Tuning(temperature = 0.8)), Providers.Loading),
        notices = remember { SnackbarHostState() },
        onIntent = {},
        onBack = {},
        onTry = {},
    )
}
