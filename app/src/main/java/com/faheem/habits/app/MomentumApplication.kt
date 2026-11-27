package com.faheem.habits.app

import android.app.Application
import com.faheem.habits.core.settings.AppearancePreferences
import com.faheem.habits.core.settings.MomentumSettingsRepository
import com.faheem.habits.features.battery.AppBatteryUsageRecorder
import com.faheem.habits.features.battery.BatteryChargingMonitorService
import com.faheem.habits.features.network.NetworkIndicatorService
import dagger.hilt.android.HiltAndroidApp
import com.faheem.habits.features.screen_time.UsageLimitSchedule
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.sync.SupabaseSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltAndroidApp
class MomentumApplication : Application() {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingSync: Job? = null

    override fun onCreate() {
        super.onCreate()
        // Only the in-memory appearance snapshot is needed for the first frame.
        AppearancePreferences.load(this)
        syncScope.launch {
            AppearancePreferences.synchronize(this@MomentumApplication, MomentumSettingsRepository(this@MomentumApplication))
            AppBatteryUsageRecorder.record(this@MomentumApplication)
            UsageLimitSchedule.refresh(this@MomentumApplication)

            // Only dirty local records schedule work; sync-applied Room writes are clean.
            // Registering after first-frame work keeps Room/observer setup off Main.
            AppDatabase.get(this@MomentumApplication).invalidationTracker.addObserver(
                object : androidx.room.InvalidationTracker.Observer(
                    "habits", "completions", "habit_skips", "habit_reminders", "expenses",
                    "category_budgets", "loan_people", "loans", "loan_attachments", "loan_payments",
                    "contacts", "library_files", "todo_items", "life_rules", "life_rule_files",
                    "custom_notifications", "vault_entries"
                ) {
                    override fun onInvalidated(tables: Set<String>) {
                        synchronized(this@MomentumApplication) {
                            pendingSync?.cancel()
                            pendingSync = syncScope.launch {
                                delay(750)
                                if (SupabaseSyncScheduler.hasDirtyChanges(applicationContext)) {
                                    SupabaseSyncScheduler.enqueue(applicationContext)
                                }
                            }
                        }
                    }
                }
            )

            if (NetworkIndicatorService.pendingResume(this@MomentumApplication)) {
                NetworkIndicatorService.startIfEnabled(applicationContext)
            }
            if (BatteryChargingMonitorService.pendingResume(this@MomentumApplication)) {
                BatteryChargingMonitorService.startIfEnabled(applicationContext)
            }
            SupabaseSyncScheduler.configurePeriodic(applicationContext)
            // Covers writes that happened before the observer was registered.
            if (SupabaseSyncScheduler.hasDirtyChanges(applicationContext)) {
                SupabaseSyncScheduler.enqueue(applicationContext)
            }
        }
    }
}
