package com.faheem.habits.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.faheem.habits.core.notifications.MomentumReminderScheduler
import com.faheem.habits.core.ui.MomentumTheme
import com.faheem.habits.features.battery.handleAutomaticBatteryPowerEvent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MomentumReminderScheduler.createChannels(this)
        MomentumReminderScheduler.scheduleAll(this)
        enableEdgeToEdge()
        setContent {
            MomentumTheme { MomentumApp() }
        }
    }
}

@Composable
fun MomentumApp() {
    val context = LocalContext.current
    var destination by remember { mutableStateOf<MomentumDestination>(MomentumDestination.Home) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { MomentumReminderScheduler.scheduleAll(context) }
    DisposableEffect(context) {
        val appContext = context.applicationContext
        val powerEvents = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                handleAutomaticBatteryPowerEvent(appContext, intent?.action, "Momentum foreground listener")
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(appContext, powerEvents, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { appContext.unregisterReceiver(powerEvents) } }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        MomentumNavigationHost(
            destination = destination,
            onOpenModule = { destination = MomentumDestination.Module(it) },
            onBack = { destination = MomentumDestination.Home },
            modifier = Modifier.padding(padding)
        )
    }
}

