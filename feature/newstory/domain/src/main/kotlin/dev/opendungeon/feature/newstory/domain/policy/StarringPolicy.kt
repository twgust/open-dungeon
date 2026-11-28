package dev.opendungeon.feature.newstory.domain.policy

import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.IdGenerator
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.DraftEdit
import dev.opendungeon.feature.newstory.domain.Selection
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.StepPolicy
import dev.opendungeon.feature.newstory.domain.YouCard
import dev.opendungeon.feature.newstory.domain.allLines
import dev.opendungeon.feature.newstory.domain.vocabulary.RollChoice
import dev.opendungeon.feature.newstory.domain.vocabulary.VocabularyList
import dev.opendungeon.feature.newstory.domain.vocabulary.WizardVocabulary
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/**
 * The player's own character. Picking a role, offered or in the player's own words, deals a card with a
 * want, a flaw and a strength, each with its own die and editable; the step's die redeals the role and all
 * three. Roles and the card's words are grouped by the story's genres, and every die rolls only from the
 * groups those open. The next arrow moves on.
 */
@Inject
@ContributesIntoSet(AppScope::class)
public class StarringPolicy(
    private val vocabulary: WizardVocabulary,
    private val rollChoice: RollChoice,
    private val ids: IdGenerator,
) : StepPolicy {

    override val step: CreationStep = CreationStep.Starring

    private val roles: List<String> get() = vocabulary[VocabularyList.Roles]

    private fun StoryDraft.shelf(list: VocabularyList): Shelf = Shelf.of(vocabulary, list, genres)

    override fun body(draft: StoryDraft): StepBody {
        val role = draft.protagonist?.role?.let { phraseFor(it, roles) }
        return StepBody.Starring(
            roles = draft.shelf(VocabularyList.Roles).options(listOfNotNull(role)),
            you = draft.protagonist?.let { YouCard(it.allLines(CARD_LINES.keys)) },
        )
    }

    override fun canAdvance(draft: StoryDraft): Boolean = draft.protagonist?.role != null

    override fun canRoll(draft: StoryDraft): Boolean = true

    /** The player may always name a role of their own, in place of whichever is picked. */
    override fun takesOwnWords(draft: StoryDraft): Boolean = true

    override fun select(draft: StoryDraft, choice: Choice): Selection? {
        if (choice !is Choice.Word) return null
        return when (choice.text) {
            draft.protagonist?.role -> Selection(draft, advance = false)
            in roles -> withRole(draft, choice.text)
            else -> null
        }
    }

    override fun submitOwn(draft: StoryDraft, text: String): Selection? =
        phraseFor(text, roles)?.let { withRole(draft, it.text) }

    override fun edit(draft: StoryDraft, edit: DraftEdit): StoryDraft? {
        if (edit !is DraftEdit.YouLine) return null
        val you = draft.protagonist ?: return null
        return draft.copy(protagonist = you.with(edit.line, edit.text))
    }

    override suspend fun roll(draft: StoryDraft): Selection {
        val you = draft.protagonist ?: Character(id = CharacterId(ids.next()))
        val role = rollChoice(draft.shelf(VocabularyList.Roles).rollable, avoiding = listOfNotNull(you.role)).single()
        val dealt = CARD_LINES.keys.fold(you.copy(role = role)) { card, line -> card.rolled(line, draft) }
        return Selection(draft.copy(protagonist = dealt), advance = false)
    }

    override suspend fun rollLine(draft: StoryDraft, line: CharacterLine): StoryDraft? {
        val you = draft.protagonist ?: return null
        if (line !in CARD_LINES) return null
        return draft.copy(protagonist = you.rolled(line, draft))
    }

    /** [draft] starring [role], keeping the card if there is one and dealing one if not. */
    private fun withRole(draft: StoryDraft, role: String): Selection {
        val you = draft.protagonist ?: CARD_LINES.keys.fold(Character(id = CharacterId(ids.next()))) { card, line ->
            card.rolled(line, draft)
        }
        return Selection(draft.copy(protagonist = you.copy(role = role)), advance = false)
    }

    /** This card with a fresh word in [line], from the groups [draft]'s genres open. */
    private fun Character.rolled(line: CharacterLine, draft: StoryDraft): Character {
        val words = draft.shelf(CARD_LINES.getValue(line)).rollable
        return with(line, rollChoice(words, avoiding = listOfNotNull(this[line])).single())
    }

    private companion object {
        /** The card's lines, in the order it shows them, each with the list its die rolls from. */
        val CARD_LINES: Map<CharacterLine, VocabularyList> = mapOf(
            CharacterLine.Wants to VocabularyList.Wants,
            CharacterLine.Flaw to VocabularyList.Flaws,
            CharacterLine.GoodAt to VocabularyList.GoodAt,
        )
    }
}
