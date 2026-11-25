package dev.opendungeon.feature.newstory.domain.policy

import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.IdGenerator
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.DraftEdit
import dev.opendungeon.feature.newstory.domain.Option
import dev.opendungeon.feature.newstory.domain.Selection
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.StepPolicy
import dev.opendungeon.feature.newstory.domain.vocabulary.RollChoice
import dev.opendungeon.feature.newstory.domain.vocabulary.VocabularyList
import dev.opendungeon.feature.newstory.domain.vocabulary.WizardVocabulary
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/**
 * The player's own character. Picking a role deals a card with a want, a flaw and a strength, each
 * with its own die and editable; the step's die redeals the role and all three. The next arrow moves on.
 */
@Inject
@ContributesIntoSet(AppScope::class)
public class StarringPolicy(
    private val vocabulary: WizardVocabulary,
    private val rollChoice: RollChoice,
    private val ids: IdGenerator,
) : StepPolicy {

    override val step: CreationStep = CreationStep.Starring

    override fun body(draft: StoryDraft): StepBody {
        val role = draft.protagonist?.role
        val offered = vocabulary[VocabularyList.Roles]
        val own = role?.takeIf { it !in offered }?.let { listOf(Option(Choice.Word(it), it, picked = true, own = true)) }
        return StepBody.Starring(
            roles = own.orEmpty() + offered.map { Option(Choice.Word(it), it, picked = it == role) },
            you = draft.protagonist,
        )
    }

    override fun canAdvance(draft: StoryDraft): Boolean =
        draft.protagonist?.let { it.role != null || it.name != null } == true

    override fun canRoll(draft: StoryDraft): Boolean = true

    override fun select(draft: StoryDraft, choice: Choice): Selection? {
        if (choice !is Choice.Word || choice.text !in vocabulary[VocabularyList.Roles]) return null
        val you = draft.protagonist ?: dealCard()
        return Selection(draft.copy(protagonist = you.copy(role = choice.text)), advance = false)
    }

    override fun edit(draft: StoryDraft, edit: DraftEdit): StoryDraft? {
        if (edit !is DraftEdit.YouLine) return null
        val you = draft.protagonist ?: return null
        return draft.copy(protagonist = you.with(edit.line, edit.text))
    }

    override suspend fun roll(draft: StoryDraft): Selection {
        val you = draft.protagonist ?: Character(id = CharacterId(ids.next()))
        val role = rollChoice(VocabularyList.Roles, avoiding = listOfNotNull(you.role)).single()
        val dealt = CARD_LINES.entries.fold(you.copy(role = role)) { card, (line, list) ->
            card.with(line, rollChoice(list, avoiding = listOfNotNull(card[line])).single())
        }
        return Selection(draft.copy(protagonist = dealt), advance = false)
    }

    override suspend fun rollLine(draft: StoryDraft, line: CharacterLine): StoryDraft? {
        val you = draft.protagonist ?: return null
        val list = CARD_LINES[line] ?: return null
        return draft.copy(protagonist = you.with(line, rollChoice(list, avoiding = listOfNotNull(you[line])).single()))
    }

    private fun dealCard(): Character = CARD_LINES.entries.fold(Character(id = CharacterId(ids.next()))) { card, (line, list) ->
        card.with(line, rollChoice(list).single())
    }

    private companion object {
        val CARD_LINES: Map<CharacterLine, VocabularyList> = mapOf(
            CharacterLine.Wants to VocabularyList.Wants,
            CharacterLine.Flaw to VocabularyList.Flaws,
            CharacterLine.GoodAt to VocabularyList.GoodAt,
        )
    }
}
