package dev.opendungeon.feature.library.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.feature.library.domain.LibraryFailure

/** What each failure says: what did not happen, then why when the reason helps. */
@Immutable
internal class FailureMessages(private val rename: String, private val delete: String, private val full: String) {
    fun of(failure: LibraryFailure): String {
        val what = when (failure) {
            is LibraryFailure.Rename -> rename
            is LibraryFailure.Delete -> delete
        }
        return when (failure.error) {
            StorageError.Full -> "$what $full"
            is StorageError.Failed -> what
        }
    }
}

@Composable
internal fun failureMessages(): FailureMessages = FailureMessages(
    rename = stringResource(R.string.library_rename_failed),
    delete = stringResource(R.string.library_delete_failed),
    full = stringResource(R.string.library_storage_full),
)
