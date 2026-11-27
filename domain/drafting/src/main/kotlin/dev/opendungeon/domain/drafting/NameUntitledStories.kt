package dev.opendungeon.domain.drafting

import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StoryRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Keeps stories named: every story found untitled is named by [NameStory], one at a time, the one played
 * last first. Naming is background work the player never asked for, so a story that cannot be named only
 * stays untitled, and is asked about again the next time this runs, not before.
 */
public interface NameUntitledStories {
    /** Follows the stories until cancelled; it never returns by itself. */
    public suspend operator fun invoke()
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultNameUntitledStories(private val stories: StoryRepository, private val nameStory: NameStory) :
    NameUntitledStories {

    override suspend fun invoke(): Unit = coroutineScope {
        val asked = HashSet<StoryId>()
        val queue = Channel<StoryId>(Channel.UNLIMITED)
        launch { for (story in queue) nameStory(story) }
        stories.stories.collect { listed ->
            listed.filter { it.title == null && asked.add(it.id) }.forEach { queue.send(it.id) }
        }
    }
}
