package dev.opendungeon.tools.continuity.di

import dev.opendungeon.core.credentials.ApiKeyStore
import dev.opendungeon.core.model.ApiKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Where the harness gets the OpenRouter key: a file named on the command line, else the
 * [VARIABLE] environment variable. Never from the repository, and never echoed: [ApiKey] masks itself.
 */
internal object EnvironmentKey {
    const val VARIABLE: String = "OPENROUTER_API_KEY"

    /** The key, or a message saying how to provide one. */
    fun read(keyFile: Path?, environment: Map<String, String> = System.getenv()): Result<ApiKey> {
        val (raw, source) = when {
            keyFile != null -> {
                val text = runCatching { Files.readString(keyFile) }.getOrElse {
                    return Result.failure(IllegalArgumentException("Cannot read the key file $keyFile: ${it.message}"))
                }
                text to "The key file $keyFile"
            }
            else -> {
                val value = environment[VARIABLE]?.takeIf(String::isNotBlank) ?: return Result.failure(
                    IllegalArgumentException(
                        "Set $VARIABLE to your OpenRouter key, or pass --key-file. See tools/continuity/README.md.",
                    ),
                )
                value to VARIABLE
            }
        }
        return ApiKey.parse(raw)?.let { Result.success(it) }
            ?: Result.failure(IllegalArgumentException("$source does not hold an OpenRouter key (sk-or-v1-…)"))
    }
}

/** The one key the harness was started with. It is read-only: the harness never stores or forgets a key. */
internal class FixedApiKeyStore(key: ApiKey) : ApiKeyStore {
    override val apiKey: Flow<ApiKey?> = flowOf(key)

    override suspend fun write(apiKey: ApiKey): Unit = throw IOException("The harness does not store keys")

    override suspend fun clear(): Unit = throw IOException("The harness does not store keys")
}
