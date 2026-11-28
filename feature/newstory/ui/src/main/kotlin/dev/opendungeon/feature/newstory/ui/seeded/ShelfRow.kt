package dev.opendungeon.feature.newstory.ui.seeded

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.domain.story.Register
import dev.opendungeon.feature.newstory.domain.seeded.ShelfOption
import dev.opendungeon.feature.newstory.ui.R

/** The shelves to deal seeds from, one row that scrolls sideways above the seed, like tabs. */
@Composable
internal fun ShelfRow(shelves: List<ShelfOption>, onPick: (Register?) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(ShelfRowPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shelves.forEach { option -> ShelfChip(option, onClick = { onPick(option.shelf) }) }
    }
}

@Composable
private fun ShelfChip(option: ShelfOption, onClick: () -> Unit) {
    val colors = Paper.colors
    val edge = if (option.picked) colors.ink else colors.hairline
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = option.picked, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(option.shelf.label).uppercase(),
            style = Paper.type.label,
            color = if (option.picked) colors.onInk else colors.muted,
            modifier = Modifier
                .border(1.dp, edge, CircleShape)
                .background(if (option.picked) colors.ink else colors.paper, CircleShape)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

@get:StringRes
private val Register?.label: Int
    get() = when (this) {
        null -> R.string.seeded_shelf_any
        Register.Everyday -> R.string.seeded_shelf_everyday
        Register.AfterDark -> R.string.seeded_shelf_after_dark
        Register.Dark -> R.string.seeded_shelf_dark
        Register.Fantastical -> R.string.seeded_shelf_fantastical
        Register.OtherWorlds -> R.string.seeded_shelf_other_worlds
        Register.Historical -> R.string.seeded_shelf_historical
    }

private val ShelfRowPadding = PaddingValues(horizontal = 24.dp)
