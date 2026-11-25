package dev.opendungeon.domain.generation

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.Outcome
import kotlinx.serialization.KSerializer

/**
 * Asks a model for a value of type [T] and parses the answer.
 *
 * Models that support structured outputs get a native JSON schema; every other model gets the schema
 * in its instructions and a repair pass when its answer does not parse. Callers never need to know which.
 */
public interface StructuredGenerator {
    public suspend fun <T> generate(request: StructuredRequest<T>): Outcome<T, GenerationError>
}

public data class StructuredRequest<T>(
    val model: AiModel,
    val messages: List<ChatMessage>,
    val serializer: KSerializer<T>,
    /**
     * Descriptions the model reads in the schema, keyed by serial name (`"CastMember"`) or by
     * `"SerialName.property"` (`"CastMember.wants"`). Keeps payload types free of any framework annotation.
     */
    val descriptions: Map<String, String> = emptyMap(),
    /** Valid values shown to models that only get the schema as instructions. */
    val examples: List<T> = emptyList(),
    val sampling: Sampling = Sampling.ProviderDefaults,
    val sessionId: String? = null,
) {
    init {
        require(messages.isNotEmpty()) { "A request needs at least one message" }
    }
}
