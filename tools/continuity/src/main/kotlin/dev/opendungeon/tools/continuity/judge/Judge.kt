package dev.opendungeon.tools.continuity.judge

import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.tools.continuity.model.ModelChoice
import dev.opendungeon.tools.continuity.scenario.CastMember
import dev.opendungeon.tools.continuity.scenario.Fact
import dev.opendungeon.tools.continuity.scenario.FactId
import dev.opendungeon.tools.continuity.scenario.Scenario
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Reads a passage and says, for each fact, whether the passage states it, contradicts it or leaves it out. */
internal interface Judge {
    /**
     * @param passage what the model under test wrote, or a turn being checked.
     * @param people who the story's people are, so a role or a title in the passage can be tied to a name.
     */
    suspend fun judge(passage: String, facts: List<Fact>, people: WhoIsWho, model: ModelChoice): JudgeResult
}

/**
 * The story's people as every reader may know them: names and public sheets, never the story itself.
 * Without it the judge cannot tell that "the harbourmaster" in an answer is the Aldous Fenn of a fact,
 * and misses every leak that names someone by their role.
 *
 * @property protagonist the player's character, whom both the passage and the facts call "you".
 */
internal data class WhoIsWho(val protagonist: CastMember, val cast: List<CastMember>) {
    companion object {
        fun of(scenario: Scenario): WhoIsWho = WhoIsWho(scenario.protagonist, scenario.cast)
    }
}

internal sealed interface JudgeResult {
    /** One verdict for every fact asked about. */
    data class Judged(val verdicts: Map<FactId, FactVerdict>) : JudgeResult

    data class Failed(val reason: JudgeFailure) : JudgeResult
}

internal sealed interface JudgeFailure {
    data class Generation(val error: GenerationError) : JudgeFailure

    /** The judge answered, but left facts out, named facts it was not asked about, or repeated one. */
    data class Incomplete(val detail: String) : JudgeFailure
}

@Serializable
internal data class FactVerdict(val verdict: Verdict, val quote: String)

@Serializable
internal enum class Verdict {
    /** Stated, or made unmistakable, as something known. */
    @SerialName("asserted")
    Asserted,

    /**
     * Raised only as a guess, a suspicion, a rumour or one possibility among others, without a claim to know
     * it. Kept apart from [Asserted] because a character may reasonably suspect what they were never told,
     * and only a comparison with a reader who was told nothing says whether a suspicion is a leak.
     */
    @SerialName("suspected")
    Suspected,

    /** Something incompatible with the fact is stated or made unmistakable. */
    @SerialName("contradicted")
    Contradicted,

    /** Neither. */
    @SerialName("absent")
    Absent,
}
