package dev.opendungeon.tools.continuity.report

import java.util.Locale

/** A report as a page to read: the headline numbers first, then the detail that explains them. */
internal object MarkdownReport {

    /** Incidents beyond this are in the JSON report only; the page is for reading, not for every line. */
    private const val MAX_INCIDENTS = 60

    fun render(report: ContinuityReport): String = buildString {
        appendLine("# Continuity: ${report.title}")
        appendLine()
        appendLine(
            "${report.turns} turns, ${report.samplesPerProbe} sample(s) per probe. Model `${report.subjectModel}`, " +
                "judged by `${report.judgeModel}`. Run ${report.createdAt}.",
        )
        appendLine()
        appendLine(
            "Rates show the share with its 95% interval. Recall: higher is better. " +
                "Leaks and contradictions: lower is better. Suspicions are forbidden facts raised only as a guess; " +
                "compare them with `none`, whose reader was told nothing, to see how much is guessing.",
        )
        appendLine()
        summary(report)
        distance(report)
        probes(report)
        incidents(report)
    }

    private fun StringBuilder.summary(report: ContinuityReport) {
        appendLine("## Summary")
        appendLine()
        appendLine(
            "| Policy | Narrator recall | Character recall | Leaks | Suspicions | Contradictions | Probes passed | " +
                "Prompt tokens | Cost (USD) | Failed |",
        )
        appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
        report.policies.forEach { p ->
            val tokens = p.meanPromptTokens?.toString() ?: "~${(p.meanPromptCharacters ?: 0) / CHARS_PER_TOKEN} (est.)"
            appendLine(
                "| `${p.policy}` | ${p.narratorRecall.cell()} | ${p.characterRecall.cell()} | ${p.leaks.cell()} | " +
                    "${p.suspicions.cell()} | " +
                    "${p.contradictions.cell()} | ${p.probesPassed.cell()} | $tokens | ${p.costUsd ?: "–"} | " +
                    "${p.unanswered + p.unjudged} |",
            )
        }
        appendLine()
    }

    private fun StringBuilder.distance(report: ContinuityReport) {
        appendLine("## Narrator recall by distance")
        appendLine()
        appendLine("How far back the fact was established, in turns, when the probe asked about it.")
        appendLine()
        appendLine("| Policy | ${report.distanceBuckets.joinToString(" | ")} |")
        appendLine("| --- |${" --- |".repeat(report.distanceBuckets.size)}")
        report.policies.forEach { p ->
            appendLine("| `${p.policy}` | ${p.recallByDistance.joinToString(" | ") { it.cell(short = true) }} |")
        }
        appendLine()
    }

    private fun StringBuilder.probes(report: ContinuityReport) {
        appendLine("## Probes")
        appendLine()
        appendLine("Answers wholly right: every expected fact stated, nothing leaked or contradicted.")
        appendLine()
        appendLine("| Probe | ${report.policies.joinToString(" | ") { "`${it.policy}`" }} |")
        appendLine("| --- |${" --- |".repeat(report.policies.size)}")
        val probeIds = report.policies.flatMap { p -> p.probes.map { it.probe } }.distinct()
        probeIds.forEach { id ->
            val cells = report.policies.map { p ->
                p.probes.firstOrNull { it.probe == id }?.passed?.let { "${it.hits}/${it.total}" } ?: "–"
            }
            appendLine("| `$id` | ${cells.joinToString(" | ")} |")
        }
        appendLine()
    }

    private fun StringBuilder.incidents(report: ContinuityReport) {
        appendLine("## Leaks, suspicions and contradictions")
        appendLine()
        if (report.incidents.isEmpty()) {
            appendLine("None.")
            return
        }
        appendLine("| Policy | Probe | Sample | Fact | Kind | Quote |")
        appendLine("| --- | --- | --- | --- | --- | --- |")
        report.incidents.take(MAX_INCIDENTS).forEach {
            appendLine(
                "| `${it.policy}` | `${it.probe}` | ${it.sample} | `${it.fact}` | ${it.kind} | ${it.quote.cellText()} |",
            )
        }
        if (report.incidents.size > MAX_INCIDENTS) {
            appendLine()
            appendLine("${report.incidents.size - MAX_INCIDENTS} more in report.json.")
        }
    }

    private fun Rate.cell(short: Boolean = false): String {
        val share = share ?: return "–"
        val head = "${share.percent()} ($hits/$total)"
        val low = low
        val high = high
        return if (short || low == null || high == null) head else "$head [${low.percent()}–${high.percent()}]"
    }

    private fun Double.percent(): String = String.format(Locale.ROOT, "%.0f%%", this * PERCENT)

    private fun String.cellText(): String = replace("|", "\\|").replace("\n", " ").ifBlank { "–" }

    private const val PERCENT = 100

    /** A rough English average, only for policies whose provider reported no usage. */
    private const val CHARS_PER_TOKEN = 4
}
