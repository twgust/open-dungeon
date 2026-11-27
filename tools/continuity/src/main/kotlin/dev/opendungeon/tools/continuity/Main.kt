package dev.opendungeon.tools.continuity

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import dev.opendungeon.tools.continuity.cli.CheckCommand
import dev.opendungeon.tools.continuity.cli.PadCommand
import dev.opendungeon.tools.continuity.cli.RunCommand

public suspend fun main(args: Array<String>) {
    Continuity().subcommands(CheckCommand(), PadCommand(), RunCommand()).main(args)
}

private class Continuity : SuspendingCliktCommand(name = "continuity") {
    override fun help(context: Context): String =
        "Measures how well a context policy keeps a long story continuous: recall of distant facts, " +
            "characters knowing what they should not, and contradictions."

    override suspend fun run() = Unit
}
