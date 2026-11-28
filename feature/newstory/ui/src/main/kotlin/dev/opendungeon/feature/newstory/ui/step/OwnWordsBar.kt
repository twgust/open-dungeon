package dev.opendungeon.feature.newstory.ui.step

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons
import dev.opendungeon.core.designsystem.component.GhostIconButton
import dev.opendungeon.core.designsystem.component.GhostTextButton
import dev.opendungeon.core.designsystem.component.OwnPill
import dev.opendungeon.core.designsystem.component.PaperField
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.feature.newstory.ui.R
import dev.opendungeon.feature.newstory.ui.stepCopy

/**
 * The dashed "Custom" pill pinned above the bottom bar. Tapped, it opens into a line to write on;
 * "Use" hands the words over and closes it.
 */
@Composable
internal fun OwnWordsBar(step: CreationStep, onUse: (String) -> Unit, modifier: Modifier = Modifier) {
    val copy = stepCopy(step)
    OwnWordsBar(
        label = stringResource(copy.ownLabel),
        hint = copy.ownHint?.let { stringResource(it) },
        key = step,
        onUse = onUse,
        modifier = modifier,
    )
}

/**
 * The same bar for any words of the player's own. It closes, dropping what was typed, whenever [key]
 * changes, since the words were for something else.
 */
@Composable
internal fun OwnWordsBar(
    label: String,
    hint: String?,
    key: Any,
    onUse: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLength: Int? = null,
) {
    var open by rememberSaveable(key) { mutableStateOf(false) }
    val words = rememberTextFieldState()
    LaunchedEffect(key) { words.clearText() }
    val use = {
        if (words.text.isNotBlank()) {
            onUse(words.text.toString())
            words.clearText()
            open = false
        }
    }
    Box(
        modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(open, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "own words") { writing ->
            if (writing) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PaperField(
                        state = words,
                        label = label,
                        placeholder = hint,
                        style = Paper.type.body.copy(fontSize = 21.sp),
                        underlined = true,
                        singleLine = true,
                        maxLength = maxLength,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        onKeyboardAction = use,
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    GhostIconButton(PaperIcons.Close, stringResource(R.string.wizard_own_cancel), onClick = {
                        open =
                            false
                    }, iconSize = 16)
                    GhostTextButton(
                        stringResource(R.string.wizard_own_use),
                        onClick = use,
                        emphasised = true,
                        enabled = words.text.isNotBlank(),
                    )
                }
            } else {
                OwnPill(label, onClick = { open = true })
            }
        }
    }
}
