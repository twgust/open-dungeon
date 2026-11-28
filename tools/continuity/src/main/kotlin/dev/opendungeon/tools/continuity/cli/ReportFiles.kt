package dev.opendungeon.tools.continuity.cli

import dev.opendungeon.tools.continuity.report.ContinuityReport
import dev.opendungeon.tools.continuity.report.MarkdownReport
import dev.opendungeon.tools.continuity.run.ProbeSample
import dev.opendungeon.tools.continuity.run.RunResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** The three files a run leaves behind: report.md to read, report.json with every number, samples.json with every answer. */
internal object ReportFiles {
    const val REPORT = "report.json"
    const val SAMPLES = "samples.json"

    /** Writes [result] into [directory] and returns the page that was written to report.md. */
    fun write(result: RunResult, directory: Path): String {
        val report = ContinuityReport.of(result, createdAt = Instant.now().toString())
        Files.createDirectories(directory)
        val markdown = MarkdownReport.render(report)
        Files.writeString(directory.resolve("report.md"), markdown)
        Files.writeString(directory.resolve(REPORT), Pretty.encodeToString(ContinuityReport.serializer(), report))
        Files.writeString(
            directory.resolve(SAMPLES),
            Pretty.encodeToString(ListSerializer(ProbeSample.serializer()), result.samples),
        )
        return markdown
    }

    /** What an earlier run was, read from its report.json; any version of the report holds these fields. */
    fun readHeader(directory: Path): RunHeader =
        Lenient.decodeFromString(RunHeader.serializer(), Files.readString(directory.resolve(REPORT)))

    fun readSamples(directory: Path): List<ProbeSample> =
        Pretty.decodeFromString(ListSerializer(ProbeSample.serializer()), Files.readString(directory.resolve(SAMPLES)))

    private val Pretty = Json {
        prettyPrint = true
        classDiscriminator = "type"
    }

    /** Reads reports written before fields were added, and ignores what a header does not need. */
    private val Lenient = Json(Pretty) { ignoreUnknownKeys = true }
}

/** The parts of an earlier run's report that say how to judge its answers again. */
@Serializable
internal data class RunHeader(
    val scenario: String,
    val subjectModel: String,
    val samplesPerProbe: Int,
    val createdAt: String,
    val policies: List<Policy>,
) {
    @Serializable
    data class Policy(val policy: String)
}
