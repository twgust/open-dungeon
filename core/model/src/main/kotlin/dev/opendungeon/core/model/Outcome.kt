package dev.opendungeon.core.model

/** The result of an operation that can fail with a typed, expected error [E]. */
public sealed interface Outcome<out T, out E> {
    public data class Success<out T>(val value: T) : Outcome<T, Nothing>

    public data class Failure<out E>(val error: E) : Outcome<Nothing, E>
}

public inline fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

public inline fun <T, E, R> Outcome<T, E>.flatMap(transform: (T) -> Outcome<R, E>): Outcome<R, E> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}
