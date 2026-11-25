package dev.opendungeon.feature.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.KeyInfo
import dev.opendungeon.core.ui.OpenDungeonTheme
import dev.opendungeon.feature.connect.ConnectState.Phase
import dev.opendungeon.feature.connect.ConnectState.Remote
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.collections.immutable.ImmutableList

@Composable
fun ConnectRoute(viewModel: ConnectViewModel = metroViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConnectScreen(state = state, onIntent = viewModel::onIntent)
}

/** Stateless: renders [state] and reports user actions as [ConnectIntent]s. */
@Composable
fun ConnectScreen(state: ConnectState, onIntent: (ConnectIntent) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            when (val phase = state.phase) {
                Phase.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is Phase.Disconnected -> KeyEntry(isValidating = false, error = phase.error, onIntent = onIntent)
                Phase.Validating -> KeyEntry(isValidating = true, error = null, onIntent = onIntent)
                is Phase.Connected -> Connected(phase, onIntent)
            }
        }
    }
}

@Composable
private fun KeyEntry(isValidating: Boolean, error: DataError?, onIntent: (ConnectIntent) -> Unit) {
    val keyState = rememberTextFieldState()
    val submit = { onIntent(ConnectIntent.Submit(keyState.text.toString())) }
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.connect_explainer), style = MaterialTheme.typography.bodyMedium)
        OutlinedSecureTextField(
            state = keyState,
            label = { Text(stringResource(R.string.connect_key_label)) },
            enabled = !isValidating,
            isError = error != null,
            supportingText = error?.let { { Text(it.message()) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onKeyboardAction = { submit() },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = submit,
            enabled = !isValidating && keyState.text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (isValidating) R.string.connect_validating else R.string.connect_submit))
        }
    }
}

@Composable
private fun Connected(phase: Phase.Connected, onIntent: (ConnectIntent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(phase.key.summary(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onIntent(ConnectIntent.Refresh) }) { Text(stringResource(R.string.connect_refresh)) }
            TextButton(onClick = { onIntent(ConnectIntent.Disconnect) }) { Text(stringResource(R.string.connect_disconnect)) }
        }
        when (val models = phase.models) {
            Remote.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
            is Remote.Failed -> Text(models.error.message(), color = MaterialTheme.colorScheme.error)
            is Remote.Loaded -> ModelList(models.value)
        }
    }
}

@Composable
private fun ModelList(models: ImmutableList<AiModel>) {
    Text(
        pluralStringResource(R.plurals.connect_model_count, models.size, models.size),
        style = MaterialTheme.typography.labelLarge,
    )
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(models, key = { it.id.value }) { model ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(model.name, style = MaterialTheme.typography.titleSmall)
                Text(model.id.value, style = MaterialTheme.typography.bodySmall)
                Text(
                    model.supportedParameters.joinToString { it.wireName },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Remote<KeyInfo>.summary(): String = when (this) {
    Remote.Loading -> stringResource(R.string.connect_connected)
    is Remote.Failed -> error.message()
    is Remote.Loaded -> value.limitRemaining
        ?.let { stringResource(R.string.connect_key_remaining, value.label, it) }
        ?: value.label
}

@Composable
private fun DataError.message(): String = when (this) {
    DataError.MalformedApiKey -> stringResource(R.string.error_malformed_key)
    DataError.MissingApiKey -> stringResource(R.string.error_missing_key)
    DataError.Network -> stringResource(R.string.error_network)
    DataError.UnexpectedResponse -> stringResource(R.string.error_unexpected_response)
    is DataError.Unauthorized -> stringResource(R.string.error_unauthorized)
    is DataError.InsufficientCredits -> stringResource(R.string.error_insufficient_credits)
    is DataError.RateLimited -> stringResource(R.string.error_rate_limited)
    is DataError.Remote -> stringResource(R.string.error_remote, status, message)
}

@Preview
@Composable
private fun KeyEntryPreview() = OpenDungeonTheme {
    ConnectScreen(state = ConnectState(phase = Phase.Disconnected(DataError.Unauthorized(""))), onIntent = {})
}
