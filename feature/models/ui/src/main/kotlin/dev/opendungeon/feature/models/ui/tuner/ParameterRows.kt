package dev.opendungeon.feature.models.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.opendungeon.core.designsystem.Paper
import dev.opendungeon.core.designsystem.PaperIcons
import dev.opendungeon.core.designsystem.component.ChoicePill
import dev.opendungeon.core.designsystem.component.GhostIconButton
import dev.opendungeon.core.designsystem.component.GhostTextButton
import dev.opendungeon.core.designsystem.component.PaperField
import dev.opendungeon.core.model.Reasoning
import dev.opendungeon.feature.models.domain.ParameterRow
import dev.opendungeon.feature.models.domain.Setting
import dev.opendungeon.feature.models.domain.TuningEdit
import dev.opendungeon.feature.models.ui.R
import dev.opendungeon.feature.models.ui.grouped
import dev.opendungeon.feature.models.ui.label
import dev.opendungeon.feature.models.ui.sliderValue
import java.util.Locale

/** One parameter: its name and wire names, what it is set to, a reset while set, and its control. */
@Composable
internal fun ParameterRowItem(row: ParameterRow, onEdit: (TuningEdit) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.setting.label(), style = Paper.type.cardText, color = Paper.colors.ink)
                Text(row.wireNames.joinToString(" · "), style = Paper.type.caption, color = Paper.colors.faint)
            }
            Text(
                text = row.shownValue(),
                style = Paper.type.caption,
                color = if (row.isSet) Paper.colors.ink else Paper.colors.muted,
            )
            if (row.isSet) {
                GhostTextButton(stringResource(R.string.tuner_reset), onClick = {
                    onEdit(TuningEdit.Reset(row.setting))
                })
            }
        }
        when (row) {
            is ParameterRow.Slider -> SliderControl(row, onEdit)
            is ParameterRow.Whole -> WholeControl(row, onEdit)
            is ParameterRow.Stops -> StopsControl(row, onEdit)
            is ParameterRow.VerbosityChoice -> PillChoices {
                row.options.forEach { option ->
                    SmallPill(option.label(), row.value == option) { onEdit(TuningEdit.PickVerbosity(option)) }
                }
            }
            is ParameterRow.ReasoningChoice -> ReasoningControl(row, onEdit)
        }
    }
}

/** The value beside the name: what is set, or "Default" with the model's own default when it states one. */
@Composable
private fun ParameterRow.shownValue(): String = when (this) {
    is ParameterRow.Slider -> value?.sliderValue()
    is ParameterRow.Whole -> wholeValue()
    is ParameterRow.Stops -> "${values.size}/$max".takeIf { values.isNotEmpty() }
    is ParameterRow.VerbosityChoice -> value?.label()
    is ParameterRow.ReasoningChoice -> reasoningValue()
} ?: stringResource(R.string.tuner_default)

@Composable
private fun ParameterRow.Whole.wholeValue(): String? {
    val locale = currentLocale()
    return value?.grouped(locale)
        ?: modelDefault?.let { stringResource(R.string.tuner_default_value, it.grouped(locale)) }
}

@Composable
private fun ParameterRow.ReasoningChoice.reasoningValue(): String? = when (val reasoning = value) {
    null -> defaultReasoning()
    Reasoning.Off -> stringResource(R.string.tuner_off)
    is Reasoning.On -> reasoning.effort?.label() ?: stringResource(R.string.tuner_on)
    is Reasoning.Budget -> stringResource(R.string.tuner_budget)
}

@Composable
private fun ParameterRow.ReasoningChoice.defaultReasoning(): String? {
    val state = when (onByDefault) {
        null -> return null
        true -> defaultEffort?.label() ?: stringResource(R.string.tuner_on)
        false -> stringResource(R.string.tuner_off)
    }
    return stringResource(R.string.tuner_default_value, state)
}

/** The thumb sits where the row says; dragging reports each position, and the row comes back rounded. */
@Composable
private fun SliderControl(row: ParameterRow.Slider, onEdit: (TuningEdit) -> Unit) {
    val colors = Paper.colors
    val shown = (row.value ?: row.start).toFloat()
    val state = remember(row.setting) {
        SliderState(value = shown, trackRange = row.range.start.toFloat()..row.range.endInclusive.toFloat())
    }
    SideEffect { state.value = shown }
    Slider(
        state = state,
        onValueChange = { onEdit(TuningEdit.Slide(row.setting, it.toDouble())) },
        colors = SliderDefaults.colors(
            thumbColor = if (row.isSet) colors.ink else colors.muted,
            activeTrackColor = if (row.isSet) colors.ink else colors.dashed,
            inactiveTrackColor = colors.track,
        ),
    )
}

@Composable
private fun WholeControl(row: ParameterRow.Whole, onEdit: (TuningEdit) -> Unit) {
    val locale = currentLocale()
    val min = row.min
    val max = row.max
    NumberField(
        value = row.value,
        accepts = row::accepts,
        allowNegative = min == null || min < 0,
        label = row.setting.label(),
        hint = when {
            row.setting == Setting.TopK -> stringResource(R.string.tuner_top_k_off)
            min != null && max != null -> stringResource(R.string.tuner_range, min.grouped(locale), max.grouped(locale))
            min != null -> stringResource(R.string.tuner_at_least, min.grouped(locale))
            else -> stringResource(R.string.tuner_any_number)
        },
        onValue = { typed -> onEdit(typed?.let { TuningEdit.Type(row.setting, it) } ?: TuningEdit.Reset(row.setting)) },
    )
}

/**
 * A whole number typed onto the page. The field keeps what is typed, so a number not yet taken can be
 * finished; it follows [value] whenever that changes from elsewhere, such as a reset.
 */
@Composable
private fun NumberField(
    value: Long?,
    accepts: (Long) -> Boolean,
    allowNegative: Boolean,
    label: String,
    hint: String,
    onValue: (Long?) -> Unit,
) {
    val field = rememberTextFieldState(initialText = value?.toString().orEmpty())
    val currentAccepts by rememberUpdatedState(accepts)
    val currentOnValue by rememberUpdatedState(onValue)
    LaunchedEffect(value) {
        val shown = field.text.toString().toLongOrNull()
        if (shown != value && (shown == null || currentAccepts(shown))) {
            if (value == null) field.clearText() else field.setTextAndPlaceCursorAtEnd(value.toString())
        }
    }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect { text ->
            if (text.isBlank()) currentOnValue(null) else text.toLongOrNull()?.let(currentOnValue)
        }
    }
    val typed = field.text.toString()
    val taken = typed.isEmpty() || typed.toLongOrNull()?.let(accepts) == true
    Column(Modifier.padding(top = 4.dp)) {
        PaperField(
            state = field,
            label = label,
            style = Paper.type.cardText,
            underlined = true,
            singleLine = true,
            inputTransformation = WholeNumberInput(allowNegative),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(hint, style = Paper.type.caption, color = if (taken) Paper.colors.faint else Paper.colors.ink)
    }
}

/** Digits only, with a leading minus where negatives are allowed. */
private class WholeNumberInput(private val allowNegative: Boolean) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val text = toString()
        val body = if (allowNegative) text.removePrefix("-") else text
        if (!body.all(Char::isDigit) || body.length > MAX_DIGITS) revertAllChanges()
    }

    private companion object {
        /** Long's 19 digits. */
        const val MAX_DIGITS = 19
    }
}

@Composable
private fun StopsControl(row: ParameterRow.Stops, onEdit: (TuningEdit) -> Unit) {
    Column(Modifier.padding(top = 4.dp)) {
        row.values.forEachIndexed { index, stop ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "“${stop.visible()}”",
                    style = Paper.type.cardText,
                    color = Paper.colors.ink,
                    modifier = Modifier.weight(1f),
                )
                GhostIconButton(
                    PaperIcons.Close,
                    stringResource(R.string.tuner_remove_stop, stop.visible()),
                    onClick = { onEdit(TuningEdit.RemoveStop(index)) },
                    iconSize = 16,
                )
            }
        }
        if (!row.isFull) {
            val field = rememberTextFieldState()
            val add = {
                onEdit(TuningEdit.AddStop(field.text.toString()))
                field.clearText()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PaperField(
                    state = field,
                    label = stringResource(R.string.tuner_new_stop),
                    placeholder = stringResource(R.string.tuner_new_stop),
                    style = Paper.type.cardText,
                    underlined = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    onKeyboardAction = add,
                    modifier = Modifier.weight(1f),
                )
                GhostTextButton(
                    stringResource(R.string.tuner_add_stop),
                    onClick = add,
                    enabled = field.text.isNotEmpty(),
                    emphasised = true,
                )
            }
        }
    }
}

/** Line breaks and tabs are what stop sequences often are, so they are shown rather than laid out. */
private fun String.visible(): String = replace("\n", "\\n").replace("\t", "\\t")

@Composable
private fun ReasoningControl(row: ParameterRow.ReasoningChoice, onEdit: (TuningEdit) -> Unit) {
    val value = row.value
    PillChoices {
        if (row.canTurnOff) {
            SmallPill(stringResource(R.string.tuner_off), value == Reasoning.Off) {
                onEdit(TuningEdit.PickReasoning(Reasoning.Off))
            }
        }
        if (row.efforts.isEmpty()) {
            SmallPill(stringResource(R.string.tuner_on), value == Reasoning.On(effort = null)) {
                onEdit(TuningEdit.PickReasoning(Reasoning.On(effort = null)))
            }
        }
        row.efforts.forEach { effort ->
            SmallPill(effort.label(), value == Reasoning.On(effort)) {
                onEdit(TuningEdit.PickReasoning(Reasoning.On(effort)))
            }
        }
        if (row.takesBudget) {
            SmallPill(stringResource(R.string.tuner_budget), value is Reasoning.Budget) {
                if (value !is Reasoning.Budget) {
                    onEdit(TuningEdit.PickReasoning(Reasoning.Budget(ParameterRow.ReasoningChoice.STARTING_BUDGET)))
                }
            }
        }
    }
    if (value is Reasoning.Budget) {
        NumberField(
            value = value.maxTokens.toLong(),
            accepts = { it in BudgetRange },
            allowNegative = false,
            label = stringResource(R.string.tuner_budget_tokens),
            hint = stringResource(R.string.tuner_at_least, 1L.grouped(currentLocale())),
            onValue = { typed ->
                typed?.takeIf {
                    it in BudgetRange
                }?.let { onEdit(TuningEdit.PickReasoning(Reasoning.Budget(it.toInt()))) }
            },
        )
    }
}

/** What a reasoning budget can be: at least a token, at most what a request's numbers hold. */
private val BudgetRange = 1L..Int.MAX_VALUE.toLong()

@Composable
private fun PillChoices(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
internal fun SmallPill(text: String, picked: Boolean, onClick: () -> Unit) {
    ChoicePill(text = text, picked = picked, onClick = onClick, style = Paper.type.button)
}

/** The language the device writes numbers in. */
@Composable
@ReadOnlyComposable
internal fun currentLocale(): Locale = Locale.forLanguageTag(LocalLocale.current.toLanguageTag())
