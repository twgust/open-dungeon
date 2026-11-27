package dev.opendungeon.feature.newstory.ui.seeded

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperColors
import dev.opendungeon.core.designsystem.component.PaperField
import dev.opendungeon.core.designsystem.component.SentenceLead
import dev.opendungeon.core.ui.rememberEditedText
import dev.opendungeon.feature.newstory.domain.seeded.Person
import dev.opendungeon.feature.newstory.domain.seeded.SeedPart
import dev.opendungeon.feature.newstory.domain.seeded.SeededBody
import dev.opendungeon.feature.newstory.ui.R
import dev.opendungeon.feature.newstory.ui.step.LimitsBody

/**
 * The seed, large, with each blank underlined. A blank is a link: tapping it, or activating it with a
 * screen reader, asks for other words in it. While a model writes, the words it replaces fade.
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
        val sentence = remember(body, colors) { body.sentence(colors) { onIntent(SeededIntent.Reroll(it)) } }
        Text(
            sentence,
            style = Paper.type.body.copy(fontSize = 32.sp, lineHeight = 1.3.em),
            color = if (body.replacing) colors.faint else colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(stringResource(R.string.seeded_hint), style = Paper.type.caption, color = colors.muted)
    }
}

private fun SeededBody.Seed.sentence(colors: PaperColors, onReroll: (String) -> Unit): AnnotatedString =
    buildAnnotatedString {
        parts.forEach { part ->
            when (part) {
                is SeedPart.Text -> append(part.text)
                is SeedPart.Blank -> {
                    val faded = part.pending || replacing
                    val style = SpanStyle(
                        color = if (faded) colors.faint else colors.ink,
                        textDecoration = TextDecoration.Underline,
                    )
                    if (part.canReroll) {
                        val link = LinkAnnotation.Clickable(
                            tag = part.key,
                            styles = TextLinkStyles(style = style),
                            linkInteractionListener = { onReroll(part.key) },
                        )
                        withLink(link) { append(part.text) }
                    } else {
                        withStyle(style) { append(part.text) }
                    }
                }
            }
        }
    }

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
