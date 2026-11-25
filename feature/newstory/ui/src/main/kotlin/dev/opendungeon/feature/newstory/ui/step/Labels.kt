package dev.opendungeon.feature.newstory.ui.step

import androidx.annotation.StringRes
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.feature.newstory.ui.R

@StringRes
internal fun CharacterLine.labelRes(): Int = when (this) {
    CharacterLine.Wants -> R.string.wizard_line_wants
    CharacterLine.Flaw -> R.string.wizard_line_flaw
    CharacterLine.GoodAt -> R.string.wizard_line_good_at
    CharacterLine.Habit -> R.string.wizard_line_habit
    CharacterLine.VoiceLine -> R.string.wizard_line_voice
    CharacterLine.Role -> R.string.wizard_line_role
    CharacterLine.Name -> R.string.wizard_line_name
    CharacterLine.Age -> R.string.wizard_line_age
    CharacterLine.Gender -> R.string.wizard_line_gender
    CharacterLine.Appearance -> R.string.wizard_line_look
    CharacterLine.Personality -> R.string.wizard_line_traits
    CharacterLine.Speech -> R.string.wizard_line_speech
}

@StringRes
internal fun ContentLimits.Desire.labelRes(): Int = when (this) {
    ContentLimits.Desire.Unspoken -> R.string.wizard_desire_unspoken
    ContentLimits.Desire.Close -> R.string.wizard_desire_close
    ContentLimits.Desire.AllTheWay -> R.string.wizard_desire_all_the_way
}

@StringRes
internal fun ContentLimits.Violence.labelRes(): Int = when (this) {
    ContentLimits.Violence.Offstage -> R.string.wizard_violence_offstage
    ContentLimits.Violence.Felt -> R.string.wizard_violence_felt
    ContentLimits.Violence.Shown -> R.string.wizard_violence_shown
}
