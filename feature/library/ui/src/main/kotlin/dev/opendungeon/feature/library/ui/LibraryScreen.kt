package dev.opendungeon.feature.library.ui

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons
import dev.opendungeon.core.designsystem.component.GhostIconButton
import dev.opendungeon.core.designsystem.component.GhostTextButton
import dev.opendungeon.core.designsystem.component.InkButton
import dev.opendungeon.core.designsystem.component.PaperField
import dev.opendungeon.core.designsystem.component.PaperNotices
import dev.opendungeon.core.ui.CollectEffects
import dev.opendungeon.core.ui.rememberEditedText
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StorySummary
import dev.opendungeon.feature.library.domain.LibraryState
import dev.opendungeon.feature.library.domain.TitleEdit
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun LibraryRoute(
    onOpen: (StoryId) -> Unit,
    onNewStory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = metroViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notices = remember { SnackbarHostState() }
    val failures = failureMessages()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is LibraryEffect.OpenStory -> onOpen(effect.story)
            LibraryEffect.StartStory -> onNewStory()
            is LibraryEffect.ShowFailure -> {
                notices.currentSnackbarData?.dismiss()
                notices.showSnackbar(failures.of(effect.failure))
            }
        }
    }
    LibraryScreen(state.library, notices, viewModel::onIntent, modifier)
}

/**
 * Stateless: the stories straight on the page, the one played last first, a hairline between them. Each
 * opens on a tap; its pen, or a long press, opens its title. New story sits at the bottom, beside a deleted story's Undo.
 */
@Composable
internal fun LibraryScreen(
    library: LibraryState,
    notices: SnackbarHostState,
    onIntent: (LibraryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        // Reading the list takes a moment; an empty page is calmer than a flash of a spinner.
        if (!library.loading) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BAR_ROOM)) {
                items(library.stories, key = { it.id.value }) { story ->
                    Column(Modifier.animateItem()) {
                        StoryRow(story, onIntent)
                        HorizontalDivider(color = Paper.colors.divider)
                    }
                }
            }
        }
        BottomBar(library.undo != null, onIntent, Modifier.align(Alignment.BottomCenter))
        PaperNotices(notices, Modifier.align(Alignment.BottomCenter).padding(bottom = BAR_ROOM))
    }
    library.editing?.let { TitleSheet(it, onIntent) }
}

@Composable
private fun StoryRow(story: StorySummary, onIntent: (LibraryIntent) -> Unit) {
    val colors = Paper.colors
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = stringResource(R.string.library_edit_story),
                onLongClick = { onIntent(LibraryIntent.Edit(story.id)) },
                onClick = { onIntent(LibraryIntent.Open(story.id)) },
            )
            .padding(start = 4.dp, top = 18.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val title = story.title
            Text(
                text = title ?: stringResource(R.string.library_untitled),
                style = titleStyle().copy(fontStyle = if (title == null) FontStyle.Italic else FontStyle.Normal),
                color = if (title == null) colors.placeholder else colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.animateContentSize(),
            )
            Text(story.meta(), style = Paper.type.caption, color = colors.muted, maxLines = 1)
        }
        GhostIconButton(
            PaperIcons.Pen,
            stringResource(R.string.library_edit_story),
            onClick = { onIntent(LibraryIntent.Edit(story.id)) },
            iconSize = 18,
        )
    }
}

/** "Gothic, salt-punk · 2 hours ago", or the time alone for a story with no genre. */
@Composable
private fun StorySummary.meta(): String {
    val played = DateUtils.getRelativeTimeSpanString(
        playedAt.toEpochMilliseconds(),
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    val genres = genres.joinToString(", ") { it.text }
    return if (genres.isEmpty()) played else "$genres · $played"
}

@Composable
private fun BottomBar(undoOffered: Boolean, onIntent: (LibraryIntent) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Paper.colors.paper)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = undoOffered,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.library_deleted),
                    style = Paper.type.caption.copy(fontSize = 13.sp),
                    color = Paper.colors.faint,
                    modifier = Modifier.padding(start = 4.dp, end = 2.dp),
                )
                GhostTextButton(stringResource(R.string.library_undo), onClick = {
                    onIntent(LibraryIntent.Undo)
                }, emphasised = true)
            }
        }
        Spacer(Modifier.weight(1f))
        InkButton(stringResource(R.string.library_new_story), onClick = { onIntent(LibraryIntent.NewStory) })
    }
}

/**
 * The story's title on a sheet from the bottom, written straight on it, with the way to delete the story.
 * Dismissing the sheet keeps nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleSheet(edit: TitleEdit, onIntent: (LibraryIntent) -> Unit) {
    val colors = Paper.colors
    val focus = remember { FocusRequester() }
    ModalBottomSheet(
        onDismissRequest = { onIntent(LibraryIntent.CancelEdit) },
        containerColor = colors.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.hairline) },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.library_title_label), style = Paper.type.label, color = colors.muted)
            val text = rememberEditedText(edit.text) { onIntent(LibraryIntent.TypeTitle(it)) }
            PaperField(
                state = text,
                label = stringResource(R.string.library_title_label),
                placeholder = stringResource(R.string.library_untitled),
                style = titleStyle(),
                underlined = true,
                singleLine = true,
                maxLength = edit.maxLength,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                onKeyboardAction = { onIntent(LibraryIntent.SaveTitle) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GhostTextButton(
                    stringResource(R.string.library_delete_story),
                    onClick = { onIntent(LibraryIntent.Delete) },
                )
                Spacer(Modifier.weight(1f))
                SheetButton(stringResource(R.string.library_cancel), onClick = { onIntent(LibraryIntent.CancelEdit) })
                SheetButton(
                    stringResource(R.string.library_save),
                    onClick = { onIntent(LibraryIntent.SaveTitle) },
                    ink = true,
                    enabled = edit.canSave,
                )
            }
        }
    }
    LaunchedFocus(focus)
}

/** Puts the caret in the title as the sheet opens, so renaming is one tap and typing. */
@Composable
private fun LaunchedFocus(focus: FocusRequester) {
    LaunchedEffect(focus) {
        // The field is attached a frame after the sheet is composed.
        withFrameNanos { }
        focus.requestFocus()
    }
}

/** A sans pill for the sheet's answers; [ink] marks the one that acts. Faded while it cannot act. */
@Composable
private fun SheetButton(text: String, onClick: () -> Unit, ink: Boolean = false, enabled: Boolean = true) {
    val colors = Paper.colors
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .border(1.dp, if (ink) colors.ink else colors.hairline, CircleShape)
            .background(if (ink) colors.ink else colors.card, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Paper.type.button, color = if (ink) colors.onInk else colors.ink)
    }
}

@Composable
private fun titleStyle(): TextStyle = Paper.type.cardName.copy(fontSize = 25.sp, lineHeight = 1.15.em)

/** Room under the list for the bottom bar, so the last story is never hidden behind New story. */
private val BAR_ROOM = 84.dp

private const val DISABLED_ALPHA = 0.3f
