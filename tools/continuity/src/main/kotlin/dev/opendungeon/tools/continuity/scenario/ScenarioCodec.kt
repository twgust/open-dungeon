package dev.opendungeon.tools.continuity.scenario

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Scenarios as JSON files: strict on read, because a misspelt key in a hand-written scenario would
 * otherwise silently drop a fact or a perceiver, and indented on write, because people review them.
 */
internal object ScenarioCodec {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        classDiscriminator = "type"
        explicitNulls = false
    }

    /** @throws SerializationException when [text] is not a scenario. */
    fun decode(text: String): Scenario = json.decodeFromString(Scenario.serializer(), text)

    fun encode(scenario: Scenario): String = json.encodeToString(Scenario.serializer(), scenario) + "\n"
}
