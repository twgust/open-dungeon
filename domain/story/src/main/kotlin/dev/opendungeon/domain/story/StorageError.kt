package dev.opendungeon.domain.story

/** Why the device could not read or write a draft. Each case maps to something the UI can say. */
public sealed interface StorageError {
    /** The device has no space left. */
    public data object Full : StorageError

    /** The database failed for any other reason. [cause] is kept for diagnosis, never shown. */
    public data class Failed(val cause: Exception) : StorageError
}
