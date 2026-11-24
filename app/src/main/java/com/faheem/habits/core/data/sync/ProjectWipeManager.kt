package com.faheem.habits.core.data.sync

import android.content.Context
import com.faheem.habits.core.data.supabase.SupabaseRestClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Applies an administrator's cloud wipe to this installation before it can sync old rows again. */
object ProjectWipeManager {
    private const val STATE_PREFS = "momentum_project_wipe_state"
    private const val APPLIED_GENERATION = "applied_generation"
    private val mutex = Mutex()
    private val _wipeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val wipeEvents = _wipeEvents.asSharedFlow()

    enum class CheckResult { UNCHANGED, IN_PROGRESS, APPLIED }

    suspend fun applyIfNeeded(
        context: Context,
        clearSettings: suspend () -> Unit = {}
    ): CheckResult = mutex.withLock {
        val app = context.applicationContext
        val remote = SupabaseRestClient(app).projectWipeState()
        if (remote.wipeInProgress) return@withLock CheckResult.IN_PROGRESS
        val statePrefs = app.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        val savedGeneration = statePrefs.getLong(APPLIED_GENERATION, Long.MIN_VALUE)

        if (savedGeneration != Long.MIN_VALUE && remote.generation <= savedGeneration) {
            return@withLock CheckResult.UNCHANGED
        }

        if (remote.generation == 0L) {
            statePrefs.edit().putLong(APPLIED_GENERATION, 0L).commit()
            return@withLock CheckResult.UNCHANGED
        }

        // A clean install made after the wipe has no old local data to erase.
        // firstInstallTime survives app upgrades, so existing installs still reset.
        if (savedGeneration == Long.MIN_VALUE && remote.generation > 0) {
            val installedAt = app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime
            if (remote.updatedAtEpochMillis <= installedAt) {
                statePrefs.edit().putLong(APPLIED_GENERATION, remote.generation).commit()
                return@withLock CheckResult.UNCHANGED
            }
        }

        LocalAppDataReset.reset(app, clearSettings)
        check(statePrefs.edit().putLong(APPLIED_GENERATION, remote.generation).commit()) {
            "Could not save the local project-wipe marker."
        }
        _wipeEvents.tryEmit(Unit)
        CheckResult.APPLIED
    }
}
