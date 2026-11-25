package dev.opendungeon.core.network

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.network.dto.ErrorDto
import java.io.IOException

/** How a call to OpenRouter failed, before any domain interpretation. */
public sealed interface NetworkError {
    /** OpenRouter answered with a non-2xx status. [body] is `null` when the error body was not the documented envelope. */
    public data class Http(val status: Int, val body: ErrorDto?) : NetworkError

    /** The request never got a response: offline, DNS, TLS or timeout. */
    public data class Transport(val cause: IOException) : NetworkError

    /** A 2xx response whose body did not match the expected schema. */
    public data class Decoding(val cause: Exception) : NetworkError
}

public typealias NetworkResult<T> = Outcome<T, NetworkError>
