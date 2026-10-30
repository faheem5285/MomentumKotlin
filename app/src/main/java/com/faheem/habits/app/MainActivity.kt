package com.faheem.habits.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.faheem.habits.core.notifications.MomentumReminderScheduler
import com.faheem.habits.core.ui.MomentumTheme
import com.faheem.habits.features.battery.handleAutomaticBatteryPowerEvent
import com.faheem.habits.features.auth.AuthScreen
import com.faheem.habits.core.data.supabase.SupabaseAuthApi
import com.faheem.habits.core.data.supabase.SessionRestoreResult
import com.faheem.habits.features.settings.PermissionsSetupScreen

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
    var isAuthenticated by remember { mutableStateOf(false) }
    var isRestoringSession by remember { mutableStateOf(true) }
    var authNotice by remember { mutableStateOf<String?>(null) }
    var showPermissionSetup by remember {
        mutableStateOf(
            !context.getSharedPreferences("momentum_app_settings", android.content.Context.MODE_PRIVATE)
                .getBoolean("permission_setup_seen", false)
        )
    }
    LaunchedEffect(context) {
        when (SupabaseAuthApi.restoreSession(context.applicationContext)) {
            SessionRestoreResult.AUTHENTICATED -> isAuthenticated = true
            SessionRestoreResult.NO_SESSION -> Unit
            SessionRestoreResult.TEMPORARY_FAILURE -> authNotice = "Could not verify your saved session. Check your internet connection and sign in again if needed. Your saved session has been kept."
        }
        isRestoringSession = false
    }
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
        if (isAuthenticated) {
            if (showPermissionSetup) {
                PermissionsSetupScreen(
                    onBack = {
                        context.getSharedPreferences("momentum_app_settings", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("permission_setup_seen", true).apply()
                        showPermissionSetup = false
                    },
                    modifier = Modifier.padding(padding)
                )
            } else {
                MomentumNavigationHost(
                    destination = destination,
                    onOpenModule = { destination = MomentumDestination.Module(it) },
                    onBack = { destination = MomentumDestination.Home },
                    modifier = Modifier.padding(padding)
                )
            }
        } else if (isRestoringSession) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            AuthScreen(initialMessage = authNotice, onAuthenticated = { isAuthenticated = true })
        }
    }
}
