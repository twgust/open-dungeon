package dev.opendungeon.feature.models.domain

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelEndpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tuning the chosen model. Every edit shows at once and is stored in the background; if storing fails,
 * the editor goes back to what is stored and [failures] says so.
 */
public interface ModelTuner {

    public val state: StateFlow<TunerState>

    public val failures: Flow<DataError>

    public fun edit(edit: TuningEdit)

    /** Leaves every parameter to the provider again. */
    public fun resetAll()

    /** Fetches the model's providers again after a failure. */
    public fun retryProviders()

    public fun interface Factory {
        public fun create(scope: CoroutineScope): ModelTuner
    }
}

public sealed interface TunerState {
    public data object Loading : TunerState

    /** No model is chosen, or the chosen one is no longer in the catalog. */
    public data object NoModel : TunerState

    public data class Ready(val model: AiModel, val sheet: ParameterSheet, val providers: Providers) : TunerState {
        val isTuned: Boolean get() = sheet.rows.any(ParameterRow::isSet)
    }
}

/** The providers serving a model, fetched each time its page opens. */
public sealed interface Providers {
    public data object Loading : Providers

    public data class Loaded(val endpoints: List<ModelEndpoint>) : Providers

    public data class Failed(val error: DataError) : Providers
}
