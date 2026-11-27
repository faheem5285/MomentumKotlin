@file:Suppress("FunctionNaming", "LongParameterList", "LongMethod", "CyclomaticComplexMethod", "TooManyFunctions")

package com.faheem.habits.features.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faheem.habits.core.data.backup.MomentumBackup
import com.faheem.habits.core.data.supabase.HttpErrorSanitizer
import com.faheem.habits.core.notifications.ReminderSettingsScreen
import com.faheem.habits.core.settings.AccountStatus
import com.faheem.habits.core.settings.AppearancePreferences
import com.faheem.habits.core.settings.AppearanceValues
import com.faheem.habits.core.settings.GlassIntensity
import com.faheem.habits.core.settings.OnboardingSignals
import com.faheem.habits.core.settings.PermissionStatusRepository
import com.faheem.habits.core.settings.SettingsSection
import com.faheem.habits.core.settings.ThemeMode
import com.faheem.habits.core.ui.AppModule
import com.faheem.habits.core.ui.IconBubble
import com.faheem.habits.core.ui.InfoBanner
import com.faheem.habits.core.ui.MomentumChip
import com.faheem.habits.core.ui.MomentumDialog
import com.faheem.habits.core.ui.MomentumTokens
import com.faheem.habits.core.ui.Positive
import com.faheem.habits.core.ui.PremiumCard
import com.faheem.habits.core.ui.SecondaryButton
import com.faheem.habits.core.ui.SettingsGroup
import com.faheem.habits.core.ui.SettingsRow
import com.faheem.habits.features.home.OnboardingCards
import kotlinx.coroutines.launch

@Composable
fun AppSettingsScreen(
    onOpenModule: (AppModule) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    cloudSignedIn: Boolean = true,
    requiresAccountAttention: Boolean = false,
    offlineNotice: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var remindersOpen by remember { mutableStateOf(false) }
    var permissionsOpen by remember { mutableStateOf(false) }
    var onboardingOpen by remember { mutableStateOf(false) }
    var hiddenModulesOpen by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<Pair<Uri, MomentumBackup.Preview>?>(null) }
    val themeMode by AppearancePreferences.themeMode.collectAsStateWithLifecycle()
    val reduceMotion by AppearancePreferences.reduceMotion.collectAsStateWithLifecycle()
    val dynamicAccent by AppearancePreferences.dynamicAccent.collectAsStateWithLifecycle()
    val glassIntensity by AppearancePreferences.glassIntensity.collectAsStateWithLifecycle()
    val appearance = AppearanceValues(themeMode, reduceMotion, dynamicAccent, glassIntensity)
    val moduleVisibility by settingsViewModel.moduleVisibility.collectAsStateWithLifecycle()
    val hiddenModules = remember(moduleVisibility) {
        settingsViewModel.hiddenModuleNames(moduleVisibility)
    }
    val onboarding by settingsViewModel.onboarding.collectAsStateWithLifecycle()
    val permissionRepository = remember(context) { PermissionStatusRepository(context) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val permissionStatuses = remember(permissionTick, permissionRepository) { permissionRepository.snapshot() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionTick++ }
    val scope = rememberCoroutineScope()
    val accountSubtext = AccountStatus.subtext(cloudSignedIn, requiresAccountAttention, offlineNotice)
    fun isVisible(module: AppModule): Boolean = moduleVisibility[module.name] != false
    fun backupSecret(): CharArray? = backupPassword.takeIf { it.isNotBlank() }?.toCharArray()
    fun clearBackupSecret() { backupPassword = "" }
    fun failureMessage(action: String, failure: Exception): String =
        "$action failed: ${HttpErrorSanitizer.forDisplay(failure.message)}"

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            backupBusy = true
            val secret = backupSecret()
            backupMessage = try {
                MomentumBackup.export(context, uri, secret)
                if (secret == null) {
                    "Backup exported as JSON. It is not encrypted; Vault secrets and file contents are excluded."
                } else {
                    "Backup exported with password encryption. Vault secrets and file contents are excluded."
                }
            } catch (e: Exception) {
                failureMessage("Export", e)
            } finally {
                secret?.fill('\u0000')
                clearBackupSecret()
                backupBusy = false
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            backupBusy = true
            val secret = backupSecret()
            try {
                pendingImport = uri to MomentumBackup.preview(context, uri, secret)
            } catch (e: Exception) {
                backupMessage = failureMessage("Import preview", e)
                secret?.fill('\u0000')
                clearBackupSecret()
            } finally {
                backupBusy = false
            }
        }
    }
    pendingImport?.let { (uri, preview) ->
        MomentumDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import preview") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This backup contains ${preview.recordCount} records, including habits, tasks, " +
                            "expenses, loans, contacts, files, language notes, and translation history."
                    )
                    Text(
                        if (preview.encrypted) {
                            "This backup is password-protected. The password is used only to open it on this device."
                        } else {
                            "This backup is unencrypted JSON. It may contain contact details and other personal data."
                        }
                    )
                    Text(
                        "Merge adds missing records. Replace deletes existing backup-supported records, " +
                            "resets cloud sync cursors, and restores appearance settings; Vault entries and " +
                            "file contents are never imported."
                    )
                    Text("Older v1–v3 backups remain importable and gain empty language tables.")
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        pendingImport = null
                        scope.launch {
                            backupBusy = true
                            val secret = backupSecret()
                            backupMessage = try {
                                MomentumBackup.restore(context, uri, replace = true, passphrase = secret).also {
                                    settingsViewModel.syncAppearance()
                                }
                            } catch (e: Exception) {
                                failureMessage("Import", e)
                            } finally {
                                secret?.fill('\u0000')
                                clearBackupSecret()
                                backupBusy = false
                            }
                        }
                    }) { Text("Replace records") }
                    TextButton(onClick = {
                        pendingImport = null
                        scope.launch {
                            backupBusy = true
                            val secret = backupSecret()
                            backupMessage = try {
                                MomentumBackup.restore(context, uri, replace = false, passphrase = secret).also {
                                    settingsViewModel.syncAppearance()
                                }
                            } catch (e: Exception) {
                                failureMessage("Import", e)
                            } finally {
                                secret?.fill('\u0000')
                                clearBackupSecret()
                                backupBusy = false
                            }
                        }
                    }) { Text("Merge") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingImport = null
                    clearBackupSecret()
                }) { Text("Cancel") }
            }
        )
    }
    if (hiddenModulesOpen) {
        MomentumDialog(
            onDismissRequest = { hiddenModulesOpen = false },
            title = { Text("Hidden modules") },
            text = {
                Text(
                    if (hiddenModules.isEmpty()) {
                        "All modules are already visible on Home."
                    } else {
                        "Hidden from Home: ${hiddenModules.joinToString(", ")}. " +
                            "You can restore them without changing any records."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    settingsViewModel.showAllModules()
                    hiddenModulesOpen = false
                }) { Text("Show all modules") }
            },
            dismissButton = {
                TextButton(onClick = { hiddenModulesOpen = false }) { Text("Close") }
            }
        )
    }
    BackHandler(enabled = remindersOpen || permissionsOpen || onboardingOpen || hiddenModulesOpen) {
        when {
            permissionsOpen -> permissionsOpen = false
            remindersOpen -> remindersOpen = false
            hiddenModulesOpen -> hiddenModulesOpen = false
            else -> onboardingOpen = false
        }
    }
    if (permissionsOpen) {
        PermissionsSetupScreen(onBack = { permissionsOpen = false })
        return
    }
    if (remindersOpen) {
        ReminderSettingsScreen(onBack = { remindersOpen = false })
        return
    }
    if (onboardingOpen) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = MomentumTokens.screenPadding,
                vertical = MomentumTokens.space2
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OnboardingCards(
                    progress = onboarding,
                    signals = OnboardingSignals(permissions = permissionStatuses),
                    onOpenModule = onOpenModule,
                    onOpenPermissions = { permissionsOpen = true },
                    onDone = settingsViewModel::completeOnboardingTask,
                    onDismiss = settingsViewModel::dismissOnboardingTask,
                    onSnooze = { task ->
                        settingsViewModel.snoozeOnboardingTask(
                            task,
                            System.currentTimeMillis() + 24L * 60 * 60 * 1000
                        )
                    },
                    onFinish = {
                        settingsViewModel.finishOnboarding()
                        onboardingOpen = false
                    }
                )
            }
            item {
                TextButton(onClick = { onboardingOpen = false }) { Text("Back to settings") }
            }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = MomentumTokens.screenPadding,
            vertical = MomentumTokens.space2
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsGroup(SettingsSection.APPEARANCE.title) {
                AppearanceCard(values = appearance, onChange = settingsViewModel::setAppearance)
            }
        }
        item {
            SettingsGroup(SettingsSection.NOTIFICATIONS.title) {
                SettingsRow(
                    title = "Notifications & reminders",
                    supportingText = "Manage schedules, categories and channels",
                    icon = Icons.Rounded.NotificationsActive,
                    onClick = { remindersOpen = true }
                )
            }
        }
        item {
            SettingsGroup(SettingsSection.PRIVACY_AND_SECURITY.title) {
                SettingsRow(
                    title = "Permissions & background setup",
                    supportingText = permissionStatuses.enabledSummary,
                    icon = Icons.Rounded.Security,
                    onClick = { permissionsOpen = true }
                )
                SettingsRow(
                    title = "Vault",
                    supportingText = if (isVisible(AppModule.Vault)) {
                        "Encrypted private records"
                    } else {
                        "Hidden from Home · restore in Hidden modules"
                    },
                    icon = Icons.Rounded.Lock,
                    onClick = {
                        if (isVisible(AppModule.Vault)) onOpenModule(AppModule.Vault) else hiddenModulesOpen = true
                    }
                )
                SettingsRow(
                    title = "Hidden modules",
                    supportingText = if (hiddenModules.isEmpty()) {
                        "All modules are visible"
                    } else {
                        "${hiddenModules.size} hidden · tap to restore"
                    },
                    icon = Icons.Rounded.Visibility,
                    onClick = { hiddenModulesOpen = true }
                )
            }
        }
        item {
            SettingsGroup(SettingsSection.DATA_AND_BACKUP.title) {
                BackupRestoreCard(
                    busy = backupBusy,
                    message = backupMessage,
                    password = backupPassword,
                    onPasswordChange = { backupPassword = it },
                    onExport = { exportLauncher.launch("momentum-backup.json") },
                    onRestore = { importLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }
                )
                HorizontalDivider(
                    Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                SettingsRow(
                    title = "Search all records",
                    supportingText = "Habits, tasks, expenses, contacts, files and life rules; never Vault",
                    icon = Icons.Rounded.Search,
                    onClick = onOpenSearch
                )
            }
        }
        item {
            SettingsGroup(SettingsSection.CLOUD_AND_SYNC.title) {
                SettingsRow(
                    title = "Cloud & Sync",
                    supportingText = accountSubtext,
                    icon = Icons.Rounded.CloudDone,
                    onClick = { onOpenModule(AppModule.Supabase) }
                )
            }
        }
        item {
            SettingsGroup(SettingsSection.SCREEN_TIME_AND_DEVICE.title) {
                DeviceSettingsRow(AppModule.Screen, "Usage limits and focus", isVisible(AppModule.Screen)) {
                    if (isVisible(AppModule.Screen)) onOpenModule(AppModule.Screen) else hiddenModulesOpen = true
                }
                DeviceSettingsRow(AppModule.Battery, "Charging and battery insights", isVisible(AppModule.Battery)) {
                    if (isVisible(AppModule.Battery)) onOpenModule(AppModule.Battery) else hiddenModulesOpen = true
                }
                DeviceSettingsRow(AppModule.Network, "Wi-Fi and mobile data", isVisible(AppModule.Network)) {
                    if (isVisible(AppModule.Network)) onOpenModule(AppModule.Network) else hiddenModulesOpen = true
                }
            }
        }
        item {
            SettingsGroup(SettingsSection.ACCOUNT.title) {
                SettingsRow(
                    title = "Profile",
                    supportingText = accountSubtext,
                    icon = Icons.Rounded.Person,
                    onClick = { onOpenModule(AppModule.Profile) }
                )
            }
        }
        item {
            SettingsGroup(SettingsSection.ABOUT.title) {
                SettingsRow(
                    title = "Getting started",
                    supportingText = "Review the first-install setup cards",
                    icon = Icons.Rounded.FactCheck,
                    onClick = {
                        settingsViewModel.revisitOnboarding()
                        onboardingOpen = true
                    }
                )
                SettingsRow(
                    title = "Privacy",
                    supportingText = "Local-first by design. Cloud sign-in is optional; Vault stays encrypted.",
                    icon = Icons.Rounded.Info
                )
            }
        }
    }
}

@Composable
private fun DeviceSettingsRow(
    module: AppModule,
    visibleSubtitle: String,
    visible: Boolean,
    onClick: () -> Unit
) {
    SettingsRow(
        title = module.label,
        supportingText = if (visible) visibleSubtitle else "Hidden from Home · restore in Hidden modules",
        icon = when (module) {
            AppModule.Screen -> Icons.Rounded.Timer
            AppModule.Battery -> Icons.Rounded.BatteryFull
            else -> Icons.Rounded.SignalCellularAlt
        },
        onClick = onClick
    )
}

@Composable
private fun AppearanceCard(
    values: AppearanceValues,
    onChange: (AppearanceValues) -> Unit
) {
    PremiumCard(accent = AppModule.Settings.accent, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Palette, AppModule.Settings.accent, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Look & feel", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "Theme, motion and accent are saved in backups",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                MomentumChip(
                    selected = values.themeMode == mode,
                    onClick = { onChange(values.copy(themeMode = mode)) },
                    label = mode.label,
                    icon = when (mode) {
                        ThemeMode.System -> Icons.Rounded.Contrast
                        ThemeMode.Light -> Icons.Rounded.LightMode
                        ThemeMode.Dark -> Icons.Rounded.DarkMode
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        SettingsRow(
            title = "Reduce motion",
            supportingText = "Use immediate transitions and remove decorative animation",
            checked = values.reduceMotion,
            onCheckedChange = { onChange(values.copy(reduceMotion = it)) }
        )
        SettingsRow(
            title = "Dynamic accent",
            supportingText = "Follow the system accent colour",
            checked = values.dynamicAccent,
            onCheckedChange = { onChange(values.copy(dynamicAccent = it)) }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Glass intensity",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassIntensity.entries.forEach { intensity ->
                MomentumChip(
                    selected = values.glassIntensity == intensity,
                    onClick = { onChange(values.copy(glassIntensity = intensity)) },
                    label = intensity.label,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BackupRestoreCard(
    busy: Boolean,
    message: String?,
    password: String,
    onPasswordChange: (String) -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit
) {
    PremiumCard(accent = AppModule.Files.accent, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Backup, AppModule.Files.accent, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Backup & restore", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "Export or restore app records",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Backups include contacts, file metadata and appearance settings, and may contain personal data. " +
                "Vault secrets and file contents are never included; file access may need to be restored " +
                "after import. Automatic weekly copies are unencrypted JSON. Replace resets cloud sync cursors.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Backup password (optional)") },
            placeholder = { Text("Encrypt new exports / open protected backup") },
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "A password protects exported backups with authenticated encryption. It is not stored or recoverable by this app.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                "Export",
                onClick = onExport,
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.FileUpload
            )
            SecondaryButton(
                "Restore",
                onClick = onRestore,
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.FileDownload
            )
        }
        if (busy) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))
        }
        message?.let {
            val failed = it.contains("failed", ignoreCase = true) ||
                it.contains("could not", ignoreCase = true)
            Spacer(Modifier.height(10.dp))
            InfoBanner(
                icon = if (failed) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle,
                text = it,
                tint = if (failed) MaterialTheme.colorScheme.error else Positive
            )
        }
    }
}
