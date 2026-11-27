package dev.opendungeon.feature.library.domain

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.drafting.NameStory
import dev.opendungeon.domain.story.Story
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StoryRepository
import dev.opendungeon.domain.story.StorySummary
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The stored stories, with what the player changed on top until storage catches up: a title being kept
 * shows at once, and a deleted story is hidden while the delete can be taken back and until it is gone.
 *
 * Every verb is called from the screen's thread and changes [local] before it returns, so the checks
 * below see each verb's effect before the next verb arrives.
 */
internal class DefaultLibrary(
    private val scope: CoroutineScope,
    private val stories: StoryRepository,
    private val nameStory: NameStory,
    private val undoWindow: Duration = UNDO_WINDOW,
) : Library {

    /** What the player changed that storage does not show yet. */
    private data class Local(
        val editing: Edit? = null,
        /** Titles being kept, by story, until the stored title is the same. */
        val renamed: Map<StoryId, String> = emptyMap(),
        /** Stories deleted: while they can be taken back, and after, until they leave storage. */
        val hidden: Set<StoryId> = emptySet(),
        val undo: StoryId? = null,
    )

    private data class Edit(val story: StoryId, val text: String)

    /** A delete that is waiting out its undo window. */
    private class Removal(val story: StoryId, var undone: Boolean = false) {
        lateinit var job: Job
    }

    private val local = MutableStateFlow(Local())

    /** The stories as stored, once read. */
    private val stored = MutableStateFlow<List<StorySummary>?>(null)
    private var removal: Removal? = null

    override val state: StateFlow<LibraryState> = combine(stored.filterNotNull(), local, ::render)
        .stateIn(scope, SharingStarted.Eagerly, LibraryState())

    private val failureChannel = Channel<LibraryFailure>(Channel.BUFFERED)
    override val failures: Flow<LibraryFailure> = failureChannel.receiveAsFlow()

    /** Stories to name, in the order they were found untitled. */
    private val toName = Channel<StoryId>(Channel.UNLIMITED)
    private val asked = HashSet<StoryId>()

    init {
        stories.stories
            .onEach { list ->
                stored.value = list
                settle(list)
                list.filter { it.title == null && asked.add(it.id) }.forEach { toName.trySend(it.id) }
            }
            .launchIn(scope)
        scope.launch {
            // A failure leaves the story untitled; the next library asks again.
            for (story in toName) nameStory(story)
        }
    }

    override fun edit(story: StoryId) {
        val shown = visible(story) ?: return
        if (local.value.editing != null) return
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

    override fun delete() {
        val edit = local.value.editing ?: return
        if (visible(edit.story) == null) return cancelEdit()
        // The delete waiting before this one can no longer be taken back: its window ends now.
        removal?.job?.cancel()
        local.update { it.copy(editing = null, hidden = it.hidden + edit.story, undo = edit.story) }
        val next = Removal(edit.story)
        removal = next
        next.job = scope.launch {
            try {
                delay(undoWindow)
            } finally {
                // Also when the library closes inside the window: what the player deleted stays deleted.
                if (!next.undone) withContext(NonCancellable) { removeForGood(next.story) }
            }
        }
    }

    override fun undoDelete() {
        val pending = removal ?: return
        if (local.value.undo != pending.story) return
        pending.undone = true
        pending.job.cancel()
        removal = null
        local.update { it.copy(hidden = it.hidden - pending.story, undo = null) }
    }

    private suspend fun removeForGood(story: StoryId) {
        local.update { if (it.undo == story) it.copy(undo = null) else it }
        if (removal?.story == story) removal = null
        val result = stories.remove(story)
        if (result is Outcome.Failure) {
            local.update { it.copy(hidden = it.hidden - story) }
            failureChannel.send(LibraryFailure.Delete(result.error))
        }
    }

    /**
     * Forgets what storage now shows by itself: a title it holds, a story it no longer has. A story whose
     * delete can still be taken back stays hidden.
     */
    private fun settle(list: List<StorySummary>) {
        val byId = list.associateBy(StorySummary::id)
        local.update { now ->
            now.copy(
                renamed = now.renamed.filterNot { (id, title) -> byId[id].let { it == null || it.title == title } },
                hidden = now.hidden.filterTo(HashSet()) { it in byId || it == removal?.story },
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
        val shown = stored
            .filterNot { it.id in local.hidden }
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
    }
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultLibraryFactory(private val stories: StoryRepository, private val nameStory: NameStory) :
    Library.Factory {
    override fun create(scope: CoroutineScope): Library = DefaultLibrary(scope, stories, nameStory)
}
