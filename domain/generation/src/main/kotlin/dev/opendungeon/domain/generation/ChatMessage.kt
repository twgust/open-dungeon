package dev.opendungeon.domain.generation

/** One message of a conversation sent to a model. Roles mirror the chat completions API. */
public sealed interface ChatMessage {
    public val text: String

    /** Instructions the model follows for the whole request. */
    public data class System(override val text: String) : ChatMessage

    public data class User(override val text: String) : ChatMessage

    /** Something the model said earlier, replayed as history. */
    public data class Assistant(override val text: String) : ChatMessage
}
