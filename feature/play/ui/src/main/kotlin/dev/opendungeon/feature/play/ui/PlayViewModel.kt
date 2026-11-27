package dev.opendungeon.feature.play.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opendungeon.core.mvi.MviViewModel
import dev.opendungeon.feature.play.domain.Play
import dev.opendungeon.feature.play.domain.PlayOutcome
import dev.opendungeon.feature.play.domain.PlaySession
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * A translator: intents go to the session as verbs, the session's state comes back as UI state, its
 * outcomes as effects. Every rule and every choice of what to show lives in the session.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class PlayViewModel(sessionFactory: PlaySession.Factory) :
    MviViewModel<PlayUiState, PlayIntent, PlayEffect>(PlayUiState(Play.Opening)) {

    private val session = sessionFactory.create(viewModelScope)

    init {
        session.state.onEach { play -> reduce { PlayUiState(play) } }.launchIn(viewModelScope)
        session.outcomes.onEach { sendEffect(it.toEffect()) }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: PlayIntent) = when (intent) {
        is PlayIntent.Open -> session.start(intent.story)
        is PlayIntent.TypeTurn -> session.typeTurn(intent.text)
        PlayIntent.Advance -> session.advance()
        PlayIntent.Retry -> session.retry()
        PlayIntent.OpenReason -> session.openReason()
        PlayIntent.OpenGuide -> session.openGuide()
        is PlayIntent.EditBeat -> session.editBeat(intent.beat)
        is PlayIntent.TypeNote -> session.typeNote(intent.text)
        PlayIntent.Submit -> session.submit()
        PlayIntent.Cancel -> session.cancel()
        PlayIntent.RemoveGuide -> session.removeGuide()
        PlayIntent.Stop -> session.stop()
        PlayIntent.Undo -> session.undo()
        PlayIntent.TryAgain -> session.tryAgain()
        PlayIntent.DismissFailure -> session.dismissFailure()
        // Choosing a model is the models screen's work; going there is navigation, not a rule.
        PlayIntent.ChooseModel -> sendEffect(PlayEffect.OpenModels)
        PlayIntent.Back -> session.back()
        is PlayIntent.AnswerLeave -> session.answerLeave(intent.answer)
    }

    private fun PlayOutcome.toEffect(): PlayEffect = when (this) {
        PlayOutcome.Left -> PlayEffect.Leave
        is PlayOutcome.Failed -> PlayEffect.ShowFailure(failure)
        is PlayOutcome.Refused -> PlayEffect.ShowRefusal(reason)
    }
}
