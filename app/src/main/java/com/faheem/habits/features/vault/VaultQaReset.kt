package com.faheem.habits.features.vault

import com.faheem.habits.BuildConfig

/** Gate for the destructive Vault QA reset; it must never ship in release builds. */
internal object VaultQaReset {
    fun isEnabled(debugBuild: Boolean = BuildConfig.DEBUG): Boolean = debugBuild

    fun requireEnabled() {
        check(isEnabled()) { "Vault QA reset is only available in debug builds." }
    }
}
