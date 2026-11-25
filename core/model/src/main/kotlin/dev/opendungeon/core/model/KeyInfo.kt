package dev.opendungeon.core.model

/** What OpenRouter reports about the player's key. Amounts are USD; `null` limits mean unlimited. */
public data class KeyInfo(
    val label: String,
    val isFreeTier: Boolean,
    val usage: Double,
    val limit: Double?,
    val limitRemaining: Double?,
)
