package dev.opendungeon.domain.story

import dev.opendungeon.core.model.Outcome
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** Turns a finished draft into a story that play starts from, and closes the draft. */
public interface BeginStory {
    /**
     * Makes a story of [draft]'s choices, its opening as the first beat, and stores it in place of the
     * draft. [draft] must have an opening; story creation only begins once it does.
     */
    public suspend operator fun invoke(draft: StoryDraft): Outcome<StoryId, StorageError>
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultBeginStory(private val repository: StoryRepository, private val ids: IdGenerator) : BeginStory {

    override suspend fun invoke(draft: StoryDraft): Outcome<StoryId, StorageError> {
        val opening = draft.opening.trim()
        require(opening.isNotEmpty()) { "A story begins from its opening, and this draft has none" }
        val story = Story(
            id = StoryId(ids.next()),
            setup = StorySetup.of(draft),
            beats = listOf(Beat(BeatId(ids.next()), Beat.Kind.Opening, opening)),
        )
        return when (val begun = repository.begin(story, from = draft.id)) {
            is Outcome.Failure -> begun
            is Outcome.Success -> Outcome.Success(story.id)
        }
    }
}
