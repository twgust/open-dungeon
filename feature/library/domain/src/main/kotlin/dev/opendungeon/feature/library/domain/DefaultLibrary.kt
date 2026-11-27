package dev.opendungeon.feature.library.domain

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.drafting.NameUntitledStories
import dev.opendungeon.domain.story.Story
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StoryRepository
import dev.opendungeon.domain.story.StorySummary
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The stored stories, with what the player changed on top until storage catches up: a title being kept
 * shows at once, a deleted story leaves at once and an undone one comes back at once.
 *
 * Deleting is storage's trash, not a timer here: a story is trashed at once and restored on undo, so
 * what the player sees survives the app dying at any moment. The undo window only decides how long
 * Undo is offered. The trash is emptied as a library opens, so nothing deleted in an earlier session
 * can still be brought back.
 *
 * Every verb is called from the screen's thread and changes [local] before it returns, so the checks
 * below see each verb's effect before the next verb arrives.
 */
internal class DefaultLibrary(
    private val scope: CoroutineScope,
    private val stories: StoryRepository,
    nameUntitled: NameUntitledStories,
    private val undoWindow: Duration = UNDO_WINDOW,
) : Library {

    /** What the player changed that storage does not show yet. */
    private data class Local(
        val editing: Edit? = null,
        /** Titles being kept, by story, until storage holds them. */
        val renamed: Map<StoryId, String> = emptyMap(),
        /** Stories deleted, until storage no longer lists them. */
        val deleted: Set<StoryId> = emptySet(),
        /** Stories undone, as they were listed, until storage lists them again. */
        val restored: Map<StoryId, StorySummary> = emptyMap(),
        val undo: StoryId? = null,
    )

    private data class Edit(val story: StoryId, val text: String)

    private val local = MutableStateFlow(Local())

    /** The stories as stored, once read. */
    private val stored = MutableStateFlow<List<StorySummary>?>(null)

    /** Each story as last listed, so an undone story comes back as the player saw it. */
    private val lastSeen = HashMap<StoryId, StorySummary>()

    /** Ends the offer of [Local.undo]. */
    private var undoOffer: Job? = null

    override val state: StateFlow<LibraryState> = combine(stored.filterNotNull(), local, ::render)
        .stateIn(scope, SharingStarted.Eagerly, LibraryState())

    private val failureChannel = Channel<LibraryFailure>(Channel.BUFFERED)
    override val failures: Flow<LibraryFailure> = failureChannel.receiveAsFlow()

    init {
        // Before anything can be deleted here, so only earlier sessions' deletes are emptied. A failure
        // only leaves them in the trash, unseen, until the next library.
        scope.launch { stories.emptyTrash() }
        stories.stories
            .onEach { listed ->
                stored.value = listed
                settle(listed)
            }
            .launchIn(scope)
        scope.launch { nameUntitled() }
    }

    override fun edit(story: StoryId) {
        if (local.value.editing != null) return
        val shown = visible(story) ?: return
        local.update { it.copy(editing = Edit(story, shown.title.orEmpty())) }
    }

    override fun typeTitle(text: String) {
        local.update { now ->
            now.editing?.let { now.copy(editing = it.copy(text = text.take(Story.MAX_TITLE_LENGTH))) } ?: now
        }
    }

    override fun saveTitle() {
        val edit = local.value.editing ?: return
        val story = visible(edit.story) ?: return cancelEdit()
        val title = edit.text.trim()
        if (!canSave(title, story)) return
        local.update { it.copy(editing = null, renamed = it.renamed + (edit.story to title)) }
        scope.launch {
            val result = stories.rename(edit.story, title)
            if (result is Outcome.Failure) {
                local.update { now ->
                    // A later rename of the same story is its own; only this one is taken back.
                    if (now.renamed[edit.story] == title) now.copy(renamed = now.renamed - edit.story) else now
                }
                failureChannel.send(LibraryFailure.Rename(result.error))
            }
        }
    }

    override fun cancelEdit() {
        local.update { it.copy(editing = null) }
    }

    override fun delete(story: StoryId) {
        val shown = visible(story) ?: return
        local.update { now ->
            now.copy(
                editing = now.editing?.takeUnless { it.story == story },
                deleted = now.deleted + story,
                restored = now.restored - story,
                undo = story,
            )
        }
        offerUndo(story)
        scope.launch {
            val result = stories.trash(story)
            if (result is Outcome.Failure) {
                local.update { now ->
                    now.copy(
                        deleted = now.deleted - story,
                        // Listed again as it was, whether or not storage has caught up with the delete.
                        restored = now.restored + (story to shown),
                        undo = now.undo?.takeUnless { it == story },
                    )
                }
                failureChannel.send(LibraryFailure.Delete(result.error))
            }
        }
    }

    override fun undoDelete() {
        val story = local.value.undo ?: return
        val shown = lastSeen[story] ?: return
        undoOffer?.cancel()
        local.update { now ->
            now.copy(deleted = now.deleted - story, restored = now.restored + (story to shown), undo = null)
        }
        scope.launch {
            val result = stories.restore(story)
            if (result is Outcome.Failure) {
                local.update { now -> now.copy(deleted = now.deleted + story, restored = now.restored - story) }
                failureChannel.send(LibraryFailure.Undo(result.error))
            }
        }
    }

    /** Offers [story] back for the undo window; a later delete's offer replaces it. */
    private fun offerUndo(story: StoryId) {
        undoOffer?.cancel()
        undoOffer = scope.launch {
            delay(undoWindow)
            local.update { if (it.undo == story) it.copy(undo = null) else it }
        }
    }

    /**
     * Forgets what storage now shows by itself: a title it holds, a delete it no longer lists, an undo it
     * lists again.
     */
    private fun settle(listed: List<StorySummary>) {
        val byId = listed.associateBy(StorySummary::id)
        lastSeen.putAll(byId)
        local.update { now ->
            now.copy(
                renamed = now.renamed.filterNot { (id, title) -> byId[id]?.title == title },
                deleted = now.deleted.filterTo(HashSet()) { it in byId },
                restored = now.restored.filterKeys { it !in byId },
            )
        }
    }

    /**
     * The story as the player sees it now, or `null` when it is not listed. Read from the same sources as
     * [state], without waiting for [state] to catch up with the last verb.
     */
    private fun visible(story: StoryId): StorySummary? =
        stored.value?.let { render(it, local.value) }?.stories?.firstOrNull { it.id == story }

    private fun canSave(title: String, story: StorySummary): Boolean = title.isNotEmpty() && title != story.title

    private fun render(stored: List<StorySummary>, local: Local): LibraryState {
        val listed =
            stored.filterNot { it.id in local.deleted } + local.restored.values.filter { it.id !in local.deleted }
        val shown = listed
            .distinctBy(StorySummary::id)
            .sortedWith(PLAYED_LAST_FIRST)
            .map { story -> local.renamed[story.id]?.let { story.copy(title = it) } ?: story }
        val editing = local.editing?.let { edit ->
            shown.firstOrNull { it.id == edit.story }?.let { story ->
                TitleEdit(
                    story = edit.story,
                    text = edit.text,
                    maxLength = Story.MAX_TITLE_LENGTH,
                    canSave = canSave(edit.text.trim(), story),
                )
            }
        }
        return LibraryState(loading = false, stories = shown, editing = editing, undo = local.undo)
    }

    private companion object {
        /** Long enough to read the line and reach Undo, short enough not to linger. */
        val UNDO_WINDOW = 6.seconds

        /** Storage's own order, kept for stories shown before storage lists them again. */
        val PLAYED_LAST_FIRST = compareByDescending(StorySummary::playedAt).thenBy { it.id.value }
    }
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultLibraryFactory(
    private val stories: StoryRepository,
    private val nameUntitled: NameUntitledStories,
) : Library.Factory {
    override fun create(scope: CoroutineScope): Library = DefaultLibrary(scope, stories, nameUntitled)
}
