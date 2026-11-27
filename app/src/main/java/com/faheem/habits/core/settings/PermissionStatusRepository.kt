package com.faheem.habits.core.settings

import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService
import com.faheem.habits.features.screen_time.InstagramScrollCounterService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for OS permission status checks. */
@Singleton
class PermissionStatusRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun snapshot(): PermissionStatuses = PermissionStatuses(
        PermissionKind.entries.associateWith(::status)
    )

    fun status(kind: PermissionKind): PermissionStatus = when (kind) {
        PermissionKind.NOTIFICATIONS -> notificationsGranted().asStatus()
        PermissionKind.EXACT_ALARMS -> exactAlarmsGranted().asStatus()
        PermissionKind.USAGE_ACCESS -> usageAccessGranted().asStatus()
        PermissionKind.OVERLAY -> overlayGranted().asStatus(supported = Build.VERSION.SDK_INT >= 23)
        PermissionKind.ACCESSIBILITY -> accessibilityEnabled().asStatus()
        PermissionKind.BATTERY_OPTIMIZATION -> batteryUnrestricted().asStatus(supported = Build.VERSION.SDK_INT >= 23)
        PermissionKind.AUTO_START, PermissionKind.BACKGROUND_RESTRICTIONS -> PermissionStatus.UNSUPPORTED
    }

    private fun Boolean.asStatus(supported: Boolean = true): PermissionStatus = when {
        !supported -> PermissionStatus.UNSUPPORTED
        this -> PermissionStatus.GRANTED
        else -> PermissionStatus.DENIED
    }

    private fun notificationsGranted(): Boolean {
        val notificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        val runtimeGranted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return notificationsEnabled && runtimeGranted
    }

    private fun exactAlarmsGranted(): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true

    private fun usageAccessGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val ops = context.getSystemService<AppOpsManager>() ?: return false
        @Suppress("DEPRECATION")
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun overlayGranted(): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    private fun accessibilityEnabled(): Boolean = InstagramScrollCounterService.isEnabled(context)

    private fun batteryUnrestricted(): Boolean =
        Build.VERSION.SDK_INT < 23 ||
            context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) == true
}
