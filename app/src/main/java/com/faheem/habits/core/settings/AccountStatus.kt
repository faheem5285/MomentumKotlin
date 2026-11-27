package com.faheem.habits.core.settings

/**
 * Settings subtext must not claim cloud backup while signed out or offline, and
 * must not imply local data was removed. These are the exact user-facing variants.
 */
object AccountStatus {
    const val CONNECTED = "Cloud backup is connected"
    const val NEEDS_ATTENTION =
        "Account needs attention — local data is still available on this device"
    const val LOCAL_ONLY =
        "Local data is still available on this device · no cloud backup"
    const val OFFLINE =
        "Offline: local records are available. Cloud sync will resume when connected."

    fun subtext(
        signedIn: Boolean,
        requiresAccountAttention: Boolean = false,
        offlineNotice: Boolean = false
    ): String = when {
        requiresAccountAttention -> NEEDS_ATTENTION
        signedIn -> CONNECTED
        offlineNotice -> OFFLINE
        else -> LOCAL_ONLY
    }
}
