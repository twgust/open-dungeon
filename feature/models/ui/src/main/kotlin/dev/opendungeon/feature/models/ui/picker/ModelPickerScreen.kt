package dev.opendungeon.feature.models.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperTheme
import dev.opendungeon.core.designsystem.component.ChoicePill
import dev.opendungeon.core.designsystem.component.GhostTextButton
import dev.opendungeon.core.designsystem.component.PaperField
import dev.opendungeon.core.designsystem.component.PaperNotices
import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelSort
import dev.opendungeon.core.ui.CollectEffects
import dev.opendungeon.core.ui.errorMessages
import dev.opendungeon.feature.models.domain.CatalogStatus
import dev.opendungeon.feature.models.domain.ModelFilters
import dev.opendungeon.feature.models.domain.PickerState
import dev.opendungeon.feature.models.ui.BusyLine
import dev.opendungeon.feature.models.ui.PageHeader
import dev.opendungeon.feature.models.ui.R
import dev.opendungeon.feature.models.ui.contextLabel
import dev.opendungeon.feature.models.ui.label
import dev.opendungeon.feature.models.ui.modalityLabel
import dev.opendungeon.feature.models.ui.tokens
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun ModelPickerRoute(onBack: () -> Unit, onOpenTuning: () -> Unit, viewModel: ModelPickerViewModel = metroViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notices = remember { SnackbarHostState() }
    val messages = errorMessages()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            PickerEffect.OpenChosen -> onOpenTuning()
            is PickerEffect.ShowFailure -> {
                notices.currentSnackbarData?.dismiss()
                notices.showSnackbar(messages.of(effect.error))
            }
        }
    }
    ModelPickerScreen(state = state, notices = notices, onIntent = viewModel::onIntent, onBack = onBack)
}

/** Stateless: renders [state] and reports what the player does as [PickerIntent]s. */
@Composable
fun ModelPickerScreen(
    state: PickerState,
    notices: SnackbarHostState,
    onIntent: (PickerIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Paper.colors.paper).safeDrawingPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            if (state.isRefreshing || state.isSorting) BusyLine() else Spacer(Modifier.height(2.dp))
            PageHeader(title = stringResource(R.string.picker_title), onBack = onBack) {
                GhostTextButton(
                    text = stringResource(R.string.picker_refresh),
                    onClick = { onIntent(PickerIntent.Refresh) },
                    enabled = !state.isRefreshing,
                )
            }
            SearchField(state.filters.query, onIntent)
            when (val catalog = state.catalog) {
                CatalogStatus.Loading -> Unit
                is CatalogStatus.Missing -> Missing(catalog.error, onRetry = { onIntent(PickerIntent.Refresh) })
                CatalogStatus.Ready -> {
                    Filters(state, onIntent)
                    Sorts(state.sort, onIntent)
                    Count(shown = state.models.size, total = state.total)
                    ModelList(state.models, state.chosen, onChoose = { onIntent(PickerIntent.Choose(it)) })
                }
            }
        }
        PaperNotices(notices, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SearchField(query: String, onIntent: (PickerIntent) -> Unit) {
    val field = rememberTextFieldState(initialText = query)
    val currentOnIntent by rememberUpdatedState(onIntent)
    // The field owns the text; each change goes to the picker, which filters as it arrives.
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect { currentOnIntent(PickerIntent.Search(it)) }
    }
    PaperField(
        state = field,
        label = stringResource(R.string.picker_search),
        placeholder = stringResource(R.string.picker_search),
        underlined = true,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun Filters(state: PickerState, onIntent: (PickerIntent) -> Unit) {
    val filters = state.filters
    PillRow {
        SmallPill(stringResource(R.string.filter_free), filters.freeOnly) { onIntent(PickerIntent.ToggleFreeOnly) }
        SmallPill(stringResource(R.string.filter_tools), filters.tools) { onIntent(PickerIntent.ToggleTools) }
        state.inputModalities.forEach { modality ->
            SmallPill(modalityLabel(modality), modality in filters.inputs) {
                onIntent(PickerIntent.ToggleInput(modality))
            }
        }
        ModelFilters.ContextFloors.forEach { floor ->
            SmallPill(stringResource(R.string.filter_context, floor.tokens()), filters.minContext == floor) {
                onIntent(PickerIntent.ToggleContext(floor))
            }
        }
    }
}

@Composable
private fun Sorts(current: ModelSort?, onIntent: (PickerIntent) -> Unit) {
    PillRow {
        (listOf(null) + ModelSort.entries).forEach { sort ->
            SmallPill(sort.label(), current == sort) { onIntent(PickerIntent.SortBy(sort)) }
        }
    }
}

@Composable
private fun PillRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun SmallPill(text: String, picked: Boolean, onClick: () -> Unit) {
    ChoicePill(text = text, picked = picked, onClick = onClick, style = Paper.type.button)
}

@Composable
private fun Count(shown: Int, total: Int) {
    Text(
        text = if (shown == total) {
            pluralStringResource(R.plurals.picker_count, total, total)
        } else {
            stringResource(R.string.picker_count_of, shown, total)
        },
        style = Paper.type.caption,
        color = Paper.colors.muted,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun ModelList(models: List<AiModel>, chosen: ModelId?, onChoose: (ModelId) -> Unit) {
    if (models.isEmpty()) {
        Text(
            stringResource(R.string.picker_empty),
            style = Paper.type.body,
            color = Paper.colors.muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(models, key = { it.id.value }) { model ->
            ModelRow(model, isChosen = model.id == chosen, onClick = { onChoose(model.id) })
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Paper.colors.divider)
        }
    }
}

@Composable
private fun ModelRow(model: AiModel, isChosen: Boolean, onClick: () -> Unit) {
    val colors = Paper.colors
    val chosenLabel = stringResource(R.string.picker_chosen)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                selected = isChosen
                if (isChosen) stateDescription = chosenLabel
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                model.name,
                style = Paper.type.cardText,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                model.id.value,
                style = Paper.type.caption,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(model.pricing.label(), model.contextLength?.let { contextLabel(it) }).joinToString(" · "),
                style = Paper.type.caption,
                color = colors.faint,
            )
        }
        if (isChosen) {
            Box(
                Modifier.padding(start = 12.dp).size(8.dp).background(colors.ink, CircleShape),
            )
        }
    }
}

@Composable
private fun Missing(error: DataError, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp)) {
        Text(errorMessages().of(error), style = Paper.type.body, color = Paper.colors.ink)
        GhostTextButton(stringResource(R.string.picker_retry), onClick = onRetry, emphasised = true)
    }
}

@Preview
@Composable
private fun PickerPreview() = PaperTheme {
    ModelPickerScreen(
        state = PickerState(catalog = CatalogStatus.Missing(DataError.Network)),
        notices = remember { SnackbarHostState() },
        onIntent = {},
        onBack = {},
    )
}
