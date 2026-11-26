package dev.opendungeon.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLengthTrim
import androidx.compose.foundation.text.input.then
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.opendungeon.core.designsystem.Paper

/**
 * Writing straight onto the page: serif text with no box. With [underlined] it gets a hairline that
 * turns to ink while focused, for short fields on cards and in the own-words bar. A [readOnly] field
 * shows text that something else is writing, such as a model mid-sentence.
 */
@Composable
public fun PaperField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    style: TextStyle = Paper.type.body,
    textAlign: TextAlign = TextAlign.Start,
    underlined: Boolean = false,
    singleLine: Boolean = false,
    maxLength: Int? = null,
    inputTransformation: InputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: (() -> Unit)? = null,
    readOnly: Boolean = false,
) {
    val colors = Paper.colors
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val rule = if (focused) colors.ink else colors.hairline
    val limit = maxLength?.let { InputTransformation.maxLengthTrim(it) }
    BasicTextField(
        state = state,
        readOnly = readOnly,
        modifier = modifier
            .semantics { contentDescription = label }
            .then(
                if (underlined) {
                    Modifier.drawBehind {
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                } else {
                    Modifier
                },
            ),
        textStyle = style.copy(color = colors.ink, textAlign = textAlign),
        cursorBrush = SolidColor(colors.ink),
        lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.Default,
        inputTransformation = when {
            limit != null && inputTransformation != null -> inputTransformation.then(limit)
            else -> limit ?: inputTransformation
        },
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction?.let { action -> { action() } },
        interactionSource = interactions,
        decorator = { field ->
            Box(Modifier.padding(top = 2.dp, bottom = if (underlined) 6.dp else 0.dp)) {
                if (state.text.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        style = style.copy(fontStyle = FontStyle.Italic, textAlign = textAlign),
                        color = colors.placeholder,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                field()
            }
        },
    )
}
