package com.faheem.habits.app

import android.app.Application
import com.faheem.habits.core.settings.AppearancePreferences
import com.faheem.habits.features.battery.AppBatteryUsageRecorder
import dagger.hilt.android.HiltAndroidApp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.faheem.habits.features.screen_time.UsageLimitWorker
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class MomentumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppearancePreferences.load(this)
        AppBatteryUsageRecorder.record(this)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "usage-limit-monitor",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UsageLimitWorker>(15, TimeUnit.MINUTES).build()
        )
    }
}
