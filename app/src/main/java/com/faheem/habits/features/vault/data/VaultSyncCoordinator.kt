package com.faheem.habits.features.vault.data

import android.content.Context
import com.faheem.habits.core.data.sync.SupabaseSyncRepository
import com.faheem.habits.core.ui.AppModule
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Coordinates Vault-specific operations with the shared sync engine.
 * The ViewModel does not construct the sync engine or choose cloud tables.
 */
class VaultSyncCoordinator @Inject constructor(
    @ApplicationContext context: Context
) {
    private val syncRepository = SupabaseSyncRepository(context)

    suspend fun syncVaultEntries(
        accountKey: ByteArray? = null,
        fullPull: Boolean = false
    ): Int = syncRepository.syncVaultEntries(accountKey, fullPull)

    suspend fun resetVault() {
        syncRepository.resetModule(AppModule.Vault)
    }
}
