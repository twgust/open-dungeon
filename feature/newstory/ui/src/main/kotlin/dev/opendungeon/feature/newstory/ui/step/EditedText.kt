package dev.opendungeon.feature.newstory.ui.step

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop

/**
 * Text the player is typing stays in the field, seeded once from [initial]; each change is reported
 * through [onEdit] so the draft keeps up. The draft never writes back into a field being typed in.
 */
@Composable
internal fun rememberEditedText(initial: String, onEdit: (String) -> Unit): TextFieldState {
    val state = rememberTextFieldState(initial)
    val latestOnEdit by rememberUpdatedState(onEdit)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.drop(1).collect { latestOnEdit(it) }
    }
    return state
}
