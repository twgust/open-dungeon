package dev.opendungeon.feature.newstory.ui.step

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons
import dev.opendungeon.core.designsystem.component.ActionPill
import dev.opendungeon.core.designsystem.component.BeginButton
import dev.opendungeon.core.designsystem.component.GhostTextButton
import dev.opendungeon.core.designsystem.component.PaperField
import dev.opendungeon.core.designsystem.component.SentenceLead
import dev.opendungeon.core.ui.rememberEditedText
import dev.opendungeon.feature.newstory.domain.DraftEdit
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.WizardState
import dev.opendungeon.feature.newstory.ui.R
import dev.opendungeon.feature.newstory.ui.WizardIntent
import dev.opendungeon.feature.newstory.ui.toAnnotatedString

/**
 * The opening: the whole story above, then one text that fills the page. While a model writes, the text
 * shows its words after the player's, and tapping it stops the writing to edit. Under it, the way back
 * from what the model added, or a failure with a way to try again.
 */
@Composable
internal fun OpeningStage(
    state: WizardState,
    body: StepBody.Opening,
    onIntent: (WizardIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tapping the text to stop is tapping it to edit, so the field it turns back into takes the focus.
    var editAfterStop by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        val lead = remember(state.lead) { state.lead.toAnnotatedString() }
        SentenceLead(
            lead = lead,
            prompt = stringResource(R.string.wizard_prompt_opening),
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 8.dp),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (body.failed) FailureNotice(onRetry = { onIntent(WizardIntent.Roll()) })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val writing = body.writing
                if (writing == null) {
                    OpeningField(body, focus = editAfterStop, onFocus = { editAfterStop = false }, onIntent)
                } else {
                    WritingText(body.text, writing, onStop = {
                        editAfterStop = true
                        onIntent(WizardIntent.Stop)
                    })
                }
            }
        }
        AfterWriting(state, body, onIntent)
    }
}

/** The wand, or Stop while the model writes, and Begin. */
@Composable
internal fun OpeningBar(state: WizardState, body: StepBody.Opening, onIntent: (WizardIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (body.writing != null) {
            ActionPill(
                text = stringResource(R.string.wizard_stop),
                icon = PaperIcons.Stop,
                onClick = { onIntent(WizardIntent.Stop) },
                running = true,
            )
        } else {
            ActionPill(
                text = stringResource(body.wand.label()),
                icon = PaperIcons.Wand,
                onClick = { onIntent(WizardIntent.Roll()) },
                enabled = state.controls.canRoll,
            )
        }
        BeginButton(
            text = stringResource(R.string.wizard_begin),
            onClick = { onIntent(WizardIntent.Begin) },
            enabled = state.controls.canAdvance,
        )
    }
}

private fun StepBody.Opening.Wand.label(): Int = when (this) {
    StepBody.Opening.Wand.WriteForMe -> R.string.wizard_opening_write
    StepBody.Opening.Wand.ContinueMyText -> R.string.wizard_opening_continue
}

private val OpeningTextStyle
    @Composable get() = Paper.type.body.copy(fontSize = 20.sp, lineHeight = 1.42.em)

@Composable
private fun OpeningField(
    body: StepBody.Opening,
    focus: Boolean,
    onFocus: () -> Unit,
    onIntent: (WizardIntent) -> Unit,
) {
    val text = rememberEditedText(body.text) { onIntent(WizardIntent.Edit(DraftEdit.Opening(it))) }
    val focusRequester = remember { FocusRequester() }
    val latestOnFocus by rememberUpdatedState(onFocus)
    LaunchedEffect(focus) {
        if (focus) {
            focusRequester.requestFocus()
            latestOnFocus()
        }
    }
    PaperField(
        state = text,
        label = stringResource(R.string.wizard_opening_label),
        placeholder = stringResource(R.string.wizard_opening_hint),
        style = OpeningTextStyle,
        maxLength = body.maxLength,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).padding(bottom = 24.dp),
    )
}

/**
 * The text while a model writes: the player's words, then the model's in the lead's grey with a caret,
 * or a pulsing "Writing…" before its first word. It follows the newest words, and a tap stops it.
 */
@Composable
private fun WritingText(text: String, writing: StepBody.Opening.Writing, onStop: () -> Unit) {
    val colors = Paper.colors
    val scroll = rememberScrollState()
    val waitingLabel = stringResource(R.string.wizard_opening_writing)
    val pulse by rememberInfiniteTransition(label = "waiting").animateFloat(
        initialValue = WAITING_DIM,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
        label = "waiting pulse",
    )
    val added = (writing as? StepBody.Opening.Writing.Adding)?.text.orEmpty()
    LaunchedEffect(added.length) { scroll.animateScrollTo(scroll.maxValue) }
    val shown = buildAnnotatedString {
        append(text)
        when (writing) {
            is StepBody.Opening.Writing.Adding -> {
                withStyle(SpanStyle(color = colors.lead)) { append(writing.text) }
                appendInlineContent(CARET)
            }
            StepBody.Opening.Writing.Waiting -> withStyle(
                SpanStyle(color = colors.faint.copy(alpha = pulse), fontStyle = FontStyle.Italic),
            ) {
                if (text.isNotEmpty() && !text.last().isWhitespace()) append(' ')
                append(waitingLabel)
            }
        }
    }
    Text(
        text = shown,
        style = OpeningTextStyle,
        color = colors.ink,
        inlineContent = mapOf(CARET to caret()),
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                onClickLabel = stringResource(R.string.wizard_opening_stop_and_edit),
                role = Role.Button,
                onClick = onStop,
            )
            .verticalScroll(scroll)
            .padding(bottom = 24.dp),
    )
}

/** A thin blinking caret where the model's next words will appear. */
@Composable
private fun caret(): InlineTextContent {
    val ink = Paper.colors.ink
    return InlineTextContent(Placeholder(4.sp, 1.em, PlaceholderVerticalAlign.TextCenter)) {
        // On for half a beat, off for the other half, like a text cursor: a blink, not a fade.
        val beat by rememberInfiniteTransition(label = "caret").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(BLINK_MILLIS, easing = LinearEasing)),
            label = "caret blink",
        )
        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
            Spacer(
                Modifier
                    .width(1.5.dp)
                    .fillMaxHeight(CARET_HEIGHT)
                    .alpha(if (beat < HALF) 1f else 0f)
                    .background(ink),
            )
        }
    }
}

/** The failure, said once and plainly, with the wand's next try beside it. */
@Composable
private fun FailureNotice(onRetry: () -> Unit) {
    val colors = Paper.colors
    Row(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.wizard_opening_failed),
                style = Paper.type.button,
                color = colors.ink,
            )
            Text(stringResource(R.string.wizard_opening_unchanged), style = Paper.type.caption, color = colors.muted)
        }
        GhostTextButton(stringResource(R.string.wizard_opening_retry), onClick = onRetry, emphasised = true)
    }
}

/**
 * The row under the text: after the model added something, Undo, with "Stopped." when it was cut
 * short; with no model chosen, the way to choose one, since the wand cannot write without it.
 */
@Composable
private fun AfterWriting(state: WizardState, body: StepBody.Opening, onIntent: (WizardIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val addition = body.addition
        val writer = state.writer
        when {
            addition != null -> {
                if (addition.stopped) {
                    Text(
                        stringResource(R.string.wizard_opening_stopped),
                        style = Paper.type.caption.copy(fontSize = 13.sp),
                        color = Paper.colors.faint,
                        modifier = Modifier.padding(start = 10.dp, end = 2.dp),
                    )
                }
                GhostTextButton(
                    stringResource(R.string.wizard_opening_undo),
                    onClick = { onIntent(WizardIntent.Undo) },
                    emphasised = true,
                )
            }
            writer != null && writer.model == null ->
                ModelLineText(writer, onChoose = { onIntent(WizardIntent.ChooseModel) })
        }
    }
}

private const val CARET = "caret"
private const val CARET_HEIGHT = 0.8f
private const val HALF = 0.5f
private const val BLINK_MILLIS = 1000
private const val PULSE_MILLIS = 700
private const val WAITING_DIM = 0.35f
