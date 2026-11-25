package dev.opendungeon.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The paper palette: one ink on one paper, with greys of the same warm hue between them. */
@Immutable
public data class PaperColors(
    val paper: Color,
    /** Cards and sheets that sit on the paper. */
    val card: Color,
    val ink: Color,
    /** Words on pills that are not picked. */
    val inkSoft: Color,
    /** The sentence so far. */
    val lead: Color,
    /** Small labels, quiet buttons and icons. */
    val muted: Color,
    /** Dice and other marks that should recede. */
    val faint: Color,
    val placeholder: Color,
    val hairline: Color,
    /** The dashed outline of the player's own pill and of work in progress. */
    val dashed: Color,
    val track: Color,
    val divider: Color,
    /** The two stripes that redact a sealed secret. */
    val redactDark: Color,
    val redactLight: Color,
) {
    /** Text and icons drawn on [ink], such as a picked pill or the next arrow. */
    val onInk: Color get() = paper

    public companion object {
        public val Light: PaperColors = PaperColors(
            paper = Color(0xFFF7F4EE),
            card = Color(0xFFFBF9F5),
            ink = Color(0xFF1B1916),
            inkSoft = Color(0xFF3F3A33),
            lead = Color(0xFF4A453E),
            muted = Color(0xFF6E685E),
            faint = Color(0xFF8C857A),
            placeholder = Color(0xFF9A9387),
            hairline = Color(0xFFDCD5C8),
            dashed = Color(0xFFB9B09F),
            track = Color(0xFFE8E3D9),
            divider = Color(0xFFE6E0D5),
            redactDark = Color(0xFFD6CFC1),
            redactLight = Color(0xFFEAE5DB),
        )

        /** The same page by lamplight: warm near-black paper, bone ink. */
        public val Dark: PaperColors = PaperColors(
            paper = Color(0xFF15130F),
            card = Color(0xFF1D1A16),
            ink = Color(0xFFEDE7DC),
            inkSoft = Color(0xFFD6CFC2),
            lead = Color(0xFFBDB5A7),
            muted = Color(0xFF9F978A),
            faint = Color(0xFF857E72),
            placeholder = Color(0xFF746D62),
            hairline = Color(0xFF3A352E),
            dashed = Color(0xFF5E574C),
            track = Color(0xFF2A2620),
            divider = Color(0xFF2E2A24),
            redactDark = Color(0xFF3A352E),
            redactLight = Color(0xFF2A2620),
        )
    }
}
