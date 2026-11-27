package com.faheem.habits.core.data.sync

import android.content.Context
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.backup.WeeklyBackupSettings
import com.faheem.habits.core.data.supabase.SecureTokenStore
import com.faheem.habits.core.data.supabase.SupabaseAuthApi
import com.faheem.habits.core.settings.AppearancePreferences
import com.faheem.habits.core.settings.MomentumSettingsRepository
import com.faheem.habits.core.notifications.MomentumReminderScheduler
import com.faheem.habits.features.vault.VaultCrypto
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocalAppDataReset {
    private const val WIPE_STATE_PREFS = "momentum_project_wipe_state"
    private const val WORK_MANAGER_PREFS = "androidx.work.util.preferences"
    private const val AUTH_SESSION_PREFS = "momentum_auth_session"
    private const val ACCOUNT_PROFILE_PREFS = "account_profile"

    suspend fun reset(
        context: Context,
        clearSettings: suspend () -> Unit = {},
        preserveAuthSession: Boolean = false
    ) {
        val app = context.applicationContext
        val weeklyBackupEnabled = WeeklyBackupSettings.enabled(app)
        val weeklyBackupFolder = WeeklyBackupSettings.folder(app)
        WeeklyBackupSettings.cancel(app)
        clearSettings()
        withContext(Dispatchers.IO) {
            AppDatabase.get(app).clearAllTables()
            val preservedPreferences = buildSet {
                add(WIPE_STATE_PREFS)
                add(WORK_MANAGER_PREFS)
                add(AppDatabase.ACCOUNT_SCOPE_PREFS)
                add(PENDING_CLOUD_DELETE_PREFS)
                if (preserveAuthSession) {
                    add(AUTH_SESSION_PREFS)
                    add(ACCOUNT_PROFILE_PREFS)
                }
            }
            clearSharedPreferences(app, preservedPreferences)
            MomentumSettingsRepository(app).clearAll()
            WeeklyBackupSettings.restoreAfterReset(app, weeklyBackupEnabled, weeklyBackupFolder)
            val activeDatabaseNames = AppDatabase.activeDatabaseFileNames(app)
            clearDirectoryContents(app.filesDir, preserve = activeDatabaseNames + "account-databases")
            AppDatabase.clearActiveAccountSnapshot(app)
            clearDirectoryContents(app.cacheDir)
            clearDirectoryContents(app.noBackupFilesDir)
            app.getExternalFilesDirs(null).filterNotNull().forEach(::clearDirectoryContents)
            VaultCrypto.clearBiometricKeyForAppReset()
            if (!preserveAuthSession) {
                SecureTokenStore.clearForAppReset(app)
                SupabaseAuthApi.clearInMemorySession()
            }
            AppearancePreferences.load(app)
            runCatching { MomentumReminderScheduler.scheduleAll(app) }
        }
    }

    private fun clearSharedPreferences(context: Context, preserve: Set<String>) {
        val directory = File(context.applicationInfo.dataDir, "shared_prefs")
        directory.listFiles()?.filter { it.isFile && it.name.endsWith(".xml") }?.forEach { file ->
            val name = file.name.removeSuffix(".xml")
            if (name !in preserve) {
                check(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()) {
                    "Could not clear local app preferences."
                }
            }
        }
    }

    private fun clearDirectoryContents(directory: File, preserve: Set<String> = emptySet()) {
        directory.listFiles()?.filterNot { it.name in preserve }?.forEach { child ->
            check(child.deleteRecursively()) { "Could not remove local app data." }
        }
    }
}
