package dev.opendungeon.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.opendungeon.core.model.DataError
import java.util.Locale

/** The words for each [DataError], read while composing so effects can show them later. */
public class ErrorMessages internal constructor(
    private val malformedKey: String,
    private val missingKey: String,
    private val network: String,
    private val unexpectedResponse: String,
    private val keyStorage: String,
    private val storage: String,
    private val unauthorized: String,
    private val insufficientCredits: String,
    private val rateLimited: String,
    private val remote: String,
) {
    public fun of(error: DataError): String = when (error) {
        DataError.MalformedApiKey -> malformedKey
        DataError.MissingApiKey -> missingKey
        DataError.Network -> network
        DataError.UnexpectedResponse -> unexpectedResponse
        DataError.KeyStorage -> keyStorage
        DataError.Storage -> storage
        is DataError.Unauthorized -> unauthorized
        is DataError.InsufficientCredits -> insufficientCredits
        is DataError.RateLimited -> rateLimited
        is DataError.Remote -> remote.format(Locale.ROOT, error.status)
    }
}

@Composable
public fun errorMessages(): ErrorMessages = ErrorMessages(
    malformedKey = stringResource(R.string.error_malformed_key),
    missingKey = stringResource(R.string.error_missing_key),
    network = stringResource(R.string.error_network),
    unexpectedResponse = stringResource(R.string.error_unexpected_response),
    keyStorage = stringResource(R.string.error_key_storage),
    storage = stringResource(R.string.error_storage),
    unauthorized = stringResource(R.string.error_unauthorized),
    insufficientCredits = stringResource(R.string.error_insufficient_credits),
    rateLimited = stringResource(R.string.error_rate_limited),
    remote = stringResource(R.string.error_remote),
)
