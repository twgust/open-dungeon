package dev.opendungeon.data.generation

import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.json.JsonStructure
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import dev.opendungeon.core.credentials.ApiKeyStore
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.network.OpenRouterJson
import dev.opendungeon.data.generation.di.OpenRouterExecutor
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.generation.StructuredGenerator
import dev.opendungeon.domain.generation.StructuredRequest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException
import ai.koog.prompt.structure.StructuredRequest as KoogStructuredRequest

/**
 * Structured output through Koog's `executeStructured`: a native JSON schema for models that list
 * `structured_outputs`, the schema as instructions for every other model, and a fixing pass on the same
 * model when an answer does not parse.
 */
@Inject
@ContributesBinding(AppScope::class)
public class KoogStructuredGenerator(
    @OpenRouterExecutor private val executor: PromptExecutor,
    private val keys: ApiKeyStore,
) : StructuredGenerator {

    override suspend fun <T> generate(request: StructuredRequest<T>): Outcome<T, GenerationError> {
        if (keys.apiKey.first() == null) return Outcome.Failure(GenerationError.Access(DataError.MissingApiKey))

        val model = request.model.toLLModel()
        val native = model.supports(LLMCapability.Schema.JSON.Standard)
        val structure = JsonStructure.create(
            id = request.serializer.descriptor.serialName.substringAfterLast('.'),
            serializer = request.serializer,
            json = OpenRouterJson,
            schemaGenerator = if (native) {
                executor.getStandardJsonSchemaGenerator(model)
            } else {
                StandardJsonSchemaGenerator
            },
            descriptionOverrides = request.descriptions,
            examples = request.examples,
        )
        val config = StructuredRequestConfig(
            default = if (native) KoogStructuredRequest.Native(structure) else KoogStructuredRequest.Manual(structure),
        )
        val params = request.sampling.toOpenRouterParams(request.model, request.sessionId, requireParameters = native)

        val result = try {
            executor.executeStructured(
                prompt = request.messages.toPrompt(id = structure.id, params = params),
                model = model,
                config = config,
                fixingParser = StructureFixingParser(model = model, retries = FIXING_RETRIES),
            )
        } catch (e: LLMClientException) {
            // Koog surfaces transport and HTTP failures as exceptions from the request itself.
            return failureOf(e)
        } catch (e: KoogHttpClientException) {
            return failureOf(e)
        }
        return result.fold(
            onSuccess = { Outcome.Success(it.data) },
            onFailure = { cause ->
                // Parsing runs inside Koog's runCatching, which would otherwise swallow cancellation.
                if (cause is CancellationException) throw cause
                Outcome.Failure(cause.toStructuredError())
            },
        )
    }

    /** A failure Koog raised from the request; one that maps to no [GenerationError] is a bug and is rethrown. */
    private fun failureOf(e: Exception): Outcome.Failure<GenerationError> =
        Outcome.Failure(e.toGenerationErrorOrNull() ?: throw e)

    /**
     * A parse failure, direct or after the fixing parser gave up (it wraps the last
     * [SerializationException]), means the model could not follow the schema. A failure that came
     * from the client, for example one of the fixing requests, keeps its transport meaning.
     */
    private fun Throwable.toStructuredError(): GenerationError {
        val chain = generateSequence(this) { it.cause }.toList()
        val fromClient = chain.any { it is LLMClientException || it is KoogHttpClientException }
        val parseFailure = chain.firstOrNull { it is SerializationException }
        return when {
            !fromClient && parseFailure != null -> GenerationError.SchemaMismatch(parseFailure.message.orEmpty())
            else -> toGenerationErrorOrNull() ?: throw this
        }
    }

    private companion object {
        /** Each retry is another paid request, so a model that cannot follow the schema fails fast. */
        const val FIXING_RETRIES = 2
    }
}
