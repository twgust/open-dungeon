package dev.opendungeon.feature.play.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons
import dev.opendungeon.core.designsystem.component.ActionPill
import dev.opendungeon.core.designsystem.component.GhostIconButton
import dev.opendungeon.core.designsystem.component.GhostTextButton
import dev.opendungeon.core.designsystem.component.InkButton
import dev.opendungeon.core.designsystem.component.PaperField
import dev.opendungeon.core.ui.rememberEditedText
import dev.opendungeon.feature.play.domain.ComposerState
import dev.opendungeon.feature.play.domain.PlayState
import dev.opendungeon.feature.play.domain.Primary
import dev.opendungeon.feature.play.domain.Refusal
import dev.opendungeon.feature.play.domain.Secondary
import dev.opendungeon.feature.play.domain.Status
import dev.opendungeon.feature.play.domain.SubmitAction

/**
 * A note for the model above the bar: the direction for the next beat, or what was wrong with the last.
 * An edit opens in place of its beat instead, so it never shows here.
 */
@Composable
internal fun NotePanel(composer: ComposerState, onIntent: (PlayIntent) -> Unit) {
    val copy = when (composer.kind) {
        ComposerState.Kind.Guide -> NoteCopy(
            R.string.play_guide_label,
            R.string.play_guide_help,
            R.string.play_guide_hint,
        )
        ComposerState.Kind.Reason -> NoteCopy(
            R.string.play_reason_label,
            R.string.play_reason_help,
            R.string.play_reason_hint,
        )
        ComposerState.Kind.Edit -> return
    }
    val colors = Paper.colors
    // Each kind of note is its own field, so switching never carries one note's words into the other.
    key(composer.kind) {
        val text = rememberEditedText(composer.text) { onIntent(PlayIntent.TypeNote(it)) }
        val focus = remember { FocusRequester() }
        var focused by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (!focused) {
                focus.requestFocus()
                focused = true
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .topRule()
                .padding(start = 28.dp, end = 28.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(copy.label), style = Paper.type.label, color = colors.ink)
            Text(
                stringResource(copy.help),
                style = Paper.type.caption.copy(lineHeight = 1.4.em),
                color = colors.muted,
            )
            PaperField(
                state = text,
                label = stringResource(copy.label),
                placeholder = stringResource(copy.hint),
                style = Paper.type.body.copy(lineHeight = 1.4.em),
                maxLength = composer.maxLength,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(
                    top = 8.dp,
                ).heightIn(min = NOTE_MIN_HEIGHT.dp, max = NOTE_MAX_HEIGHT.dp).focusRequester(focus),
            )
        }
    }
}

private data class NoteCopy(val label: Int, val help: Int, val hint: Int)

/** The direction waiting for the next beat. Tapping it edits it; the cross takes it back. */
@Composable
internal fun GuideLine(guide: String, onIntent: (PlayIntent) -> Unit) {
    val colors = Paper.colors
    Row(
        Modifier.fillMaxWidth().topRule().padding(start = 28.dp, end = 14.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(
                    onClickLabel = stringResource(R.string.play_edit_guide),
                    role = Role.Button,
                    onClick = { onIntent(PlayIntent.OpenGuide) },
                ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.play_guide), style = Paper.type.label, color = colors.muted)
            Text(
                guide,
                style = Paper.type.body.copy(fontSize = 17.sp, fontStyle = FontStyle.Italic),
                color = colors.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GhostIconButton(
            PaperIcons.Close,
            stringResource(R.string.play_remove_guide),
            onClick = { onIntent(PlayIntent.RemoveGuide) },
            iconSize = 13,
        )
    }
}

/** What the line above the bar says, in order of what matters most now. */
private sealed interface Line {
    data class Hint(val reason: Refusal) : Line

    data class Writing(val again: Boolean) : Line

    data class Failed(val failed: Status.Failed) : Line

    data class Changed(val change: Status.Change) : Line

    data object Empty : Line
}

private fun lineOf(state: PlayState, hint: Refusal?): Line {
    if (hint != null) return Line.Hint(hint)
    val status = state.status
    if (status is Status.Writing) return Line.Writing(status.again)
    // An open note or edit has the player's attention; what came before it waits.
    if (state.composer != null) return Line.Empty
    return when (status) {
        null, is Status.Writing -> Line.Empty
        is Status.Failed -> Line.Failed(status)
        is Status.Changed -> Line.Changed(status.change)
    }
}

/** The line above the bar: what is under way, what just changed with the way back, or what failed. */
@Composable
internal fun StatusLine(state: PlayState, hint: Refusal?, failures: FailureMessages, onIntent: (PlayIntent) -> Unit) {
    AnimatedContent(
        targetState = lineOf(state, hint),
        transitionSpec = { fadeIn(tween(FADE_IN_MILLIS)) togetherWith fadeOut(tween(FADE_OUT_MILLIS)) },
        contentKey = { it::class },
        label = "status line",
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = 28.dp, end = 14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.CenterStart,
    ) { line ->
        when (line) {
            is Line.Hint -> Note(
                stringResource(
                    when (line.reason) {
                        Refusal.StopFirst -> R.string.play_stop_first
                        Refusal.FinishEditFirst -> R.string.play_finish_edit_first
                    },
                ),
                emphasised = true,
            )
            is Line.Writing -> WritingNote(line.again)
            is Line.Failed -> FailureNotice(line.failed, failures, onIntent)
            is Line.Changed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Note(stringResource(line.change.label()), modifier = Modifier.padding(end = 2.dp))
                GhostTextButton(stringResource(R.string.play_undo), onClick = {
                    onIntent(PlayIntent.Undo)
                }, emphasised = true)
            }
            Line.Empty -> Unit
        }
    }
}

private fun Status.Change.label(): Int = when (this) {
    Status.Change.Added -> R.string.play_added
    Status.Change.Rewritten -> R.string.play_rewritten
    Status.Change.Stopped -> R.string.play_stopped
    Status.Change.EditSaved -> R.string.play_edit_saved
    Status.Change.BeatDeleted -> R.string.play_beat_deleted
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier, emphasised: Boolean = false) {
    Text(
        text,
        style = Paper.type.caption.copy(fontSize = 13.sp),
        color = if (emphasised) Paper.colors.ink else Paper.colors.faint,
        modifier = modifier,
    )
}

@Composable
private fun WritingNote(again: Boolean) {
    val pulse by rememberInfiniteTransition(label = "writing note").animateFloat(
        initialValue = WAITING_DIM,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
        label = "writing note pulse",
    )
    Text(
        stringResource(if (again) R.string.play_writing_again else R.string.play_writing_next),
        style = Paper.type.caption.copy(fontSize = 13.sp),
        color = Paper.colors.faint.copy(alpha = pulse),
    )
}

/** Writing failed: said once and plainly, why, that nothing changed, and the one thing that helps. */
@Composable
private fun FailureNotice(failed: Status.Failed, failures: FailureMessages, onIntent: (PlayIntent) -> Unit) {
    val colors = Paper.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(if (failed.again) R.string.play_failed_again else R.string.play_failed_next),
                style = Paper.type.button,
                color = colors.ink,
            )
            Text(
                failures.of(failed.error) + " " + stringResource(R.string.play_unchanged),
                style = Paper.type.caption,
                color = colors.muted,
            )
        }
        when (failed.fix) {
            Status.Fix.TryAgain -> GhostTextButton(
                stringResource(R.string.play_try_again),
                onClick = { onIntent(PlayIntent.TryAgain) },
                emphasised = true,
            )
            Status.Fix.ChooseModel -> GhostTextButton(
                stringResource(R.string.play_choose_model),
                onClick = { onIntent(PlayIntent.ChooseModel) },
                emphasised = true,
            )
        }
        GhostIconButton(
            PaperIcons.Close,
            stringResource(R.string.play_dismiss),
            onClick = { onIntent(PlayIntent.DismissFailure) },
            iconSize = 13,
        )
    }
}

/** The bar: the note or Guide on the left, the main act on the right. */
@Composable
internal fun PlayBar(state: PlayState, onIntent: (PlayIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (val secondary = state.secondary) {
            is Secondary.Guide -> ActionPill(
                text = stringResource(
                    if (secondary.replacing) R.string.play_edit_guide else R.string.play_guide_button,
                ),
                icon = null,
                onClick = { onIntent(PlayIntent.OpenGuide) },
                enabled = secondary.enabled,
            )
            Secondary.Cancel -> ActionPill(
                text = stringResource(R.string.play_cancel),
                icon = null,
                onClick = { onIntent(PlayIntent.Cancel) },
            )
        }
        when (val primary = state.primary) {
            is Primary.Advance -> InkButton(
                text = stringResource(if (primary.sends) R.string.play_send else R.string.play_continue),
                icon = PaperIcons.Wand,
                onClick = { onIntent(PlayIntent.Advance) },
            )
            is Primary.Submit -> InkButton(
                text = stringResource(primary.action.label()),
                icon = PaperIcons.Wand.takeIf { primary.action == SubmitAction.Retry },
                onClick = { onIntent(PlayIntent.Submit) },
                enabled = primary.enabled,
            )
            Primary.Stop -> ActionPill(
                text = stringResource(R.string.play_stop),
                icon = PaperIcons.Stop,
                onClick = { onIntent(PlayIntent.Stop) },
                running = true,
            )
        }
    }
}

private fun SubmitAction.label(): Int = when (this) {
    SubmitAction.AddGuide -> R.string.play_add_guide
    SubmitAction.UpdateGuide -> R.string.play_update_guide
    SubmitAction.Retry -> R.string.play_retry
    SubmitAction.Save -> R.string.play_save
    SubmitAction.DeleteBeat -> R.string.play_delete_beat
}

/** A hairline along the top, setting a panel off from the story above it. */
@Composable
private fun Modifier.topRule(): Modifier {
    val color = Paper.colors.divider
    return drawBehind { drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
}

/** Room for a few lines; a longer note scrolls, so the story above keeps some of the page. */
private const val NOTE_MIN_HEIGHT = 56
private const val NOTE_MAX_HEIGHT = 132
private const val FADE_IN_MILLIS = 140
private const val FADE_OUT_MILLIS = 90
private const val PULSE_MILLIS = 700
private const val WAITING_DIM = 0.35f
