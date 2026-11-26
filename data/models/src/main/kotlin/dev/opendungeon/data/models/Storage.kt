package dev.opendungeon.data.models

import android.database.sqlite.SQLiteException
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.Outcome

/**
 * Runs database work and folds SQLite's failures into [DataError.Storage]. Only SQLite's own exceptions
 * are caught: anything else Room throws, such as a missing migration, is a bug.
 */
internal inline fun <T> storage(block: () -> T): Outcome<T, DataError> = try {
    Outcome.Success(block())
} catch (_: SQLiteException) {
    Outcome.Failure(DataError.Storage)
}
