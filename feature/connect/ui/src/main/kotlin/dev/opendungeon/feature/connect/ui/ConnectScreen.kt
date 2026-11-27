package dev.opendungeon.feature.connect.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opendungeon.core.designsystem.PaperTheme
import dev.opendungeon.core.designsystem.component.PaperNotices
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.KeyInfo
import dev.opendungeon.core.ui.CollectEffects
import dev.opendungeon.core.ui.errorMessages
import dev.opendungeon.feature.connect.domain.Connection
import dev.opendungeon.feature.connect.domain.ModelChoice
import dev.opendungeon.feature.connect.domain.Remote
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun ConnectRoute(
    onModels: () -> Unit,
    onTry: () -> Unit,
    onNewStory: () -> Unit,
    library: @Composable (Modifier) -> Unit,
    viewModel: ConnectViewModel = metroViewModel(),
) {
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
    ConnectScreen(
        connection = connection,
        notices = notices,
        onIntent = viewModel::onIntent,
        onModels = onModels,
        onTry = onTry,
        onNewStory = onNewStory,
        library = library,
    )
}

/**
 * Stateless: renders [connection] and reports user actions as [ConnectIntent]s. Once a story exists,
 * home is [library] under the key and the model; until then it offers to start one.
 */
@Composable
fun ConnectScreen(
    connection: Connection,
    notices: SnackbarHostState,
    onIntent: (ConnectIntent) -> Unit,
    onModels: () -> Unit,
    onTry: () -> Unit,
    onNewStory: () -> Unit,
    library: @Composable (Modifier) -> Unit,
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
                is Connection.Connected -> Connected(connection, onIntent, onModels, onTry, onNewStory, library)
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
private fun Connected(
    connected: Connection.Connected,
    onIntent: (ConnectIntent) -> Unit,
    onModels: () -> Unit,
    onTry: () -> Unit,
    onNewStory: () -> Unit,
    library: @Composable (Modifier) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(connected.key.summary(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onIntent(ConnectIntent.Refresh) }) { Text(stringResource(R.string.connect_refresh)) }
            TextButton(
                onClick = { onIntent(ConnectIntent.Disconnect) },
            ) { Text(stringResource(R.string.connect_disconnect)) }
        }
        HorizontalDivider()
        ModelRow(connected.model, onModels)
        HorizontalDivider()
        if (connected.showsLibrary) {
            library(Modifier.weight(1f))
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onTry,
                    enabled = connected.model is ModelChoice.Chosen,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.connect_try)) }
                Button(onClick = onNewStory, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.connect_new_story))
                }
            }
        }
    }
}

/** The model the player writes with; tapping it opens the catalog. */
@Composable
private fun ModelRow(choice: ModelChoice, onModels: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.connect_change_model), onClick = onModels)
            .padding(vertical = 16.dp),
    ) {
        Text(stringResource(R.string.connect_model), style = MaterialTheme.typography.labelMedium)
        when (choice) {
            ModelChoice.Loading -> CircularProgressIndicator(
                Modifier.padding(top = 8.dp).size(20.dp),
                strokeWidth = 2.dp,
            )
            is ModelChoice.Unavailable -> Text(choice.error.message(), color = MaterialTheme.colorScheme.error)
            ModelChoice.None -> Text(
                stringResource(R.string.connect_choose_model),
                style = MaterialTheme.typography.titleMedium,
            )
            is ModelChoice.Chosen -> {
                Text(choice.model.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    choice.model.id.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

@Preview
@Composable
private fun KeyEntryPreview() = PaperTheme {
    ConnectScreen(
        connection = Connection.Disconnected(DataError.Unauthorized("")),
        notices = remember { SnackbarHostState() },
        onIntent = {},
        onModels = {},
        onTry = {},
        onNewStory = {},
        library = {},
    )
}

@Preview
@Composable
private fun ConnectedPreview() = PaperTheme {
    ConnectScreen(
        connection = Connection.Connected(
            key = Remote.Loaded(
                KeyInfo(label = "story key", isFreeTier = false, usage = 1.2, limit = 10.0, limitRemaining = 8.8),
            ),
            model = ModelChoice.None,
        ),
        notices = remember { SnackbarHostState() },
        onIntent = {},
        onModels = {},
        onTry = {},
        onNewStory = {},
        library = {},
    )
}
