package dev.opendungeon.feature.newstory.ui.step

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.component.ChoicePill
import dev.opendungeon.feature.newstory.domain.Option
import dev.opendungeon.feature.newstory.domain.OptionGroup
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.ui.R
import dev.opendungeon.feature.newstory.ui.WizardIntent

@Composable
internal fun WordsBody(body: StepBody.Words, onIntent: (WizardIntent) -> Unit) {
    PillGroups(body.groups, onIntent)
}

/** The pills in grouped lists are smaller than a lone row's, since a group can hold a dozen. */
internal val GroupedPill: TextStyle @Composable get() = Paper.type.pill.copy(fontSize = 19.sp)

/**
 * Open groups under their labels, then one line naming the folded ones. Tapping a folded group's name
 * shows it under the open ones for as long as the step is on screen: which groups are open is the step's
 * to decide, and unfolding one only looks at it.
 */
@Composable
internal fun PillGroups(groups: List<OptionGroup>, onIntent: (WizardIntent) -> Unit, modifier: Modifier = Modifier) {
    var unfolded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        groups.filter { it.open }.forEach { group -> PillGroup(group, onIntent) }
        groups.filter { !it.open && it.label in unfolded }.forEach { group -> PillGroup(group, onIntent) }
        val folded = groups.filter { !it.open && it.label !in unfolded }.mapNotNull(OptionGroup::label)
        if (folded.isNotEmpty()) FoldedGroups(folded, onUnfold = { unfolded = unfolded + it })
    }
}

@Composable
private fun PillGroup(group: OptionGroup, onIntent: (WizardIntent) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        group.label?.let { GroupLabel(it) }
        PillRow(group.options, onIntent, style = GroupedPill)
    }
}

/** A group's name between two hairlines. */
@Composable
private fun GroupLabel(label: String) {
    Row(
        Modifier.fillMaxWidth().semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Hairline(Modifier.weight(1f))
        Text(label.uppercase(), style = Paper.type.label, color = Paper.colors.muted)
        Hairline(Modifier.weight(1f))
    }
}

@Composable
private fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.height(1.dp).background(Paper.colors.hairline))
}

/** "Also" and the folded groups' names, each a tap that unfolds it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoldedGroups(labels: List<String>, onUnfold: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.wizard_more_groups).uppercase(),
            style = Paper.type.label,
            color = Paper.colors.faint,
            modifier = Modifier.padding(end = 6.dp),
        )
        labels.forEach { label ->
            Text(
                text = label.uppercase(),
                style = Paper.type.label,
                color = Paper.colors.inkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = stringResource(R.string.wizard_show_group, label)) { onUnfold(label) }
                    .padding(horizontal = 8.dp, vertical = 16.dp),
            )
        }
    }
}

/** Pills centred and wrapping, the player's own words in italics. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PillRow(
    options: List<Option>,
    onIntent: (WizardIntent) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = Paper.type.pill,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { option ->
            ChoicePill(
                text = option.text,
                picked = option.picked,
                onClick = { onIntent(WizardIntent.Pick(option.choice)) },
                style = if (option.own) style.copy(fontStyle = FontStyle.Italic) else style,
            )
        }
    }
}
