package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.core.model.ModelId
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.Secret
import dev.opendungeon.domain.story.Take
import dev.opendungeon.domain.story.TakeId

/*
 * The cards a step shows, already decided by its policy: which lines, in which order, and what a secret
 * may say. They are projections of the draft, never the draft itself, so nothing a card leaves out can
 * reach a screen.
 */

/** One line of a character as a card shows it. */
public data class ShownLine(val line: CharacterLine, val text: String)

/** A secret as the player may see it. A sealed secret is only a flag: its words stay with the story. */
public sealed interface ShownSecret {
    public data object None : ShownSecret

    public data object Sealed : ShownSecret

    public data class Known(val text: String) : ShownSecret
}

/** The player's own card: every line it offers, filled or not, each with its own die and editable. */
public data class YouCard(val lines: List<ShownLine>)

/** Someone on offer for the story, with the lines they have, and whether they are in it. */
public data class PersonCard(
    val id: CharacterId,
    val role: String?,
    val name: String?,
    val appearance: String?,
    val lines: List<ShownLine>,
    val secret: ShownSecret,
    val inStory: Boolean,
)

/** One audition of the opening turn. [canBeChosen] is false while it is writing or after it was cut short. */
public data class Audition(
    val id: TakeId,
    val model: ModelId,
    val text: String,
    val state: Take.State,
    val chosen: Boolean,
    val canBeChosen: Boolean,
)

/** [lines] in order, empty ones shown blank. */
internal fun Character.allLines(lines: Collection<CharacterLine>): List<ShownLine> =
    lines.map { ShownLine(it, this[it].orEmpty()) }

/** [lines] in order, empty ones left out. */
internal fun Character.filledLines(lines: Collection<CharacterLine>): List<ShownLine> =
    lines.mapNotNull { line -> this[line]?.let { ShownLine(line, it) } }

internal fun Secret.shown(): ShownSecret = when (this) {
    Secret.None -> ShownSecret.None
    is Secret.Known -> ShownSecret.Known(text)
    is Secret.Sealed -> ShownSecret.Sealed
}
