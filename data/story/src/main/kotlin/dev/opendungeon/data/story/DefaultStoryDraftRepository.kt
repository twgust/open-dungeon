package dev.opendungeon.data.story

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import dev.opendungeon.core.database.story.StoryDraftDao
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.domain.story.StoryDraftRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** Keeps drafts in the app database. */
@Inject
@ContributesBinding(AppScope::class)
public class DefaultStoryDraftRepository(private val dao: StoryDraftDao) : StoryDraftRepository {

    override suspend fun openDraft(): Outcome<StoryDraft?, StorageError> = storage { dao.first()?.toDomain() }

    override suspend fun draft(id: DraftId): Outcome<StoryDraft?, StorageError> = storage {
        dao.get(id.value)?.toDomain()
    }

    override suspend fun save(draft: StoryDraft): Outcome<Unit, StorageError> = storage {
        dao.replaceAll(draft.toStored())
    }

    override suspend fun discard(id: DraftId): Outcome<Unit, StorageError> = storage { dao.delete(id.value) }

    /**
     * Runs one database call and folds SQLite's failures into a [StorageError]. Only SQLite's own
     * exceptions are caught: anything else Room throws, such as a missing migration, is a bug.
     */
    private inline fun <T> storage(block: () -> T): Outcome<T, StorageError> = try {
        Outcome.Success(block())
    } catch (_: SQLiteFullException) {
        Outcome.Failure(StorageError.Full)
    } catch (e: SQLiteException) {
        Outcome.Failure(StorageError.Failed(e))
    }
}
