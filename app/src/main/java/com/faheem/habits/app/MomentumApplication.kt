package com.faheem.habits.app

import android.app.Application
import com.faheem.habits.core.settings.AppearancePreferences
import com.faheem.habits.features.battery.AppBatteryUsageRecorder
import dagger.hilt.android.HiltAndroidApp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.faheem.habits.features.screen_time.UsageLimitWorker
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.sync.SupabaseSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class MomentumApplication : Application() {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingSync: Job? = null

    override fun onCreate() {
        super.onCreate()
        AppearancePreferences.load(this)
        AppBatteryUsageRecorder.record(this)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "usage-limit-monitor",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UsageLimitWorker>(15, TimeUnit.MINUTES).build()
        )

        // Only dirty local records schedule work; sync-applied Room writes are clean.
        AppDatabase.get(this).invalidationTracker.addObserver(
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
        syncScope.launch {
            if (SupabaseSyncScheduler.hasDirtyChanges(applicationContext)) {
                SupabaseSyncScheduler.enqueue(applicationContext)
            }
        }
    }
}
