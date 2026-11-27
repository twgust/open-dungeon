package dev.opendungeon.domain.story.testing

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.Story
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StoryRepository
import dev.opendungeon.domain.story.StorySummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * Stories in a map, kept the way the app database keeps them: a save writes play but not the title, and
 * every save is played later than the last. [fail] makes that many of the next writes fail with
 * [StorageError.Full]; [hold] makes the next writes wait until it completes, so a test can look at what
 * shows while a write is under way.
 */
public class InMemoryStories(vararg stories: Story) : StoryRepository {

    private val stored = MutableStateFlow(stories.map { Entry(it, playedAt = 0) }.associateBy { it.story.id })
    private var clock = 0L

    public var fail: Int = 0
    public var hold: CompletableDeferred<Unit>? = null

    /** Every write, in order, as "rename salt: The Salt Key" and the like. */
    public val writes: MutableList<String> = mutableListOf()

    /** The story as stored, in the trash or not. */
    public fun stored(id: StoryId): Story? = stored.value[id]?.story

    public fun isTrashed(id: StoryId): Boolean = stored.value[id]?.trashed == true

    override val stories: Flow<List<StorySummary>> = stored.map { entries ->
        entries.values
            .filterNot(Entry::trashed)
            .sortedWith(compareByDescending<Entry> { it.playedAt }.thenBy { it.story.id.value })
            .map { (story, playedAt, _) ->
                StorySummary(story.id, story.title, story.setup.genres, Instant.fromEpochMilliseconds(playedAt))
            }
    }

    override suspend fun story(id: StoryId): Outcome<Story?, StorageError> =
        Outcome.Success(stored.value[id]?.takeUnless(Entry::trashed)?.story)

    override suspend fun begin(story: Story, from: DraftId): Outcome<Unit, StorageError> =
        write("begin ${story.id.value}") {
            stored.value += story.id to Entry(story, ++clock)
        }

    override suspend fun save(story: Story): Outcome<Unit, StorageError> = write("save ${story.id.value}") {
        val before = stored.value[story.id] ?: return@write
        stored.value += story.id to Entry(story.copy(title = before.story.title), ++clock)
    }

    override suspend fun rename(id: StoryId, title: String): Outcome<Unit, StorageError> =
        write("rename ${id.value}: $title") {
            retitle(id) { title }
        }

    override suspend fun nameIfUntitled(id: StoryId, title: String): Outcome<Unit, StorageError> =
        write("name ${id.value}: $title") { retitle(id) { it ?: title } }

    override suspend fun trash(id: StoryId): Outcome<Unit, StorageError> = write("trash ${id.value}") {
        stored.value[id]?.let { stored.value += id to it.copy(trashed = true) }
    }

    override suspend fun restore(id: StoryId): Outcome<Unit, StorageError> = write("restore ${id.value}") {
        stored.value[id]?.let { stored.value += id to it.copy(trashed = false) }
    }

    override suspend fun emptyTrash(): Outcome<Unit, StorageError> = write("empty trash") {
        stored.value = stored.value.filterValues { !it.trashed }
    }

    private fun retitle(id: StoryId, title: (String?) -> String) {
        val entry = stored.value[id] ?: return
        stored.value += id to entry.copy(story = entry.story.copy(title = title(entry.story.title)))
    }

    private suspend fun write(name: String, block: () -> Unit): Outcome<Unit, StorageError> {
        hold?.await()
        if (fail > 0) {
            fail--
            return Outcome.Failure(StorageError.Full)
        }
        writes += name
        block()
        return Outcome.Success(Unit)
    }

    private data class Entry(val story: Story, val playedAt: Long, val trashed: Boolean = false)
}
