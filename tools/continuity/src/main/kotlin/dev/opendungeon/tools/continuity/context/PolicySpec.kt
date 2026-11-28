package dev.opendungeon.tools.continuity.context

import dev.opendungeon.tools.continuity.context.TranscriptPolicy.Projection
import dev.opendungeon.tools.continuity.context.TranscriptPolicy.Window

/**
 * Policies as they are named on the command line and in reports:
 *
 * - `full`: the whole transcript.
 * - `recent:N`: the last N turns verbatim.
 * - `chars:N`: the newest N characters, the app's current window at `chars:12000`.
 * - `perceived/…` before any of these: a character reader gets only what they perceived.
 */
internal object PolicySpec {

    /** What `run` compares when no policy is named: today's app, the window sizes in question, and the two bounds. */
    val Defaults: List<String> = listOf("full", "chars:12000", "recent:3", "recent:6", "perceived/full")

    /** The policy [spec] names, or a message saying what is wrong with it. */
    fun parse(spec: String): Result<ContextPolicy> = runCatching {
        val trimmed = spec.trim()
        val perceived = trimmed.startsWith(Projection.PERCEIVED_PREFIX)
        val windowSpec = trimmed.removePrefix(Projection.PERCEIVED_PREFIX)
        val window = when {
            windowSpec == Window.All.spec -> Window.All
            windowSpec.startsWith(RECENT) -> Window.LastTurns(count(windowSpec.removePrefix(RECENT), spec))
            windowSpec.startsWith(CHARS) -> Window.Characters(count(windowSpec.removePrefix(CHARS), spec))
            else -> throw IllegalArgumentException(
                "Unknown policy '$spec'; use full, recent:N or chars:N, optionally after perceived/",
            )
        }
        TranscriptPolicy(window, if (perceived) Projection.Perceived else Projection.Omniscient)
    }

    private fun count(text: String, spec: String): Int =
        requireNotNull(text.toIntOrNull()?.takeIf { it >= 1 }) { "Policy '$spec' needs a whole number of at least 1" }

    private const val RECENT = "recent:"
    private const val CHARS = "chars:"
}
