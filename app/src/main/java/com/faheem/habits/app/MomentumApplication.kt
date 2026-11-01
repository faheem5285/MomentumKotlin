package com.faheem.habits.app

import android.app.Application
import com.faheem.habits.core.settings.AppearancePreferences
import com.faheem.habits.features.battery.AppBatteryUsageRecorder
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MomentumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppearancePreferences.load(this)
        AppBatteryUsageRecorder.record(this)
    }
}
