package com.faheem.habits.core.data.sync

import java.security.MessageDigest

/** Deterministic, bounded, account-scoped first-sync backup file naming. */
internal object FirstSyncBackup {
    const val MAX_BYTES = 10 * 1024 * 1024
    const val LEGACY_PLAINTEXT_FILE = "sync-first-local-backup.json"

    /** The account id is hashed so the filename never contains an email or UUID. */
    fun fileNameForAccount(userId: String?): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((userId?.takeIf { it.isNotBlank() } ?: "local").toByteArray(Charsets.UTF_8))
        val marker = digest.joinToString("") { "%02x".format(it) }.take(12)
        return "sync-first-local-backup-$marker.enc"
    }
}
