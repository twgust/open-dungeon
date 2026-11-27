package dev.opendungeon.tools.continuity.cli

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo
import dev.opendungeon.tools.continuity.di.EnvironmentKey
import dev.opendungeon.tools.continuity.model.ModelChoice
import dev.opendungeon.tools.continuity.model.ReasoningChoice
import java.nio.file.Path

/** The options every command that calls a model shares. */
internal class ModelOptions : OptionGroup("Models") {
    val model: String by option("--model", help = "OpenRouter id of the model under test, or of the interlude writer")
        .default(ModelChoice.DEFAULT_MODEL)

    val judgeModel: String? by option(
        "--judge-model",
        help = "OpenRouter id of the judge; the same model when left out",
    )

    val reasoning: ReasoningChoice by option(
        "--reasoning",
        help = "How hard the model under test thinks: least, default or most",
    )
        .convert { requireNotNull(ReasoningChoice.fromWireName(it)) { "Use least, default or most" } }
        .default(ReasoningChoice.Least)

    val concurrency: Int by option(
        "--concurrency",
        help = "Model calls in flight at once",
    ).int().restrictTo(min = 1).default(DEFAULT_CONCURRENCY)

    val keyFile: Path? by option(
        "--key-file",
        help = "A file holding the OpenRouter key; else ${EnvironmentKey.VARIABLE} is read",
    )
        .path(mustExist = true, canBeDir = false)

    val judgeModelId: String get() = judgeModel ?: model

    private companion object {
        const val DEFAULT_CONCURRENCY = 6
    }
}
