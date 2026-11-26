package dev.opendungeon.feature.newstory.ui

import androidx.compose.ui.text.font.FontStyle
import dev.opendungeon.domain.story.Sentence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SentenceTextTest {

    @Test
    fun `own words are set in italics, offered words upright`() {
        val sentence = Sentence(
            listOf(
                Sentence.Segment.Fixed("A "),
                Sentence.Segment.Chosen("salt-punk", own = true),
                Sentence.Segment.Fixed(" story, twisted by "),
                Sentence.Segment.Chosen("a heist", own = false),
            ),
        )

        val text = sentence.toAnnotatedString()

        assertEquals("A salt-punk story, twisted by a heist", text.text)
        val italic = text.spanStyles.single()
        assertEquals(FontStyle.Italic, italic.item.fontStyle)
        assertEquals("salt-punk", text.text.substring(italic.start, italic.end))
    }

    @Test
    fun `an empty sentence sets nothing`() {
        assertTrue(Sentence.Empty.toAnnotatedString().isEmpty())
    }
}
