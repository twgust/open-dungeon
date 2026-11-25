package dev.opendungeon.domain.story

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
public value class DraftId(public val value: String)

@JvmInline
public value class CharacterId(public val value: String)

@JvmInline
public value class TakeId(public val value: String)

/** Mints identifiers. An interface so tests can make ids predictable. */
public interface IdGenerator {
    public fun next(): String
}

@Inject
@ContributesBinding(AppScope::class)
public class RandomIdGenerator : IdGenerator {
    @OptIn(ExperimentalUuidApi::class)
    override fun next(): String = Uuid.random().toString()
}
