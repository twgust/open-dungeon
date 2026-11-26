package dev.opendungeon.feature.newstory.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import dev.opendungeon.domain.story.Sentence

/** The sentence as the page sets it: the player's own words in italics, everything else upright. */
internal fun Sentence.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    segments.forEach { segment ->
        if (segment is Sentence.Segment.Chosen && segment.own) {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(segment.text) }
        } else {
            append(segment.text)
        }
    }
}
