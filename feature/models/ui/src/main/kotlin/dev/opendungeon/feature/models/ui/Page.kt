package dev.opendungeon.feature.models.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons
import dev.opendungeon.core.designsystem.component.GhostIconButton

/** The top of each page: back, a serif title with an optional line under it, and actions at the end. */
@Composable
internal fun PageHeader(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GhostIconButton(PaperIcons.Back, stringResource(R.string.models_back), onClick = onBack)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                title,
                style = Paper.type.cardName.copy(fontSize = Paper.type.pill.fontSize),
                color = Paper.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    it,
                    style = Paper.type.caption,
                    color = Paper.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/** Small capitals over a section: "PARAMETERS". */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Paper.type.label, color = Paper.colors.muted, modifier = modifier)
}

/** A hairline that moves while something loads, in the place a [dev.opendungeon.core.designsystem.component.ProgressLine] would sit. */
@Composable
internal fun BusyLine(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier = modifier.fillMaxWidth().height(2.dp),
        color = Paper.colors.ink,
        trackColor = Paper.colors.track,
    )
}
