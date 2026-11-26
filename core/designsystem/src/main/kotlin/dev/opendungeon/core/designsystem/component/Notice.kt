package dev.opendungeon.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opendungeon.core.designsystem.Paper

/** Short notices, such as a failure, set in ink on the page's edge. Show one with [SnackbarHostState.showSnackbar]. */
@Composable
public fun PaperNotices(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = Paper.colors.ink,
            contentColor = Paper.colors.onInk,
        ) {
            Text(data.visuals.message, style = Paper.type.caption.copy(fontSize = 14.sp))
        }
    }
}
