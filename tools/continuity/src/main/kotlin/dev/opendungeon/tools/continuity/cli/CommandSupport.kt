package dev.opendungeon.tools.continuity.cli

import com.github.ajalt.clikt.core.PrintMessage
import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.tools.continuity.di.EnvironmentKey
import dev.opendungeon.tools.continuity.di.FixedApiKeyStore
import dev.opendungeon.tools.continuity.di.HarnessGraph
import dev.opendungeon.tools.continuity.model.ResolveError
import dev.opendungeon.tools.continuity.scenario.Scenario
import dev.opendungeon.tools.continuity.scenario.ScenarioCheck
import dev.opendungeon.tools.continuity.scenario.ScenarioCodec
import dev.zacsweers.metro.createGraphFactory
import kotlinx.serialization.SerializationException
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.Path
import kotlin.io.path.exists

/** A graph built around the key, failing with a message the person can act on when there is no usable key. */
internal fun harness(options: ModelOptions): HarnessGraph = harness(options.keyFile)

/** A graph built around the key in [keyFile], or in the environment when there is none. */
internal fun harness(keyFile: Path?): HarnessGraph {
    val key = EnvironmentKey.read(keyFile).getOrElse { fail(it.message.orEmpty()) }
    return createGraphFactory<HarnessGraph.Factory>().create(FixedApiKeyStore(key), HarnessGraph.Attribution)
}

/** The catalog's own descriptions of [ids], or a failure saying which id is not offered and what is. */
internal suspend fun resolve(graph: HarnessGraph, vararg ids: String): Map<String, AiModel> {
    val wanted = ids.mapTo(LinkedHashSet(), ::ModelId)
    return when (val result = graph.models.resolve(wanted)) {
        is Outcome.Success -> result.value.mapKeys { it.key.value }
        is Outcome.Failure -> when (val error = result.error) {
            is ResolveError.Access -> fail("Could not read OpenRouter's catalog: ${error.error}")
            is ResolveError.Unknown -> fail(
                buildString {
                    append("OpenRouter offers no model '${error.missing.value}' to this key.")
                    if (error.similar.isNotEmpty()) append(" Similar: ${error.similar.joinToString { it.value }}")
                },
            )
        }
    }
}

/**
 * The scenario at [source]: a path to a JSON file, or the name of one bundled with the harness. It must
 * pass [ScenarioCheck]; running probes against a broken scenario would only measure the breakage.
 */
internal fun loadScenario(source: String): Scenario {
    val path = Path(source)
    val text = when {
        path.exists() -> Files.readString(path)
        else -> Scenario::class.java.getResource("/scenarios/$source.json")?.readText()
            ?: fail("No file '$source', and no bundled scenario of that name. Bundled: ${BUNDLED.joinToString()}")
    }
    val scenario = try {
        ScenarioCodec.decode(text)
    } catch (e: SerializationException) {
        fail("'$source' is not a scenario: ${e.message}")
    }
    val problems = ScenarioCheck.problems(scenario)
    if (problems.isNotEmpty()) {
        fail(
            problems.joinToString(prefix = "'$source' breaks these rules:\n- ", separator = "\n- "),
        )
    }
    return scenario
}

/** A file name safe stamp for when a run started, in UTC. */
internal fun timestamp(): String =
    ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

/** Where commands write by default: under the repository's own build directory, which git ignores. */
internal val OutputRoot: Path = Path("build", "continuity")

internal fun fail(message: String): Nothing = throw PrintMessage(message, statusCode = 1, printError = true)

/** Scenarios that ship with the harness, by the name `run` and `pad` accept. */
private val BUNDLED = listOf("saltmere")
