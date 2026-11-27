package com.faheem.habits.features.vault

/** Idle policy for the unlocked Vault. Times use elapsed realtime, not wall clock. */
object VaultSessionTimeout {
    const val IDLE_TIMEOUT_MS = 2 * 60 * 1_000L
    const val CHECK_INTERVAL_MS = 5_000L

    fun shouldLock(nowElapsedMs: Long, lastInteractionElapsedMs: Long): Boolean =
        lastInteractionElapsedMs > 0L &&
            nowElapsedMs - lastInteractionElapsedMs >= IDLE_TIMEOUT_MS
}
