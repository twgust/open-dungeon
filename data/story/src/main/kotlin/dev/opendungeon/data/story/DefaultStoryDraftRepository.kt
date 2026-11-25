package dev.opendungeon.data.story

import dev.opendungeon.core.database.story.StoryDraftDao
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.domain.story.StoryDraftRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Keeps drafts in the app database. */
@Inject
@ContributesBinding(AppScope::class)
public class DefaultStoryDraftRepository(private val dao: StoryDraftDao) : StoryDraftRepository {

    override suspend fun openDraft(): StoryDraft? = dao.first()?.toDomain()

    override fun observe(id: DraftId): Flow<StoryDraft?> =
        dao.observe(id.value).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun save(draft: StoryDraft) {
        dao.replaceAll(draft.toStored())
    }

    override suspend fun discard(id: DraftId) {
        dao.delete(id.value)
    }
}
