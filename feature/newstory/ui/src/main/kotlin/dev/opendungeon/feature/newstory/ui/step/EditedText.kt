package dev.opendungeon.feature.newstory.ui.step

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop

/**
 * A field that the player types in and the draft can also change, for example when a die lands on the
 * line being edited. What is typed is reported through [onEdit]; when [value] then changes to something
 * the field did not type, the field takes it, so the next keystroke never writes stale text back.
 */
@Composable
internal fun rememberEditedText(value: String, onEdit: (String) -> Unit): TextFieldState {
    val field = rememberTextFieldState(value)
    val echoes = remember { TypedEchoes() }
    val latestOnEdit by rememberUpdatedState(onEdit)
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.drop(1).collect { text ->
            if (echoes.typed(text)) latestOnEdit(text)
        }
    }
    LaunchedEffect(value) {
        echoes.arrived(value, field = field.text.toString())?.let(field::setTextAndPlaceCursorAtEnd)
    }
    return field
}

/**
 * Tells the field's own words coming back from the draft apart from words put there by something else.
 *
 * Typed text reaches the draft and returns a moment later, and the draft can skip some of it when the
 * player types faster than it updates, so an arriving value that matches anything typed since the last
 * one is the field's own echo. Anything else is a change the field has to take.
 */
internal class TypedEchoes {
    private val unconfirmed = ArrayDeque<String>()
    private var placed: String? = null

    /** Records what the field now holds. Returns whether to report it: text the draft placed is not news to it. */
    fun typed(text: String): Boolean {
        if (text == placed) {
            placed = null
            return false
        }
        placed = null
        unconfirmed.addLast(text)
        return true
    }

    /** Returns the text the field must take for [value] to show, or `null` when the field already agrees. */
    fun arrived(value: String, field: String): String? {
        val echo = unconfirmed.indexOf(value)
        if (echo >= 0) {
            repeat(echo + 1) { unconfirmed.removeFirst() }
            return null
        }
        unconfirmed.clear()
        if (value == field) return null
        placed = value
        return value
    }
}
