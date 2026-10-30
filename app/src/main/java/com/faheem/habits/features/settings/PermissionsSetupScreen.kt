package com.faheem.habits.features.settings

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.os.PowerManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private enum class PermissionState { ENABLED, DISABLED, UNAVAILABLE }

private data class SetupPermission(
    val title: String,
    val description: String,
    val featureImpact: String,
    val state: PermissionState,
    val action: () -> Unit
)

@Composable
fun PermissionsSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var openingSettings by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    fun openSettings(primary: Intent, fallback: Intent = appDetailsIntent(context)) {
        try {
            if (primary.resolveActivity(context.packageManager) != null) {
                context.startActivity(primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else if (fallback.resolveActivity(context.packageManager) != null) {
                context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                openingSettings++
            }
        } catch (_: Exception) {
            try {
                context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) { openingSettings++ }
        }
    }

    fun notificationGranted(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun exactAlarmGranted(): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()

    fun usageGranted(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    fun overlayGranted(): Boolean = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)
    fun batteryUnrestricted(): Boolean = Build.VERSION.SDK_INT < 23 ||
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    val permissions = remember(refresh, openingSettings) {
        listOf(
            SetupPermission("Notifications", "Allow Momentum to show reminders and ongoing network status notifications.", "Without it, reminders and the network notification may be hidden.", if (notificationGranted()) PermissionState.ENABLED else PermissionState.DISABLED) {
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            },
            SetupPermission("Display over other apps", "Allows the optional floating network indicator to appear above other apps.", "The floating network indicator cannot appear; other network features remain available.", if (overlayGranted()) PermissionState.ENABLED else if (Build.VERSION.SDK_INT >= 23) PermissionState.DISABLED else PermissionState.UNAVAILABLE) {
                if (Build.VERSION.SDK_INT >= 23) openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            },
            SetupPermission("Alarms & reminders", "Allows scheduled reminders to fire at their intended time on Android 12 and newer.", "Reminders may be delayed or less precise.", if (exactAlarmGranted()) PermissionState.ENABLED else PermissionState.DISABLED) {
                if (Build.VERSION.SDK_INT >= 31) openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            },
            SetupPermission("Usage access", "Reads app usage totals to power Screen Time and network data summaries.", "Screen Time totals and Wi-Fi/mobile usage summaries will be unavailable.", if (usageGranted()) PermissionState.ENABLED else PermissionState.DISABLED) {
                openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            },
            SetupPermission("Accessibility service", "Counts Instagram scroll events for the optional Reels estimate. Momentum does not read screen text.", "The Reels scroll estimate will remain off.", if (com.faheem.habits.features.screen_time.InstagramScrollCounterService.isEnabled(context)) PermissionState.ENABLED else PermissionState.DISABLED) {
                openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            SetupPermission("Background autostart", "Some manufacturers provide an additional autostart switch for reliable background reminders and monitoring.", "Background work may be stopped by manufacturer-specific power management.", PermissionState.UNAVAILABLE) {
                openSettings(appDetailsIntent(context))
            },
            SetupPermission("Battery optimization", "Excluding Momentum from battery optimization can improve reliability of user-requested background reminders.", "Android or the manufacturer may delay background work. This setting is optional.", if (batteryUnrestricted()) PermissionState.ENABLED else PermissionState.DISABLED) {
                openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            },
            SetupPermission("Background restrictions", "Review Android's per-app battery and background usage controls.", "Restricted apps may have delayed reminders or background updates.", PermissionState.UNAVAILABLE) {
                openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
        )
    }
    val applicable = permissions.filter { it.state != PermissionState.UNAVAILABLE }
    val configured = applicable.count { it.state == PermissionState.ENABLED }
    val total = applicable.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
                Column {
                    Text("Permissions & background", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Set up Android access for Momentum", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$configured of $total configured", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { if (total == 0) 0f else configured.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    Text("Only permissions supported by this Android version are included in progress. Optional access can be left off.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Android settings names and availability vary by version and manufacturer, especially Xiaomi/Redmi. If a page does not open, find Momentum under Settings → Apps or Security/Permissions, then review Autostart, Battery saver and Background restrictions. Momentum cannot change restricted settings for you.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(permissions.size) { index ->
            val item = permissions[index]
            PermissionSetupCard(item)
        }
        item {
            Text("No permission is required to use the basic habit, finance, files or password-vault screens. Grant only the optional access for features you want.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (openingSettings > 0) item {
            Text("Could not open that settings page. Open Android Settings → Apps → Momentum and locate the setting manually.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PermissionSetupCard(item: SetupPermission) {
    val (label, tint) = when (item.state) {
        PermissionState.ENABLED -> "Enabled" to MaterialTheme.colorScheme.primary
        PermissionState.DISABLED -> "Disabled" to MaterialTheme.colorScheme.error
        PermissionState.UNAVAILABLE -> "Not available on this device" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (item.state == PermissionState.ENABLED) Icons.Rounded.CheckCircle else Icons.Rounded.Security, contentDescription = null, tint = tint)
                Spacer(Modifier.width(10.dp))
                Text(item.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(item.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(item.featureImpact, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.state == PermissionState.UNAVAILABLE) {
                OutlinedButton(onClick = item.action, modifier = Modifier.fillMaxWidth()) { Text("View app settings") }
            } else if (item.state == PermissionState.ENABLED) {
                OutlinedButton(onClick = item.action, modifier = Modifier.fillMaxWidth()) { Text("Manage setting") }
            } else {
                Button(onClick = item.action, modifier = Modifier.fillMaxWidth()) { Text("Enable / configure") }
            }
        }
    }
}

private fun appDetailsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
