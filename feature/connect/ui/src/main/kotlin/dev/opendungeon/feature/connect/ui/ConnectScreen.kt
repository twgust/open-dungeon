package dev.opendungeon.feature.connect.ui

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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opendungeon.core.designsystem.PaperTheme
import dev.opendungeon.core.designsystem.component.PaperNotices
import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.KeyInfo
import dev.opendungeon.core.ui.CollectEffects
import dev.opendungeon.feature.connect.domain.Connection
import dev.opendungeon.feature.connect.domain.Remote
import dev.zacsweers.metrox.viewmodel.metroViewModel
import java.util.Locale

@Composable
fun ConnectRoute(onNewStory: () -> Unit, viewModel: ConnectViewModel = metroViewModel()) {
    val connection by viewModel.state.collectAsStateWithLifecycle()
    val notices = remember { SnackbarHostState() }
    val messages = errorMessages()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ConnectEffect.ShowFailure -> {
                notices.currentSnackbarData?.dismiss()
                notices.showSnackbar(messages.of(effect.error))
            }
        }
    }
    ConnectScreen(connection = connection, notices = notices, onIntent = viewModel::onIntent, onNewStory = onNewStory)
}

/** Stateless: renders [connection] and reports user actions as [ConnectIntent]s. */
@Composable
fun ConnectScreen(
    connection: Connection,
    notices: SnackbarHostState,
    onIntent: (ConnectIntent) -> Unit,
    onNewStory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, snackbarHost = { PaperNotices(notices) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            when (connection) {
                Connection.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is Connection.Disconnected -> KeyEntry(
                    isValidating = false,
                    error = connection.error,
                    onIntent = onIntent,
                )
                Connection.Validating -> KeyEntry(isValidating = true, error = null, onIntent = onIntent)
                is Connection.Connected -> Connected(connection, onIntent, onNewStory)
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
private fun Connected(connected: Connection.Connected, onIntent: (ConnectIntent) -> Unit, onNewStory: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(connected.key.summary(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onIntent(ConnectIntent.Refresh) }) { Text(stringResource(R.string.connect_refresh)) }
            TextButton(
                onClick = { onIntent(ConnectIntent.Disconnect) },
            ) { Text(stringResource(R.string.connect_disconnect)) }
        }
        Button(onClick = onNewStory, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(stringResource(R.string.connect_new_story))
        }
        when (val models = connected.models) {
            Remote.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
            is Remote.Failed -> Text(models.error.message(), color = MaterialTheme.colorScheme.error)
            is Remote.Loaded -> ModelList(models.value)
        }
    }
}

@Composable
private fun ModelList(models: List<AiModel>) {
    Column {
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
}

@Composable
private fun Remote<KeyInfo>.summary(): String = when (this) {
    Remote.Loading -> stringResource(R.string.connect_connected)
    is Remote.Failed -> error.message()
    is Remote.Loaded ->
        value.limitRemaining
            ?.let { stringResource(R.string.connect_key_remaining, value.label, it) }
            ?: value.label
}

@Composable
private fun DataError.message(): String = errorMessages().of(this)

/** The words for each error, read while composing so effects can show them later. */
private class ErrorMessages(
    private val malformedKey: String,
    private val missingKey: String,
    private val network: String,
    private val unexpectedResponse: String,
    private val keyStorage: String,
    private val unauthorized: String,
    private val insufficientCredits: String,
    private val rateLimited: String,
    private val remote: String,
) {
    fun of(error: DataError): String = when (error) {
        DataError.MalformedApiKey -> malformedKey
        DataError.MissingApiKey -> missingKey
        DataError.Network -> network
        DataError.UnexpectedResponse -> unexpectedResponse
        DataError.KeyStorage -> keyStorage
        is DataError.Unauthorized -> unauthorized
        is DataError.InsufficientCredits -> insufficientCredits
        is DataError.RateLimited -> rateLimited
        is DataError.Remote -> remote.format(Locale.ROOT, error.status)
    }
}

@Composable
private fun errorMessages() = ErrorMessages(
    malformedKey = stringResource(R.string.error_malformed_key),
    missingKey = stringResource(R.string.error_missing_key),
    network = stringResource(R.string.error_network),
    unexpectedResponse = stringResource(R.string.error_unexpected_response),
    keyStorage = stringResource(R.string.error_key_storage),
    unauthorized = stringResource(R.string.error_unauthorized),
    insufficientCredits = stringResource(R.string.error_insufficient_credits),
    rateLimited = stringResource(R.string.error_rate_limited),
    remote = stringResource(R.string.error_remote),
)

@Preview
@Composable
private fun KeyEntryPreview() = PaperTheme {
    ConnectScreen(
        connection = Connection.Disconnected(DataError.Unauthorized("")),
        notices = remember { SnackbarHostState() },
        onIntent = {},
        onNewStory = {},
    )
}
