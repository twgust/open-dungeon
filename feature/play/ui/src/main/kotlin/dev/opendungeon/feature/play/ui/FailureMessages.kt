package dev.opendungeon.feature.play.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.opendungeon.core.ui.ErrorMessages
import dev.opendungeon.core.ui.errorMessages
import dev.opendungeon.domain.drafting.DraftingError
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.feature.play.domain.PlayFailure

/** The words for each failure, read while composing so effects can show them later. */
internal class FailureMessages(
    private val storageFull: String,
    private val storage: String,
    private val unexpected: String,
    private val readFull: String,
    private val read: String,
    private val noModel: String,
    private val unusable: String,
    private val noProvider: String,
    private val refused: String,
    private val interrupted: String,
    private val schemaMismatch: String,
    private val access: ErrorMessages,
) {
    /** A change that could not be kept, for a notice. */
    fun of(failure: PlayFailure): String = when (failure) {
        is PlayFailure.Storage -> when (failure.error) {
            StorageError.Full -> storageFull
            is StorageError.Failed -> storage
        }
        is PlayFailure.Unexpected -> unexpected
    }

    /** Why the story could not be read. */
    fun of(error: StorageError): String = when (error) {
        StorageError.Full -> readFull
        is StorageError.Failed -> read
    }

    /** Why a model could not write, as a short sentence. */
    fun of(error: DraftingError): String = when (error) {
        DraftingError.NoModel -> noModel
        DraftingError.Unusable -> unusable
        is DraftingError.Generation -> when (val cause = error.error) {
            is GenerationError.Access -> access.of(cause.error) + "."
            GenerationError.NoProvider -> noProvider
            GenerationError.Refused -> refused
            GenerationError.Interrupted -> interrupted
            is GenerationError.SchemaMismatch -> schemaMismatch
        }
    }
}

@Composable
internal fun failureMessages() = FailureMessages(
    storageFull = stringResource(R.string.play_failure_storage_full),
    storage = stringResource(R.string.play_failure_storage),
    unexpected = stringResource(R.string.play_failure_unexpected),
    readFull = stringResource(R.string.play_cause_storage_full),
    read = stringResource(R.string.play_cause_storage),
    noModel = stringResource(R.string.play_cause_no_model),
    unusable = stringResource(R.string.play_cause_unusable),
    noProvider = stringResource(R.string.play_cause_no_provider),
    refused = stringResource(R.string.play_cause_refused),
    interrupted = stringResource(R.string.play_cause_interrupted),
    schemaMismatch = stringResource(R.string.play_cause_schema_mismatch),
    access = errorMessages(),
)
