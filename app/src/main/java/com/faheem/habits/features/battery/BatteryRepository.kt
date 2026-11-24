package com.faheem.habits.features.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

@Immutable
data class BatteryDashboardSnapshot(
    val reading: BatteryReading,
    val diagnostic: BatteryDiagnostic,
    val sessions: List<BatteryChargeSession>,
    val levels: List<BatteryLevelSample>,
    val isLoading: Boolean = false
)

@Singleton
class BatteryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    internal fun observeDashboard(): Flow<BatteryDashboardSnapshot> = callbackFlow {
        var wasCharging: Boolean? = null
        fun publish(intent: Intent?, action: String? = intent?.action) {
            val batteryIntent = if (action == Intent.ACTION_BATTERY_CHANGED) intent
            else context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val parsed = batteryReading(batteryIntent)
            val reading = parsed.copy(minutesToFull = platformChargingEtaMinutes(context, parsed.charging))
            when (action) {
                Intent.ACTION_POWER_CONNECTED -> BatteryAnimationDiagnostics.record(
                    context, "Plug-in signal received by open Battery screen",
                    "The live receiver observed ACTION_POWER_CONNECTED. The manifest receiver may separately launch the overlay."
                )
                Intent.ACTION_POWER_DISCONNECTED -> BatteryAnimationDiagnostics.record(
                    context, "Unplug signal received by open Battery screen",
                    "The live receiver observed ACTION_POWER_DISCONNECTED."
                )
                Intent.ACTION_BATTERY_CHANGED -> {
                    val previous = wasCharging
                    if (previous != null && reading.charging != previous) {
                        BatteryAnimationDiagnostics.record(
                            context,
                            if (reading.charging) "Live battery state changed to charging" else "Live battery state changed to unplugged",
                            "The device state changed to ${if (reading.charging) "charging" else "on battery"} (${reading.source}, ${reading.percent}%)."
                        )
                    }
                    BatteryLevelHistory.record(context, reading.percent, reading.charging)
                    BatteryUsageEstimator.ensureDailyAnchor(context)
                }
            }
            wasCharging = reading.charging
            trySend(
                BatteryDashboardSnapshot(
                    reading = reading,
                    diagnostic = BatteryAnimationDiagnostics.latest(context),
                    sessions = BatterySessionHistory.recent(context),
                    levels = BatteryLevelHistory.samples(context)
                )
            )
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                publish(intent)
            }
        }
        val sticky = ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        publish(sticky, Intent.ACTION_BATTERY_CHANGED)
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }.conflate()
}
