@file:Suppress("FunctionNaming")

package com.faheem.habits.app

import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.lifecycleScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faheem.habits.core.notifications.MomentumReminderScheduler
import com.faheem.habits.core.notifications.ZyloNotifications
import com.faheem.habits.core.settings.AppearancePreferences
import com.faheem.habits.core.ui.AppModule
import com.faheem.habits.core.ui.BrandGradient
import com.faheem.habits.core.ui.MomentumTheme
import com.faheem.habits.core.ui.ScreenBackground
import com.faheem.habits.BuildConfig
import com.faheem.habits.R
import com.faheem.habits.features.battery.handleAutomaticBatteryPowerEvent
import com.faheem.habits.features.auth.AuthScreen
import com.faheem.habits.core.data.supabase.SupabaseAuthApi
import com.faheem.habits.core.data.supabase.SessionRestoreResult
import com.faheem.habits.core.data.sync.SupabaseSyncScheduler
import com.faheem.habits.core.data.sync.ProjectWipeManager
import com.faheem.habits.features.settings.PermissionsSetupScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Destination requested by a notification tap. The token lets repeated taps reopen the module. */
data class NotificationTarget(val moduleName: String, val token: Long)

@dagger.hilt.android.AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private var notificationTarget by mutableStateOf<NotificationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationTarget = notificationTargetFrom(intent)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build()
            )
        }
        scheduleRemindersSafely()
        enableEdgeToEdge()
        setContent {
            val themeMode by AppearancePreferences.themeMode.collectAsStateWithLifecycle()
            val reduceMotion by AppearancePreferences.reduceMotion.collectAsStateWithLifecycle()
            MomentumTheme(themeMode = themeMode, reduceEffects = reduceMotion) {
                MomentumApp(notificationTarget = notificationTarget)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationTarget = notificationTargetFrom(intent)
    }

    private fun notificationTargetFrom(intent: Intent?): NotificationTarget? {
        val moduleName = intent?.getStringExtra(ZyloNotifications.EXTRA_TARGET_MODULE) ?: return null
        return NotificationTarget(moduleName, System.currentTimeMillis())
    }

    private fun scheduleRemindersSafely() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                MomentumReminderScheduler.scheduleAll(applicationContext)
            } catch (e: IllegalStateException) {
                Log.e("MomentumStartup", "Reminder scheduling was skipped during startup.", e)
            } catch (e: SecurityException) {
                Log.e("MomentumStartup", "Reminder scheduling is not permitted on this device.", e)
            }
        }
    }
}

@Composable
fun MomentumApp(
    settingsViewModel: com.faheem.habits.features.settings.SettingsViewModel = hiltViewModel(),
    notificationTarget: NotificationTarget? = null
) {
    val context = LocalContext.current
    val appScope = rememberCoroutineScope()
    var destination by remember { mutableStateOf<MomentumDestination>(MomentumDestination.Home) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var isRestoringSession by remember { mutableStateOf(true) }
    var authNotice by remember { mutableStateOf<String?>(null) }
    val permissionSetupSeen by settingsViewModel.permissionSetupSeen.collectAsStateWithLifecycle()
    val showPermissionSetup = !permissionSetupSeen
    LaunchedEffect(notificationTarget) {
        val moduleName = notificationTarget?.moduleName ?: return@LaunchedEffect
        AppModule.entries.firstOrNull { it.name.equals(moduleName, true) }?.let {
            destination = MomentumDestination.Module(it)
        }
    }
    LaunchedEffect(context) {
        when (SupabaseAuthApi.restoreSession(context.applicationContext)) {
            SessionRestoreResult.AUTHENTICATED -> {
                try {
                    val wiped = ProjectWipeManager.applyIfNeeded(context.applicationContext) {
                        settingsViewModel.clearForProjectWipe()
                    }
                    if (wiped) {
                        authNotice = "An administrator reset app data. Sign in again to start fresh."
                    } else {
                        isAuthenticated = true
                        SupabaseSyncScheduler.enqueue(context.applicationContext)
                    }
                } catch (failure: Exception) {
                    // Keep local use available, but the sync worker also checks the wipe marker
                    // before it can upload any local rows.
                    isAuthenticated = true
                    authNotice = failure.message
                }
            }
            SessionRestoreResult.NO_SESSION -> Unit
            SessionRestoreResult.TEMPORARY_FAILURE -> authNotice = "Could not verify your saved session. Check your internet connection and sign in again if needed. Your saved session has been kept."
        }
        isRestoringSession = false
    }
    LaunchedEffect(Unit) {
        ProjectWipeManager.wipeEvents.collect {
            settingsViewModel.clearForProjectWipe()
            destination = MomentumDestination.Home
            isAuthenticated = false
            isRestoringSession = false
            authNotice = "An administrator reset app data. Sign in again to start fresh."
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (isAuthenticated) (context as? ComponentActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
            try {
                val wiped = ProjectWipeManager.applyIfNeeded(context.applicationContext) {
                    settingsViewModel.clearForProjectWipe()
                }
                if (wiped) {
                    withContext(Dispatchers.Main) {
                        destination = MomentumDestination.Home
                        isAuthenticated = false
                        authNotice = "An administrator reset app data. Sign in again to start fresh."
                    }
                    return@launch
                }
                MomentumReminderScheduler.scheduleAll(context.applicationContext)
            } catch (e: IllegalStateException) {
                android.util.Log.e("MomentumStartup", "Reminder rescheduling failed.", e)
            } catch (e: SecurityException) {
                android.util.Log.e("MomentumStartup", "Reminder rescheduling is not permitted.", e)
            }
        }
    }
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
                        settingsViewModel.markPermissionSetupSeen()
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
            RestoringSessionScreen(modifier = Modifier.padding(padding))
        } else {
            AuthScreen(
                initialMessage = authNotice,
                onAuthenticated = {
                    appScope.launch {
                        try {
                            val wiped = ProjectWipeManager.applyIfNeeded(context.applicationContext) {
                                settingsViewModel.clearForProjectWipe()
                            }
                            if (wiped) {
                                authNotice = "An administrator reset app data. Sign in again to start fresh."
                            } else {
                                isAuthenticated = true
                                SupabaseSyncScheduler.enqueue(context.applicationContext)
                            }
                        } catch (failure: Exception) {
                            authNotice = failure.message
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun RestoringSessionScreen(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    ScreenBackground(modifier = modifier, tint = BrandGradient.first()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(72.dp)
                        .shadow(
                            22.dp,
                            shape,
                            clip = false,
                            ambientColor = BrandGradient.first().copy(alpha = 0.60f),
                            spotColor = BrandGradient.last().copy(alpha = 0.50f)
                        )
                        .clip(shape)
                        .border(1.dp, Color.White.copy(alpha = 0.40f), shape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.zylo_logo),
                        contentDescription = "Zylo logo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Zylo",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.8).sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Preparing your space",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp
                )
            }
        }
    }
}
