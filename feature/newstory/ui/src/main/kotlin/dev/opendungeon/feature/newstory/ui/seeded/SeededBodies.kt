package dev.opendungeon.feature.newstory.ui.seeded

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons
import dev.opendungeon.core.designsystem.component.GhostIconButton
import dev.opendungeon.core.designsystem.component.PaperField
import dev.opendungeon.core.designsystem.component.SentenceLead
import dev.opendungeon.core.ui.rememberEditedText
import dev.opendungeon.domain.drafting.Seed
import dev.opendungeon.feature.newstory.domain.seeded.Person
import dev.opendungeon.feature.newstory.domain.seeded.SeedPart
import dev.opendungeon.feature.newstory.domain.seeded.SeededBody
import dev.opendungeon.feature.newstory.domain.seeded.Suggestions
import dev.opendungeon.feature.newstory.ui.R
import dev.opendungeon.feature.newstory.ui.step.LimitsBody

/**
 * The seed, large, its blanks empty slots until the player fills them, and under it the words for the
 * picked blank. Each slot and each word is a button of its own, so a screen reader reaches them in turn.
 */
@Composable
internal fun SeedBody(body: SeededBody.Seed, onIntent: (SeededIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    BodyColumn(modifier) {
        Text(
            body.genre.uppercase(),
            style = Paper.type.label,
            color = if (body.replacing) colors.faint else colors.muted,
        )
        Sentence(body, onPick = { onIntent(SeededIntent.PickBlank(it)) })
        body.suggestions?.let { shown ->
            SuggestionColumn(
                shown,
                onFill = { onIntent(SeededIntent.FillBlank(shown.blank, it)) },
                onMore = { onIntent(SeededIntent.MoreWords(shown.blank)) },
            )
        }
    }
}

/** The sentence, wrapped between words like text, with each blank and the words around it kept on one line. */
@Composable
private fun Sentence(body: SeededBody.Seed, onPick: (String) -> Unit) {
    val style = SentenceStyle.copy(color = if (body.replacing) Paper.colors.faint else Paper.colors.ink)
    val space = with(LocalDensity.current) { (SentenceStyle.fontSize * SPACE_EM).toDp() }
    val words = remember(body.parts) { wordsOf(body.parts) }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        words.forEach { word ->
            Row {
                word.forEach { piece ->
                    when (piece) {
                        is SeedPart.Text -> Text(piece.text, style = style, modifier = Modifier.alignByBaseline())
                        is SeedPart.Blank -> Slot(
                            piece,
                            style,
                            enabled = !body.replacing,
                            onClick = { onPick(piece.key) },
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The sentence's pieces grouped into words: runs with no space between them, such as a blank and the full
 * stop after it, which must not be split across lines.
 */
private fun wordsOf(parts: List<SeedPart>): List<List<SeedPart>> {
    val words = mutableListOf<List<SeedPart>>()
    var word = mutableListOf<SeedPart>()

    fun close() {
        if (word.isNotEmpty()) words += word
        word = mutableListOf()
    }
    parts.forEach { part ->
        when (part) {
            is SeedPart.Blank -> word += part
            is SeedPart.Text -> part.text.split(' ').forEachIndexed { index, piece ->
                if (index > 0) close()
                if (piece.isNotEmpty()) word += SeedPart.Text(piece)
            }
        }
    }
    close()
    return words
}

/** One blank: a dashed rule while empty, the player's words once filled, marked while picked. */
@Composable
private fun Slot(
    blank: SeedPart.Blank,
    style: TextStyle,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val kind = stringResource(blank.kind.label)
    val description = "$kind, ${blank.words ?: stringResource(R.string.seeded_blank_empty)}"
    Text(
        text = blank.words ?: "\u00A0",
        style = style,
        textAlign = TextAlign.Center,
        modifier = modifier
            .widthIn(min = if (blank.words == null) EmptySlotWidth else 0.dp)
            .clip(SlotShape)
            .background(if (blank.picked) colors.track else Color.Transparent)
            .drawBehind {
                val stroke = (if (blank.picked) 2.5.dp else 1.5.dp).toPx()
                val y = size.height - stroke / 2
                drawLine(
                    color = if (blank.words == null && !blank.picked) colors.dashed else colors.ink,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke,
                    pathEffect = if (blank.words == null && !blank.picked) Dash else null,
                )
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                selected = blank.picked
            }
            .padding(horizontal = 1.dp),
    )
}

/**
 * The picked blank's words, small and spaced like film credits under its kind, in a short column that
 * scrolls and fades at an edge with more beyond it. The chosen words stand out; the wand asks for more.
 */
@Composable
private fun SuggestionColumn(shown: Suggestions, onFill: (String) -> Unit, onMore: () -> Unit) {
    val colors = Paper.colors
    val scroll = rememberScrollState()
    // New words join the end, so the column rolls on to show them.
    val count = shown.words.size
    var seen by remember(shown.blank) { mutableIntStateOf(count) }
    LaunchedEffect(shown.blank, count) {
        if (count > seen) scroll.animateScrollTo(scroll.maxValue)
        seen = count
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(shown.kind.label).uppercase(),
            style = Paper.type.label,
            color = colors.faint,
            modifier = Modifier.padding(bottom = 6.dp).semantics { heading() },
        )
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = CreditsHeight)
                .fadingEdges(scroll)
                .verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            shown.words.forEach { words ->
                val chosen = words == shown.chosen
                Text(
                    words.uppercase(),
                    style = CreditStyle.copy(fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal),
                    color = when {
                        chosen -> colors.ink
                        shown.pending -> colors.faint
                        else -> colors.muted
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Button) { onFill(words) }
                        .semantics {
                            contentDescription = words
                            selected = chosen
                        }
                        .wrapContentHeight()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        GhostIconButton(
            PaperIcons.Wand,
            stringResource(R.string.seeded_more),
            onClick = onMore,
            enabled = shown.canSuggestMore,
            iconSize = 18,
        )
    }
}

/** Fades the top or bottom edge when the column scrolls beyond it, the way credits roll off a screen. */
private fun Modifier.fadingEdges(scroll: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = FadeHeight.toPx()
        if (scroll.value > 0) {
            drawRect(
                Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, endY = fade),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn,
            )
        }
        if (scroll.value < scroll.maxValue) {
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Black,
                    1f to Color.Transparent,
                    startY = size.height - fade,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - fade),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn,
            )
        }
    }

private val Seed.Kind.label: Int
    get() = when (this) {
        Seed.Kind.Person -> R.string.seeded_kind_person
        Seed.Kind.Thing -> R.string.seeded_kind_thing
        Seed.Kind.Act -> R.string.seeded_kind_act
        Seed.Kind.Place -> R.string.seeded_kind_place
    }

/** The label of the "Custom" pill for a blank of this kind. */
internal val Seed.Kind.ownLabel: Int
    get() = when (this) {
        Seed.Kind.Person -> R.string.seeded_own_person
        Seed.Kind.Thing -> R.string.seeded_own_thing
        Seed.Kind.Act -> R.string.seeded_own_act
        Seed.Kind.Place -> R.string.seeded_own_place
    }

private val SentenceStyle: TextStyle
    @Composable get() = Paper.type.body.copy(fontSize = 32.sp, lineHeight = 1.3.em)

private val CreditStyle: TextStyle
    @Composable get() = Paper.type.caption.copy(fontSize = 13.sp, letterSpacing = 0.16.em, lineHeight = 1.35.em)

/** The gap between words, as a share of the type size: a serif's word space. */
private const val SPACE_EM = 0.26f
private val EmptySlotWidth = 76.dp
private val CreditsHeight = 228.dp
private val FadeHeight = 48.dp
private val SlotShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
private val Dash = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))

/** Two short fields, both optional: who the player is, and who the premise puts across from them. */
@Composable
internal fun PeopleBody(body: SeededBody.People, onIntent: (SeededIntent) -> Unit, modifier: Modifier = Modifier) {
    BodyColumn(modifier) {
        SentenceLead(lead = AnnotatedString(body.premise), prompt = stringResource(R.string.seeded_people_prompt))
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Description(stringResource(R.string.wizard_you), body.you, body.maxLength) {
                onIntent(SeededIntent.Describe(Person.You, it))
            }
            Description(body.counterpart.uppercase(), body.them, body.maxLength) {
                onIntent(SeededIntent.Describe(Person.Them, it))
            }
        }
    }
}

@Composable
private fun Description(label: String, value: String, maxLength: Int, onEdit: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = Paper.type.label, color = Paper.colors.muted)
        PaperField(
            state = rememberEditedText(value, onEdit),
            label = label,
            placeholder = stringResource(R.string.seeded_optional),
            style = Paper.type.body.copy(fontSize = 21.sp),
            underlined = true,
            maxLength = maxLength,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SeededLimitsBody(
    body: SeededBody.Limits,
    onIntent: (SeededIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BodyColumn(modifier) {
        SentenceLead(lead = AnnotatedString(body.premise), prompt = stringResource(R.string.wizard_prompt_limits))
        LimitsBody(body.limits, onPick = { onIntent(SeededIntent.Pick(it)) })
    }
}

/** The story being written: who is in it once cast, then the opening as it streams in. */
@Composable
internal fun MakingBody(body: SeededBody.Making, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    BodyColumn(modifier) {
        Text(
            body.premise,
            style = Paper.type.lead(body.premise.length),
            color = colors.lead,
            textAlign = TextAlign.Center,
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            body.you?.let { CastLine(stringResource(R.string.wizard_you), it) }
            body.them?.let { CastLine(body.counterpart.uppercase(), it) }
        }
        if (body.opening.isNotEmpty()) {
            Text(
                body.opening,
                style = Paper.type.body,
                color = colors.ink,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CastLine(label: String, cast: SeededBody.Cast) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = Paper.type.label, color = Paper.colors.muted)
        Text(
            listOfNotNull(cast.name, cast.role).joinToString(", "),
            style = Paper.type.cardText,
            color = Paper.colors.ink,
        )
    }
}

/** A step's body, centred, with the page's spacing between its parts. */
@Composable
private fun BodyColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content,
    )
}
