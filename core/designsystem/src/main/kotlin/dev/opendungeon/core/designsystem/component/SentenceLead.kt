package dev.opendungeon.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.opendungeon.core.designsystem.Paper

/**
 * The sentence so far in the serif, then the step's [prompt] large and italic under it:
 * "A gothic noir story," over "twisted by". The lead shrinks as it grows.
 */
@Composable
public fun SentenceLead(lead: AnnotatedString, prompt: String, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (lead.isNotEmpty()) {
            Text(lead, style = Paper.type.lead(lead.length), color = colors.lead, textAlign = TextAlign.Center)
        }
        Text(
            text = prompt,
            style = Paper.type.prompt,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
    }
}
