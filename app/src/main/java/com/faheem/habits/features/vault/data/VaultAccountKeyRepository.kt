package com.faheem.habits.features.vault.data

import android.content.Context
import com.faheem.habits.core.data.supabase.SupabaseRestClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Owns the Vault's account-key network boundary.
 *
 * Keeping Supabase key operations behind this repository prevents the
 * ViewModel from constructing or depending directly on the REST client.
 */
class VaultAccountKeyRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val client = SupabaseRestClient(context)

    suspend fun fetchVaultKey(): ByteArray? = client.fetchVaultKey()

    suspend fun saveVaultKeyIfAbsent(key: ByteArray) {
        client.saveVaultKeyIfAbsent(key)
    }

    suspend fun count(table: String): Int = client.count(table)
}
