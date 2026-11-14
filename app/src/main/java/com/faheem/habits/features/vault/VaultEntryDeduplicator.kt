package com.faheem.habits.features.vault

import com.faheem.habits.core.data.VaultRecord

/**
 * Decrypts Vault records and removes duplicate secrets and duplicate record IDs.
 *
 * This pure operation is kept outside the screen so import and restore flows
 * can share the same deduplication behavior.
 */
internal fun uniqueVaultEntries(
    records: List<VaultRecord>,
    key: ByteArray
): List<Pair<VaultRecord, VaultSecret>> {
    val seen = mutableSetOf<VaultSecret>()
    val seenUuids = mutableSetOf<String>()
    return records.mapNotNull { record ->
        val secret = VaultCrypto.decrypt(record.nonce, record.ciphertext, key)
        if (secret !in seen && record.uuid !in seenUuids) {
            seen += secret
            seenUuids += record.uuid
            record to secret
        } else {
            null
        }
    }
}
