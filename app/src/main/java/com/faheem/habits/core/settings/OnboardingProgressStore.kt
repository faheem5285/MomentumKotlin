package com.faheem.habits.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "momentum_onboarding"
)

/**
 * Install-scoped onboarding progress. Deliberately separate from
 * `momentum_app_settings` so account switches, local resets and project wipes
 * do not erase "getting started" progress.
 */
@Singleton
class OnboardingProgressStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val progress: Flow<OnboardingProgress> = context.onboardingDataStore.data.map { prefs ->
        val tasks = OnboardingTask.entries.associate { task ->
            val prefix = "onboarding_task_${task.id}"
            task.id to OnboardingTaskState(
                done = prefs[booleanPreferencesKey("${prefix}_done")] ?: false,
                dismissed = prefs[booleanPreferencesKey("${prefix}_dismissed")] ?: false,
                snoozedUntil = prefs[longPreferencesKey("${prefix}_snoozed_until")] ?: 0L,
                doneAt = prefs[longPreferencesKey("${prefix}_done_at")] ?: 0L,
                dismissedAt = prefs[longPreferencesKey("${prefix}_dismissed_at")] ?: 0L
            )
        }
        OnboardingProgress(
            phase = if (prefs[COMPLETED] == true) OnboardingPhase.DONE else OnboardingPhase.PENDING,
            version = prefs[VERSION] ?: OnboardingProgress.CURRENT_VERSION,
            tasks = tasks
        )
    }

    suspend fun markDone(task: OnboardingTask, nowMillis: Long) {
        context.onboardingDataStore.edit { prefs ->
            val prefix = "onboarding_task_${task.id}"
            prefs[booleanPreferencesKey("${prefix}_done")] = true
            prefs[longPreferencesKey("${prefix}_done_at")] = nowMillis
            prefs[booleanPreferencesKey("${prefix}_dismissed")] = false
            prefs[longPreferencesKey("${prefix}_snoozed_until")] = 0L
        }
    }

    suspend fun dismiss(task: OnboardingTask, nowMillis: Long) {
        context.onboardingDataStore.edit { prefs ->
            val prefix = "onboarding_task_${task.id}"
            prefs[booleanPreferencesKey("${prefix}_dismissed")] = true
            prefs[longPreferencesKey("${prefix}_dismissed_at")] = nowMillis
            prefs[longPreferencesKey("${prefix}_snoozed_until")] = 0L
        }
    }

    suspend fun snooze(task: OnboardingTask, untilMillis: Long) {
        context.onboardingDataStore.edit { prefs ->
            val prefix = "onboarding_task_${task.id}"
            prefs[longPreferencesKey("${prefix}_snoozed_until")] = untilMillis
            prefs[booleanPreferencesKey("${prefix}_dismissed")] = false
        }
    }

    suspend fun finish() {
        context.onboardingDataStore.edit { prefs -> prefs[COMPLETED] = true }
    }

    suspend fun revisit() {
        context.onboardingDataStore.edit { prefs ->
            prefs.remove(COMPLETED)
            prefs[VERSION] = OnboardingProgress.CURRENT_VERSION
        }
    }

    /**
     * Explicit, install-scoped reset. Deliberately not called by account
     * switches, local resets or project wipes.
     */
    suspend fun clearProgress() {
        context.onboardingDataStore.edit { it.clear() }
    }

    private companion object {
        val COMPLETED = booleanPreferencesKey("onboarding_completed")
        val VERSION = intPreferencesKey("onboarding_version")
    }
}
