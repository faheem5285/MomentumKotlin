@file:Suppress("FunctionNaming", "LongParameterList", "LongMethod", "CyclomaticComplexMethod", "TooManyFunctions")

package com.faheem.habits.features.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.faheem.habits.core.data.backup.MomentumBackup
import com.faheem.habits.core.notifications.ReminderSettingsScreen
import com.faheem.habits.core.settings.AppearancePreferences
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
import com.faheem.habits.core.ui.SectionHeader
import com.faheem.habits.core.ui.StatusPill
import com.faheem.habits.core.ui.SettingsGroup
import com.faheem.habits.core.ui.SettingsRow
import kotlinx.coroutines.launch

@Composable
fun AppSettingsScreen(onOpenModule: (AppModule) -> Unit = {}) {
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val moduleVisibility by settingsViewModel.moduleVisibility.collectAsStateWithLifecycle()
    var version by remember { mutableIntStateOf(0) }
    var moduleSearch by remember { mutableStateOf("") }
    var remindersOpen by remember { mutableStateOf(false) }
    var permissionsOpen by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Pair<Uri, MomentumBackup.Preview>?>(null) }
    val themeMode by AppearancePreferences.themeMode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            backupBusy = true
            backupMessage = try {
                MomentumBackup.export(context, uri)
                "Backup exported. Vault secrets and file contents are excluded."
            } catch (e: Exception) {
                "Export failed: ${e.message ?: "unknown error"}"
            } finally {
                backupBusy = false
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            backupBusy = true
            try {
                pendingImport = uri to MomentumBackup.preview(context, uri)
            } catch (e: Exception) {
                backupMessage = "Import preview failed: ${e.message ?: "unknown error"}"
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
                            "expenses, loans, contacts, and files."
                    )
                    Text("Merge adds missing records. Replace deletes existing backup-supported records first.")
                    Text(
                        "This JSON is not encrypted. Vault entries and file contents are excluded; " +
                            "file access may need to be restored."
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        pendingImport = null
                        scope.launch {
                            backupBusy = true
                            backupMessage = try {
                                MomentumBackup.restore(context, uri, replace = true)
                            } catch (e: Exception) {
                                "Import failed: ${e.message ?: "unknown error"}"
                            } finally {
                                backupBusy = false
                            }
                        }
                    }) { Text("Replace records") }
                    TextButton(onClick = {
                        pendingImport = null
                        scope.launch {
                            backupBusy = true
                            backupMessage = try {
                                MomentumBackup.restore(context, uri, replace = false)
                            } catch (e: Exception) {
                                "Import failed: ${e.message ?: "unknown error"}"
                            } finally {
                                backupBusy = false
                            }
                        }
                    }) { Text("Merge") }
                }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } }
        )
    }
    BackHandler(enabled = remindersOpen || permissionsOpen) {
        if (permissionsOpen) permissionsOpen = false else remindersOpen = false
    }
    if (permissionsOpen) {
        PermissionsSetupScreen(onBack = { permissionsOpen = false })
        return
    }
    if (remindersOpen) {
        ReminderSettingsScreen(onBack = { remindersOpen = false })
        return
    }

    key(version) {
        val modules = AppModule.entries.filter { it != AppModule.Settings }
        val visibleCount = modules.count { moduleVisibility[it.name] ?: true }
        val hiddenCount = modules.size - visibleCount
        val filteredModules = modules.filter { module ->
            module.label.contains(moduleSearch, ignoreCase = true) || module.name.contains(moduleSearch, ignoreCase = true)
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
                SettingsGroup("Account") {
                    SettingsRow(
                        title = "Profile",
                        supportingText = "Edit your details",
                        icon = Icons.Rounded.Person,
                        onClick = { onOpenModule(AppModule.Profile) }
                    )
                    SettingsRow(
                        title = "Cloud & Sync",
                        supportingText = "Manage account and cloud backup",
                        icon = Icons.Rounded.CloudDone,
                        onClick = { onOpenModule(AppModule.Supabase) }
                    )
                }
            }
            item { SectionHeader("Appearance") }
            item {
                AppearanceCard(
                    themeMode = themeMode,
                    onThemeChange = { AppearancePreferences.setThemeMode(context, it) }
                )
            }
            item {
                SettingsGroup("Home modules · $visibleCount of ${modules.size} shown") {
                    OutlinedTextField(
                        value = moduleSearch,
                        onValueChange = { moduleSearch = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        placeholder = { Text("Search modules") },
                        singleLine = true
                    )
                    filteredModules.forEach { module ->
                        SettingsRow(
                            title = module.label,
                            supportingText = if (moduleVisibility[module.name] ?: true) "Visible on Home" else "Hidden, with data kept on this device",
                            icon = module.icon,
                            checked = moduleVisibility[module.name] ?: true,
                            onCheckedChange = { checked ->
                                settingsViewModel.setModuleVisible(module.name, checked)
                                version++
                            }
                        )
                    }
                    if (filteredModules.isEmpty()) {
                        Text("No modules match that search.", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        settingsViewModel.showAllModules()
                        version++
                    }) { Text("Show all modules") }
                }
            }
            item { SectionHeader("Backup") }
            item {
                BackupRestoreCard(
                    busy = backupBusy,
                    message = backupMessage,
                    onExport = { exportLauncher.launch("momentum-backup.json") },
                    onRestore = { importLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }
                )
            }
            item { SectionHeader("Preferences") }
            item {
                PreferencesCard(
                    onOpenNotifications = { remindersOpen = true },
                    onOpenPermissions = { permissionsOpen = true }
                )
            }

        }
    }
}

@Composable
private fun AppearanceCard(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    PremiumCard(accent = AppModule.Settings.accent, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Palette, AppModule.Settings.accent, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Look & feel", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "Match the system or lock Zylo to light or dark",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                MomentumChip(
                    selected = themeMode == mode,
                    onClick = { onThemeChange(mode) },
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
    }
}

@Composable
private fun ModuleToggleCard(
    module: AppModule,
    visible: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val tint = module.accent
    PremiumCard(accent = tint, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(module.icon, tint, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        module.label,
                        Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusPill(
                        if (visible) "Shown" else "Hidden",
                        if (visible) tint else MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    if (visible) "Appears on your Home screen" else "Hidden, all data is kept",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(checked = visible, onCheckedChange = onCheckedChange)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(module.gradient))
        )
    }
}

@Composable
private fun BackupRestoreCard(
    busy: Boolean,
    message: String?,
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
            "JSON backups are not encrypted. They exclude Vault entries and file contents; imported file access may need to be restored.",
            fontSize = 12.sp,
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

@Composable
private fun PreferencesCard(
    onOpenNotifications: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    PremiumCard(accent = AppModule.Screen.accent, contentPadding = PaddingValues(4.dp)) {
        SettingLinkRow(
            icon = Icons.Rounded.NotificationsActive,
            title = "Notifications & reminders",
            subtitle = "Manage schedules, categories and channels",
            tint = AppModule.Habits.accent,
            onClick = onOpenNotifications
        )
        HorizontalDivider(
            Modifier.padding(horizontal = 14.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        SettingLinkRow(
            icon = Icons.Rounded.Security,
            title = "Permissions & background setup",
            subtitle = "Review Android access and background settings",
            tint = AppModule.Vault.accent,
            onClick = onOpenPermissions
        )
    }
}

@Composable
private fun SettingLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBubble(icon, tint, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

