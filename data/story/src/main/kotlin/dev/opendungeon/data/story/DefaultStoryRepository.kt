package dev.opendungeon.data.story

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import dev.opendungeon.core.database.play.BeatEntity
import dev.opendungeon.core.database.play.StoryDao
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.Story
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StoryRepository
import dev.opendungeon.domain.story.StorySummary
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Keeps stories in the app database.
 *
 * A save writes only the beats that changed since this repository last wrote or read the story, so typing
 * into a long story never rewrites it whole. What was written is remembered per story; when it is not
 * known, after a failure or in a new process, the beats are written whole once.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class DefaultStoryRepository(private val dao: StoryDao, private val clock: Clock) : StoryRepository {

    private val lock = Mutex()

    /** The beats as stored, by story, as far as this repository knows. Guarded by [lock]. */
    private val written = HashMap<StoryId, List<BeatEntity>>()

    override val stories: Flow<List<StorySummary>> = dao.listings()
        .map { rows -> rows.map { it.toSummary() } }
        .catch { cause -> if (cause is SQLiteException) emit(emptyList()) else throw cause }
        .distinctUntilChanged()

    override suspend fun story(id: StoryId): Outcome<Story?, StorageError> = lock.withLock {
        storage {
            val stored = dao.get(id.value)
            stored?.toDomain()?.also { written[id] = it.beatEntities() }
        }
    }

    override suspend fun begin(story: Story, from: DraftId): Outcome<Unit, StorageError> = lock.withLock {
        val stored = story.toStored(updatedAt = now())
        storage { dao.begin(stored, draftId = from.value) }
            .also { if (it is Outcome.Success) written[story.id] = stored.beats }
    }

    override suspend fun save(story: Story): Outcome<Unit, StorageError> = lock.withLock {
        val row = story.toProgress(updatedAt = now())
        val beats = story.beatEntities()
        val before = written.remove(story.id)
        val saved = storage {
            if (before == null) {
                dao.replace(row, beats)
            } else {
                val ids = beats.mapTo(HashSet(), BeatEntity::id)
                dao.update(
                    story = row,
                    changed = beats - before.toSet(),
                    removed = before.map(BeatEntity::id).filterNot(ids::contains),
                )
            }
        }
        // Remembered only once stored: after a failure, the next save writes every beat.
        if (saved is Outcome.Success) written[story.id] = beats
        saved
    }

    override suspend fun rename(id: StoryId, title: String): Outcome<Unit, StorageError> =
        storage { dao.rename(id.value, title) }

    override suspend fun nameIfUntitled(id: StoryId, title: String): Outcome<Unit, StorageError> =
        storage { dao.nameIfUntitled(id.value, title) }

    override suspend fun trash(id: StoryId): Outcome<Unit, StorageError> = lock.withLock {
        storage { dao.trash(id.value, at = now()) }
    }

    override suspend fun restore(id: StoryId): Outcome<Unit, StorageError> = lock.withLock {
        storage { dao.restore(id.value) }
    }

    override suspend fun emptyTrash(): Outcome<Unit, StorageError> = lock.withLock {
        // What this repository wrote of a removed story is forgotten: a story can only be read again by id.
        storage { dao.emptyTrash() }.also { if (it is Outcome.Success) written.clear() }
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()

    /**
     * Runs database calls and folds SQLite's failures into a [StorageError]. Only SQLite's own exceptions
     * are caught: anything else Room throws, such as a missing migration, is a bug.
     */
    private inline fun <T> storage(block: () -> T): Outcome<T, StorageError> = try {
        Outcome.Success(block())
    } catch (_: SQLiteFullException) {
        Outcome.Failure(StorageError.Full)
    } catch (e: SQLiteException) {
        Outcome.Failure(StorageError.Failed(e))
    }
}
