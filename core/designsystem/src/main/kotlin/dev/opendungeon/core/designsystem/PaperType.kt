package dev.opendungeon.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** A serif for everything the story says, a small sans for everything the app says. */
public object PaperFonts {
    public val Serif: FontFamily = FontFamily(
        Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
    )

    public val Sans: FontFamily = FontFamily(
        Font(R.font.inter, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.inter, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    )
}

@Immutable
public data class PaperType(
    /** A step's prompt: "twisted by", "How far?". */
    val prompt: TextStyle,
    val pill: TextStyle,
    /** The player's own pill and anything else in their voice. */
    val own: TextStyle,
    val body: TextStyle,
    val cardName: TextStyle,
    val cardText: TextStyle,
    /** Small capitals over a value: "YOU", "WANTS". */
    val label: TextStyle,
    val button: TextStyle,
    val caption: TextStyle,
) {
    /** The sentence so far, smaller the longer it gets so it never crowds the prompt. */
    public fun lead(length: Int): TextStyle = serif(
        size = when {
            length < SHORT_LEAD -> 32.sp
            length < MEDIUM_LEAD -> 24.sp
            else -> 20.sp
        },
        lineHeight = 1.25.em,
    )

    public companion object {
        private const val SHORT_LEAD = 30
        private const val MEDIUM_LEAD = 90

        public val Default: PaperType = PaperType(
            prompt = serif(42.sp, 1.1.em, italic = true),
            pill = serif(23.sp, 1.1.em),
            own = serif(19.sp, 1.2.em, italic = true),
            body = serif(19.sp, 1.25.em),
            cardName = serif(32.sp, 1.em),
            cardText = serif(16.sp, 1.3.em),
            label = TextStyle(fontFamily = PaperFonts.Sans, fontSize = 10.sp, letterSpacing = 0.18.em),
            button = TextStyle(fontFamily = PaperFonts.Sans, fontSize = 14.sp),
            caption = TextStyle(fontFamily = PaperFonts.Sans, fontSize = 12.sp),
        )

        private fun serif(size: TextUnit, lineHeight: TextUnit, italic: Boolean = false) = TextStyle(
            fontFamily = PaperFonts.Serif,
            fontSize = size,
            lineHeight = lineHeight,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        )
    }
}
