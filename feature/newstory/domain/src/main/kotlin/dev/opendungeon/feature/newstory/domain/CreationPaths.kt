package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.story.StoryDraftRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** The ways to start a story, and what each one opens on. */
public interface CreationPaths {
    /**
     * Whether the custom path resumes a draft rather than starting a new one. A draft that cannot be read
     * counts as none: the custom wizard says so itself when it opens.
     */
    public suspend fun customResumes(): Boolean
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultCreationPaths(private val drafts: StoryDraftRepository) : CreationPaths {
    override suspend fun customResumes(): Boolean = (drafts.openDraft() as? Outcome.Success)?.value != null
}
