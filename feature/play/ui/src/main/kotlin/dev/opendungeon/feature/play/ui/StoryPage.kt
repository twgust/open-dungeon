package dev.opendungeon.feature.play.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons
import dev.opendungeon.core.designsystem.component.GhostTextButton
import dev.opendungeon.core.designsystem.component.PaperField
import dev.opendungeon.core.ui.rememberEditedText
import dev.opendungeon.domain.story.Beat
import dev.opendungeon.feature.play.domain.ComposerState
import dev.opendungeon.feature.play.domain.PlayBeat
import dev.opendungeon.feature.play.domain.PlayState
import dev.opendungeon.feature.play.domain.TurnField
import dev.opendungeon.feature.play.domain.Writing

/**
 * The story, straight on the page: beats separated by hairlines, the newest at the bottom, then the
 * player's turn. The list is laid out from the bottom up, so it opens on the newest beat and stays there
 * while a model writes, without scrolling the reader away from an older beat they went back to.
 */
@Composable
internal fun StoryPage(state: PlayState, onIntent: (PlayIntent) -> Unit, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    // Asking for a beat brings the bottom back into view: the reader wants to see it arrive.
    LaunchedEffect(state.writing != null) {
        if (state.writing != null) list.animateScrollToItem(0)
    }
    LazyColumn(
        state = list,
        reverseLayout = true,
        verticalArrangement = Arrangement.Top,
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 20.dp),
        modifier = modifier.fillMaxWidth().fadingTop(),
    ) {
        // Laid out bottom up: the first item here is the last on the page.
        item(key = TURN_KEY, contentType = TURN_KEY) {
            Column(Modifier.fillMaxWidth()) {
                Rule()
                TurnInput(state.turn, onIntent)
            }
        }
        val writing = state.writing
        if (writing != null && !writing.again) {
            item(key = WRITING_KEY, contentType = WRITING_KEY) {
                Column(Modifier.fillMaxWidth()) {
                    Rule()
                    WritingText(writing, onStop = { onIntent(PlayIntent.Stop) })
                }
            }
        }
        beats(state, onIntent)
    }
}

private fun LazyListScope.beats(state: PlayState, onIntent: (PlayIntent) -> Unit) {
    val last = state.beats.lastIndex
    for (index in last downTo 0) {
        val beat = state.beats[index]
        item(key = beat.id.value, contentType = BEAT_KEY) {
            Column(Modifier.fillMaxWidth()) {
                if (index > 0) Rule()
                when (beat.look) {
                    PlayBeat.Look.Editing -> BeatEditor(beat, state.composer, onIntent)
                    PlayBeat.Look.Rewritten -> state.writing?.let {
                        WritingText(it, onStop = { onIntent(PlayIntent.Stop) })
                    }
                    PlayBeat.Look.Plain, PlayBeat.Look.Faded -> BeatText(beat, onTap = {
                        onIntent(PlayIntent.EditBeat(beat.id))
                    })
                }
                if (index == last && state.retryOffered) RetryRow(onIntent)
            }
        }
    }
}

private val BeatStyle: TextStyle
    @Composable get() = Paper.type.body.copy(fontSize = 20.sp, lineHeight = 1.42.em)

/** A turn is the player's voice: italic and a shade softer than the story around it. */
@Composable
private fun styleOf(kind: Beat.Kind): TextStyle =
    if (kind == Beat.Kind.Turn) BeatStyle.copy(fontStyle = FontStyle.Italic) else BeatStyle

/** A beat as text. Tapping it opens it for editing in place. */
@Composable
private fun BeatText(beat: PlayBeat, onTap: () -> Unit) {
    val colors = Paper.colors
    val interactions = remember { MutableInteractionSource() }
    Text(
        text = beat.text,
        style = styleOf(beat.kind),
        color = if (beat.kind == Beat.Kind.Turn) colors.inkSoft else colors.ink,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (beat.look == PlayBeat.Look.Faded) FADED_ALPHA else 1f)
            .clickable(
                interactionSource = interactions,
                indication = null,
                onClickLabel = stringResource(R.string.play_edit_beat),
                role = Role.Button,
                onClick = onTap,
            ),
    )
}

/** A beat open for editing: its words in a field, in place, ready to type. */
@Composable
private fun BeatEditor(beat: PlayBeat, composer: ComposerState?, onIntent: (PlayIntent) -> Unit) {
    val text = rememberEditedText(composer?.text ?: beat.text) { onIntent(PlayIntent.TypeNote(it)) }
    val focus = remember { FocusRequester() }
    var focused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focused) {
            focus.requestFocus()
            focused = true
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.play_editing), style = Paper.type.label, color = Paper.colors.ink)
        PaperField(
            state = text,
            label = stringResource(R.string.play_editing),
            placeholder = stringResource(R.string.play_editing_hint),
            style = styleOf(beat.kind),
            maxLength = composer?.maxLength,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).focusRequester(focus),
        )
    }
}

/**
 * What a model is writing: a pulsing "Writing…" until its first words, then the words with a caret.
 * A tap stops it, keeping what is written.
 */
@Composable
private fun WritingText(writing: Writing, onStop: () -> Unit) {
    val colors = Paper.colors
    val transition = rememberInfiniteTransition(label = "writing")
    val pulse by transition.animateFloat(
        initialValue = WAITING_DIM,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
        label = "writing pulse",
    )
    val words = writing.text
    val modifier = Modifier
        .fillMaxWidth()
        .clickable(
            onClickLabel = stringResource(R.string.play_stop_and_keep),
            role = Role.Button,
            onClick = onStop,
        )
    if (words == null) {
        Text(
            stringResource(R.string.play_writing),
            style = BeatStyle.copy(fontStyle = FontStyle.Italic),
            color = colors.faint.copy(alpha = pulse),
            modifier = modifier,
        )
    } else {
        val shown = buildAnnotatedString {
            append(words)
            appendInlineContent(CARET)
        }
        Text(shown, style = BeatStyle, color = colors.ink, inlineContent = mapOf(CARET to caret()), modifier = modifier)
    }
}

/** A thin blinking caret where the model's next words will appear. */
@Composable
private fun caret(): InlineTextContent {
    val ink = Paper.colors.ink
    return InlineTextContent(Placeholder(4.sp, 1.em, PlaceholderVerticalAlign.TextCenter)) {
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

/** Under the last beat a model wrote: write it again, as it is or for a reason. */
@Composable
private fun RetryRow(onIntent: (PlayIntent) -> Unit) {
    Row(
        Modifier.padding(top = 10.dp).offset(x = (-10).dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WandTextButton(stringResource(R.string.play_retry), onClick = { onIntent(PlayIntent.Retry) })
        GhostTextButton(
            stringResource(R.string.play_retry_with_reason),
            onClick = { onIntent(PlayIntent.OpenReason) },
            emphasised = true,
        )
    }
}

/** A quiet text button with the wand, for a quiet act that has a model write. */
@Composable
private fun WandTextButton(text: String, onClick: () -> Unit) {
    val colors = Paper.colors
    Row(
        Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PaperIcons.Wand, contentDescription = null, tint = colors.ink, modifier = Modifier.size(16.dp))
        Text(text, style = Paper.type.button, color = colors.ink)
    }
}

/** The player's next turn, under the story. It waits, read-only, while a model writes. */
@Composable
private fun TurnInput(turn: TurnField, onIntent: (PlayIntent) -> Unit) {
    val text = rememberEditedText(turn.text) { onIntent(PlayIntent.TypeTurn(it)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.play_you), style = Paper.type.label, color = Paper.colors.faint)
        PaperField(
            state = text,
            label = stringResource(R.string.play_you),
            placeholder = stringResource(R.string.play_you_hint),
            style = BeatStyle.copy(fontStyle = FontStyle.Italic),
            maxLength = turn.maxLength,
            readOnly = !turn.enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .alpha(if (turn.enabled) 1f else FADED_ALPHA),
        )
    }
}

/** The hairline between beats. */
@Composable
private fun Rule() {
    Box(
        Modifier
            .padding(vertical = 22.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Paper.colors.divider),
    )
}

/** The story fades out under the header instead of being cut by it. */
private fun Modifier.fadingTop(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, endY = FADE_DP.dp.toPx()),
            blendMode = BlendMode.DstIn,
        )
    }

private const val TURN_KEY = "turn"
private const val WRITING_KEY = "writing"
private const val BEAT_KEY = "beat"
private const val CARET = "caret"
private const val CARET_HEIGHT = 0.8f
private const val HALF = 0.5f
private const val BLINK_MILLIS = 1000
private const val PULSE_MILLIS = 700
private const val WAITING_DIM = 0.35f
private const val FADED_ALPHA = 0.4f
private const val FADE_DP = 32
