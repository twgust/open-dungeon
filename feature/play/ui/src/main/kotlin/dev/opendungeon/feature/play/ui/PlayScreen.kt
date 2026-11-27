package dev.opendungeon.feature.play.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons
import dev.opendungeon.core.designsystem.component.GhostIconButton
import dev.opendungeon.core.designsystem.component.GhostTextButton
import dev.opendungeon.core.designsystem.component.PaperNotices
import dev.opendungeon.core.ui.CollectEffects
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.feature.play.domain.LeaveAnswer
import dev.opendungeon.feature.play.domain.Play
import dev.opendungeon.feature.play.domain.PlayState
import dev.opendungeon.feature.play.domain.Refusal
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.delay

@Composable
fun PlayRoute(story: StoryId, onLeave: () -> Unit, onModels: () -> Unit, viewModel: PlayViewModel = metroViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notices = remember { SnackbarHostState() }
    val failures = failureMessages()
    var hint by remember { mutableStateOf<Hint?>(null) }
    LaunchedEffect(viewModel, story) { viewModel.onIntent(PlayIntent.Open(story)) }
    LaunchedEffect(hint) {
        if (hint != null) {
            delay(HINT_MILLIS)
            hint = null
        }
    }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            PlayEffect.Leave -> onLeave()
            PlayEffect.OpenModels -> onModels()
            // A new hint restarts the wait, even when it says the same thing again.
            is PlayEffect.ShowRefusal -> hint = Hint(effect.reason, serial = (hint?.serial ?: 0) + 1)
            is PlayEffect.ShowFailure -> {
                notices.currentSnackbarData?.dismiss()
                notices.showSnackbar(failures.of(effect.failure))
            }
        }
    }
    PlayScreen(
        story = story,
        state = state,
        hint = hint?.reason,
        notices = notices,
        failures = failures,
        onIntent = viewModel::onIntent,
    )
}

/** A refusal said back on the line above the bar for a moment. */
private data class Hint(val reason: Refusal, val serial: Int)

/** Stateless: the story on one paper page, the newest beat at the bottom, the player's turn under it. */
@Composable
internal fun PlayScreen(
    story: StoryId,
    state: PlayUiState,
    hint: Refusal?,
    notices: SnackbarHostState,
    failures: FailureMessages,
    onIntent: (PlayIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onIntent(PlayIntent.Back) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Paper.colors.paper)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(onBack = { onIntent(PlayIntent.Back) })
            when (val play = state.play) {
                // Reading a story takes a moment; an empty page is calmer than a flash of a spinner.
                Play.Opening -> Unit
                Play.Missing -> MissingPage(onBack = { onIntent(PlayIntent.Back) })
                is Play.Unreadable -> UnreadablePage(
                    cause = failures.of(play.error),
                    onTryAgain = { onIntent(PlayIntent.Open(story)) },
                )
                is Play.Playing -> PlayingPage(play.state, hint, failures, onIntent)
            }
        }
        PaperNotices(notices, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GhostIconButton(PaperIcons.Back, stringResource(R.string.play_back), onClick = onBack)
    }
}

@Composable
private fun PlayingPage(state: PlayState, hint: Refusal?, failures: FailureMessages, onIntent: (PlayIntent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StoryPage(state, onIntent, Modifier.weight(1f))
        state.composer?.let { NotePanel(it, onIntent) }
        state.guide?.let { GuideLine(it, onIntent) }
        StatusLine(state, hint, failures, onIntent)
        PlayBar(state, onIntent)
    }
    if (state.confirmingLeave) LeaveSheet(onIntent)
}

@Composable
private fun MissingPage(onBack: () -> Unit) {
    NoStory(
        title = stringResource(R.string.play_missing_title),
        note = stringResource(R.string.play_missing_note),
        action = stringResource(R.string.play_back),
        onAction = onBack,
    )
}

@Composable
private fun UnreadablePage(cause: String, onTryAgain: () -> Unit) {
    NoStory(
        title = stringResource(R.string.play_unreadable_title),
        note = cause,
        action = stringResource(R.string.play_open_again),
        onAction = onTryAgain,
    )
}

/** The page when there is no story to show, said plainly, with the one thing that helps. */
@Composable
private fun NoStory(title: String, note: String, action: String, onAction: () -> Unit) {
    val colors = Paper.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            style = Paper.type.cardName.copy(fontSize = 30.sp, lineHeight = 1.1.em),
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(note, style = Paper.type.button, color = colors.muted)
        GhostTextButton(action, onClick = onAction, emphasised = true, modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * Leaving while a model writes stops it, so the player says so first, on a sheet from the bottom.
 * Dismissing the sheet is staying.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaveSheet(onIntent: (PlayIntent) -> Unit) {
    val colors = Paper.colors
    ModalBottomSheet(
        onDismissRequest = { onIntent(PlayIntent.AnswerLeave(LeaveAnswer.Stay)) },
        containerColor = colors.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.hairline) },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.play_leave_title),
                style = Paper.type.cardName.copy(fontSize = 30.sp, lineHeight = 1.1.em),
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.play_leave_note), style = Paper.type.button, color = colors.muted)
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                SheetButton(stringResource(R.string.play_stay), onClick = {
                    onIntent(PlayIntent.AnswerLeave(LeaveAnswer.Stay))
                })
                SheetButton(
                    stringResource(R.string.play_leave),
                    onClick = { onIntent(PlayIntent.AnswerLeave(LeaveAnswer.Leave)) },
                    ink = true,
                )
            }
        }
    }
}

/** A sans pill for the sheet's answers; [ink] marks the one that acts. */
@Composable
private fun SheetButton(text: String, onClick: () -> Unit, ink: Boolean = false) {
    val colors = Paper.colors
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .border(1.dp, if (ink) colors.ink else colors.hairline, CircleShape)
            .background(if (ink) colors.ink else colors.card, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Paper.type.button, color = if (ink) colors.onInk else colors.ink)
    }
}

/** Long enough to read a short line, short enough not to linger over the next tap. */
private const val HINT_MILLIS = 2600L
