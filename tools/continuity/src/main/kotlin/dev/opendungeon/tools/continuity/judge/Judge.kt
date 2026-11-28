package dev.opendungeon.tools.continuity.judge

import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.tools.continuity.model.ModelChoice
import dev.opendungeon.tools.continuity.scenario.Fact
import dev.opendungeon.tools.continuity.scenario.FactId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Reads a passage and says, for each fact, whether the passage states it, contradicts it or leaves it out. */
internal interface Judge {
    /**
     * @param passage what the model under test wrote, or a turn being checked.
     * @param protagonist the name of the player's character, whom both the passage and the facts call "you".
     */
    suspend fun judge(passage: String, facts: List<Fact>, protagonist: String, model: ModelChoice): JudgeResult
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
    /** Stated, or made unmistakable. */
    @SerialName("asserted")
    Asserted,

    /** Something incompatible with the fact is stated or made unmistakable. */
    @SerialName("contradicted")
    Contradicted,

    /** Neither. */
    @SerialName("absent")
    Absent,
}
