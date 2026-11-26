package dev.opendungeon.domain.drafting

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.domain.openrouter.ModelCatalogRepository
import dev.opendungeon.domain.openrouter.ModelSetupRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The model that writes a story's premise and cast, as the player tuned it. */
public data class DraftingModel(val model: AiModel, val tuning: Tuning)

/**
 * Decides which model drafts. The story model is only chosen on creation's last step, by audition, so
 * drafting needs a model of its own before that.
 */
public interface ResolveDraftingModel {
    /** The drafting model, or `null` while none is chosen or the chosen one is no longer offered. */
    public operator fun invoke(): Flow<DraftingModel?>
}

/**
 * Drafts with the model the player chose and tuned on the models screen. A separate drafting choice
 * ranked by the player's own scores arrives with model notes; until then the chosen model is the one
 * the player already trusts with their key and tuning.
 */
@Inject
@ContributesBinding(AppScope::class)
public class DefaultResolveDraftingModel(
    private val setup: ModelSetupRepository,
    private val catalog: ModelCatalogRepository,
) : ResolveDraftingModel {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun invoke(): Flow<DraftingModel?> =
        combine(setup.chosenModel, catalog.catalog) { id, catalog -> id?.let { catalog?.get(it) } }
            .distinctUntilChanged()
            .flatMapLatest { model ->
                if (model == null) flowOf(null) else setup.tuning(model.id).map { DraftingModel(model, it) }
            }
            .distinctUntilChanged()
}
