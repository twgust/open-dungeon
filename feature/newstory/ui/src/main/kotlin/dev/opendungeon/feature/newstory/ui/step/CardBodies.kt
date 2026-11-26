package dev.opendungeon.feature.newstory.ui.step

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.component.CardLine
import dev.opendungeon.core.designsystem.component.CharacterCard
import dev.opendungeon.core.designsystem.component.ChoicePill
import dev.opendungeon.core.designsystem.component.SealedSecret
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.PersonCard
import dev.opendungeon.feature.newstory.domain.ShownSecret
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.ui.R
import dev.opendungeon.feature.newstory.ui.WizardIntent

/** Swipeable people, each taken into the story or left out. While new people are cast, the cards wait. */
@Composable
internal fun CastBody(body: StepBody.Cast, writing: Boolean, onIntent: (WizardIntent) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Deck(cardWidth = 286.dp, items = body.people, key = { it.id.value }) { person ->
            CastCard(person, enabled = !writing, onIntent)
        }
        if (body.people.isNotEmpty()) {
            Text(
                pluralStringResource(
                    R.plurals.wizard_cast_count,
                    body.people.size,
                    body.people.count {
                        it.inStory
                    },
                    body.people.size,
                ),
                style = Paper.type.caption,
                color = Paper.colors.muted,
            )
        }
    }
}

@Composable
private fun CastCard(person: PersonCard, enabled: Boolean, onIntent: (WizardIntent) -> Unit) {
    val colors = Paper.colors
    CharacterCard(label = (person.role ?: stringResource(R.string.wizard_someone)).uppercase()) {
        Column(Modifier.padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            person.name?.let { Text(it, style = Paper.type.cardName, color = colors.ink) }
            person.appearance?.let {
                Text(
                    it,
                    style = Paper.type.cardText.copy(fontSize = 17.sp, fontStyle = FontStyle.Italic),
                    color = colors.lead,
                )
            }
            HorizontalDivider(color = colors.divider)
            person.lines.forEach { line ->
                CardLine(stringResource(line.line.labelRes()), labelWidth = 50.dp) {
                    val italic = line.line == CharacterLine.VoiceLine
                    Text(
                        line.text,
                        style = Paper.type.cardText.copy(
                            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                        ),
                        color = colors.ink,
                    )
                }
            }
            when (val secret = person.secret) {
                ShownSecret.None -> Unit
                ShownSecret.Sealed -> CardLine(stringResource(R.string.wizard_line_secret), labelWidth = 50.dp) {
                    SealedSecret(stringResource(R.string.wizard_secret_sealed))
                }
                is ShownSecret.Known -> CardLine(stringResource(R.string.wizard_line_secret), labelWidth = 50.dp) {
                    Text(secret.text, style = Paper.type.cardText, color = colors.ink)
                }
            }
            ChoicePill(
                text = stringResource(if (person.inStory) R.string.wizard_cast_taken else R.string.wizard_cast_take),
                picked = person.inStory,
                onClick = { onIntent(WizardIntent.Pick(Choice.CastMember(person.id))) },
                enabled = enabled,
                style = Paper.type.pill.copy(fontSize = 18.sp),
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            )
        }
    }
}

/** A row of cards that snaps one to the centre, wider than the page's text column. */
@Composable
private fun <T> Deck(cardWidth: Dp, items: List<T>, key: (T) -> Any, card: @Composable (T) -> Unit) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        modifier = Modifier.requiredWidth(screenWidth),
        contentPadding = PaddingValues(horizontal = (screenWidth - cardWidth) / 2),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items, key = key) { item ->
            Box(Modifier.width(cardWidth)) { card(item) }
        }
    }
}
