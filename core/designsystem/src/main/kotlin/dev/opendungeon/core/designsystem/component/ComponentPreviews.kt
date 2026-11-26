package dev.opendungeon.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons
import dev.opendungeon.core.designsystem.PaperTheme

@Preview(name = "Light", widthDp = 390)
@Preview(name = "Dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large text", widthDp = 390, fontScale = 2f)
private annotation class PreviewOnPaper

@Composable
private fun OnPaper(content: @Composable () -> Unit) = PaperTheme {
    Column(
        modifier = Modifier.background(Paper.colors.paper).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@PreviewOnPaper
@Composable
private fun SentenceStepPreview() = OnPaper {
    ProgressLine(progress = 0.2f)
    SentenceLead(lead = AnnotatedString("A gothic noir story,"), prompt = "twisted by")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChoicePill("a heist", picked = true, onClick = {})
        ChoicePill("a romance", picked = false, onClick = {})
        ChoicePill("cosmic dread", picked = false, onClick = {})
    }
    OwnPill("my own", onClick = {})
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        GhostIconButton(PaperIcons.Back, "Back", onClick = {})
        DieButton("Surprise me", onClick = {})
        NextArrow("Next", onClick = {})
        BeginButton("Begin", onClick = {})
    }
}

@PreviewOnPaper
@Composable
private fun CardPreview() = OnPaper {
    CharacterCard(label = "YOU", onToggleEdit = {}, editLabel = "Edit", doneLabel = "done") {
        CardLine("WANTS", trailing = { LineDieButton("Reroll wants", onClick = {}) }) {
            Text("to clear a debt that isn’t yours", style = Paper.type.body, color = Paper.colors.ink)
        }
        CardLine("SECRET") { SealedSecret("Sealed secret") }
    }
}
