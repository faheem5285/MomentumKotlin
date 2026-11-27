package com.faheem.habits.features.vault

import java.security.MessageDigest

/** Fixed-width mask so a hidden value never reveals its length. */
internal const val MASKED_SENSITIVE_VALUE = "••••••••"

internal const val SENSITIVE_CLIPBOARD_LABEL = "Sensitive vault value"
internal const val SENSITIVE_CLIPBOARD_TOAST =
    "Copied. Clipboard clears after 30 seconds if unchanged."

/**
 * The account key is escrowed so the vault can be unlocked on another device. That is not E2EE:
 * anyone who can read the account key and encrypted rows can decrypt them.
 */
internal const val VAULT_CLOUD_KEY_THREAT_MODEL =
    "Security model: your vault key is stored with your signed-in account so you can unlock " +
        "the vault on another device. That means this is not end-to-end encryption; anyone who " +
        "can read your account key and the encrypted records can decrypt them. The export file " +
        "is protected separately with the backup password you choose."

internal fun sensitiveClipboardHash(value: String): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
