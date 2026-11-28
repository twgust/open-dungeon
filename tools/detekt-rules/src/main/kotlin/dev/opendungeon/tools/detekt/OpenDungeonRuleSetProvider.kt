package dev.opendungeon.tools.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/** The rules this project holds itself to beyond detekt's own, configured under `open-dungeon` in detekt.yml. */
public class OpenDungeonRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("open-dungeon")

    override fun instance(): RuleSet = RuleSet(ruleSetId, listOf(::UnescapedRegexBrace))
}
