@file:Suppress("FunctionNaming")

package com.faheem.habits.app

import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.lifecycleScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faheem.habits.core.notifications.MomentumReminderScheduler
import com.faheem.habits.core.notifications.ZyloNotifications
import com.faheem.habits.core.settings.AppearancePreferences
import com.faheem.habits.core.settings.OnboardingPhase
import com.faheem.habits.core.ui.AppModule
import com.faheem.habits.core.ui.BrandGradient
import com.faheem.habits.core.ui.MomentumTheme
import com.faheem.habits.core.ui.ScreenBackground
import com.faheem.habits.BuildConfig
import com.faheem.habits.R
import com.faheem.habits.core.data.supabase.SupabaseAuthApi
import com.faheem.habits.core.data.supabase.HttpErrorSanitizer
import com.faheem.habits.core.data.supabase.SessionRestoreResult
import com.faheem.habits.core.data.sync.SupabaseSyncScheduler
import com.faheem.habits.core.data.sync.ProjectWipeManager
import com.faheem.habits.features.settings.PermissionsSetupScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Destination requested by a notification tap. The token lets repeated taps reopen the module. */
data class NotificationTarget(val moduleName: String, val itemId: Int?, val token: Long)

@Volatile
private var lastResumeWipeCheckMs = 0L
private const val RESUME_WIPE_CHECK_INTERVAL_MS = 5 * 60_000L

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
            val dynamicAccent by AppearancePreferences.dynamicAccent.collectAsStateWithLifecycle()
            val glassIntensity by AppearancePreferences.glassIntensity.collectAsStateWithLifecycle()
            MomentumTheme(
                themeMode = themeMode,
                reduceEffects = reduceMotion,
                dynamicAccent = dynamicAccent,
                glassIntensity = glassIntensity.factor
            ) {
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
        val deepLink = intent?.data?.takeIf {
            it.scheme == MomentumRoutes.DEEP_LINK_SCHEME &&
                it.host == MomentumRoutes.DEEP_LINK_MODULE_HOST
        }
        val moduleName = intent?.getStringExtra(ZyloNotifications.EXTRA_TARGET_MODULE)
            ?: deepLink?.pathSegments?.firstOrNull()
            ?: if (
                intent?.data?.scheme == MomentumRoutes.DEEP_LINK_SCHEME &&
                intent.data?.host == MomentumRoutes.DEEP_LINK_AUTH_HOST
            ) AppModule.Profile.name else return null
        val itemId = if (intent?.hasExtra(ZyloNotifications.EXTRA_TARGET_ITEM_ID) == true) {
            intent.getIntExtra(ZyloNotifications.EXTRA_TARGET_ITEM_ID, -1).takeIf { it > 0 }
        } else {
            deepLink?.getQueryParameter(MomentumRoutes.ARG_ITEM_ID)?.toIntOrNull()?.takeIf { it > 0 }
        }
        return NotificationTarget(moduleName, itemId, System.currentTimeMillis())
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
    var destination by remember { mutableStateOf<MomentumDestination>(MomentumDestination.Home) }
    var sessionState by remember { mutableStateOf(AppSessionState.Initial) }
    val permissionSetupSeen by settingsViewModel.permissionSetupSeen.collectAsStateWithLifecycle()
    val accountChoiceSeen by settingsViewModel.accountChoiceSeen.collectAsStateWithLifecycle()
    val moduleVisibility by settingsViewModel.moduleVisibility.collectAsStateWithLifecycle()
    val onboarding by settingsViewModel.onboarding.collectAsStateWithLifecycle()
    var permissionSetupRequested by remember { mutableStateOf(false) }
    var profileInitialCreateAccount by remember { mutableStateOf(false) }
    LaunchedEffect(destination) {
        val isProfile = destination is MomentumDestination.Module &&
            (destination as MomentumDestination.Module).module == AppModule.Profile
        if (!isProfile) profileInitialCreateAccount = false
    }
    // UNKNOWN means DataStore has not answered yet; showing the setup gate here
    // would flash on every cold start. PENDING/DONE are real, persisted states.
    val showAccountChoice = onboarding.phase != OnboardingPhase.UNKNOWN &&
        sessionState.phase == AppSessionPhase.READY &&
        !accountChoiceSeen &&
        !sessionState.cloudSignedIn &&
        !sessionState.requiresAccountAttention
    val showPermissionSetup = !showAccountChoice &&
        onboarding.phase != OnboardingPhase.UNKNOWN &&
        (permissionSetupRequested || !permissionSetupSeen)

    fun closePermissionSetup() {
        permissionSetupRequested = false
        settingsViewModel.markPermissionSetupSeen()
    }

    fun isModuleVisible(module: AppModule): Boolean = moduleVisibility[module.name] ?: true

    fun applySessionEvent(event: AppSessionEvent) {
        sessionState = reduceAppSession(sessionState, event)
        if (sessionState.requiresAccountAttention) {
            destination = MomentumDestination.Module(AppModule.Profile)
        }
    }

    LaunchedEffect(notificationTarget) {
        val target = notificationTarget ?: return@LaunchedEffect
        val module = MomentumRoutes.parseModule(target.moduleName) ?: return@LaunchedEffect
        if (isModuleEntryAllowed(module, { m -> isModuleVisible(m) }, ModuleEntrySource.NOTIFICATION)) {
            destination = MomentumDestination.Module(module, target.itemId, target.token)
        }
    }
    LaunchedEffect(context) {
        data class RestoreOutcome(
            val result: SessionRestoreResult,
            val wipeApplied: Boolean,
            val notice: String?
        )
        val outcome = withContext(Dispatchers.IO) {
            val result = SupabaseAuthApi.restoreSession(context.applicationContext)
            var wipeApplied = false
            var notice: String? = null
            if (result == SessionRestoreResult.AUTHENTICATED) {
                try {
                    val wipeCheck = ProjectWipeManager.applyIfNeeded(context.applicationContext) {
                        settingsViewModel.clearForProjectWipe()
                    }
                    wipeApplied = wipeCheck == ProjectWipeManager.CheckResult.APPLIED
                    if (wipeCheck == ProjectWipeManager.CheckResult.UNCHANGED) {
                        SupabaseSyncScheduler.enqueue(context.applicationContext)
                    }
                } catch (failure: Exception) {
                    // Keep local use available; the sync worker independently checks
                    // the wipe marker before uploading local rows.
                    notice = HttpErrorSanitizer.forDisplay(failure.message)
                }
            }
            RestoreOutcome(result = result, wipeApplied = wipeApplied, notice = notice)
        }
        applySessionEvent(
            AppSessionEvent.RestoreFinished(
                result = outcome.result,
                wipeApplied = outcome.wipeApplied,
                notice = outcome.notice
            )
        )
    }
    LaunchedEffect(Unit) {
        ProjectWipeManager.wipeEvents.collect {
            settingsViewModel.clearForProjectWipe()
            applySessionEvent(AppSessionEvent.WipeApplied)
        }
    }
    LaunchedEffect(Unit) {
        SupabaseAuthApi.signOutEvents.collect { keepLocalData ->
            destination = MomentumDestination.Home
            applySessionEvent(AppSessionEvent.SignedOut(keepLocalData))
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (sessionState.phase == AppSessionPhase.READY) {
            (context as? ComponentActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
                val signedIn = !SupabaseAuthApi.currentUserId(context.applicationContext).isNullOrBlank()
                if (!signedIn) return@launch
                try {
                    val now = System.currentTimeMillis()
                    if (now - lastResumeWipeCheckMs >= RESUME_WIPE_CHECK_INTERVAL_MS) {
                        val wipeCheck = ProjectWipeManager.applyIfNeeded(context.applicationContext) {
                            settingsViewModel.clearForProjectWipe()
                        }
                        if (wipeCheck == ProjectWipeManager.CheckResult.APPLIED) {
                            lastResumeWipeCheckMs = now
                            withContext(Dispatchers.Main) {
                                applySessionEvent(AppSessionEvent.WipeApplied)
                            }
                            return@launch
                        }
                        if (wipeCheck == ProjectWipeManager.CheckResult.UNCHANGED) {
                            lastResumeWipeCheckMs = now
                        }
                    }
                    MomentumReminderScheduler.scheduleAll(context.applicationContext)
                } catch (e: IllegalStateException) {
                    android.util.Log.e("MomentumStartup", "Reminder rescheduling failed.", e)
                } catch (e: SecurityException) {
                    android.util.Log.e("MomentumStartup", "Reminder rescheduling is not permitted.", e)
                }
            }
        }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (sessionState.phase == AppSessionPhase.RESTORING) {
            RestoringSessionScreen(modifier = Modifier.padding(padding))
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                sessionState.notice?.let { notice ->
                    Text(
                        text = notice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { applySessionEvent(AppSessionEvent.NoticeDismissed) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    showAccountChoice -> {
                        AccountChoiceScreen(
                            onSignIn = {
                                profileInitialCreateAccount = false
                                settingsViewModel.markAccountChoiceSeen()
                                destination = MomentumDestination.Module(AppModule.Profile)
                            },
                            onCreateAccount = {
                                profileInitialCreateAccount = true
                                settingsViewModel.markAccountChoiceSeen()
                                destination = MomentumDestination.Module(AppModule.Profile)
                            },
                            onContinueWithoutAccount = {
                                settingsViewModel.markAccountChoiceSeen()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    showPermissionSetup -> {
                        BackHandler { closePermissionSetup() }
                        PermissionsSetupScreen(
                            onBack = { closePermissionSetup() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> {
                        MomentumNavigationHost(
                            destination = destination,
                            cloudSignedIn = sessionState.cloudSignedIn,
                            requiresAccountAttention = sessionState.requiresAccountAttention,
                            offlineNotice = sessionState.notice?.startsWith("Offline", ignoreCase = true) == true,
                            profileInitialCreateAccount = profileInitialCreateAccount,
                            onOpenPermissions = { permissionSetupRequested = true },
                            onOpenSearch = { destination = MomentumDestination.Search },
                            onOpenSearchResult = { module, itemId ->
                                if (isModuleEntryAllowed(module, { m -> isModuleVisible(m) }, ModuleEntrySource.NORMAL)) {
                                    destination = MomentumDestination.Module(module, itemId = itemId)
                                }
                            },
                            onOpenModule = { module ->
                                if (isModuleEntryAllowed(module, { m -> isModuleVisible(m) }, ModuleEntrySource.NORMAL)) {
                                    profileInitialCreateAccount = false
                                    destination = MomentumDestination.Module(module)
                                }
                            },
                            onBack = {
                                if (destination is MomentumDestination.Module &&
                                    (destination as MomentumDestination.Module).module == AppModule.Profile
                                ) {
                                    profileInitialCreateAccount = false
                                }
                                destination = MomentumDestination.Home
                            },
                            modifier = Modifier.weight(1f),
                            isModuleVisible = { module -> isModuleVisible(module) },
                            entrySource = ModuleEntrySource.DEEP_LINK
                        )
                    }
                }
            }
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
                        painter = painterResource(R.drawable.zylo_launcher),
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
