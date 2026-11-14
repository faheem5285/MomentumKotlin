@file:Suppress("FunctionNaming", "LongParameterList", "LongMethod", "CyclomaticComplexMethod", "TooManyFunctions")

package com.faheem.habits.features.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.faheem.habits.BuildConfig
import com.faheem.habits.R
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.backup.MomentumBackup
import com.faheem.habits.core.data.backup.WeeklyBackupSettings
import com.faheem.habits.core.notifications.ReminderSettingsScreen
import com.faheem.habits.core.settings.AppearancePreferences
import com.faheem.habits.core.settings.ThemeMode
import com.faheem.habits.core.ui.AppModule
import com.faheem.habits.core.ui.BrandGradient
import com.faheem.habits.core.ui.ChartSlice
import com.faheem.habits.core.ui.HeroCard
import com.faheem.habits.core.ui.IconBubble
import com.faheem.habits.core.ui.InfoBanner
import com.faheem.habits.core.ui.MetricTile
import com.faheem.habits.core.ui.MomentumChip
import com.faheem.habits.core.ui.MomentumDialog
import com.faheem.habits.core.ui.MomentumTokens
import com.faheem.habits.core.ui.Positive
import com.faheem.habits.core.ui.PremiumCard
import com.faheem.habits.core.ui.SecondaryButton
import com.faheem.habits.core.ui.SectionHeader
import com.faheem.habits.core.ui.StackedBar
import com.faheem.habits.core.ui.StatusPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Locale

@Composable
fun AppSettingsScreen() {
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val moduleVisibility by settingsViewModel.moduleVisibility.collectAsStateWithLifecycle()
    var version by remember { mutableIntStateOf(0) }
    var remindersOpen by remember { mutableStateOf(false) }
    var permissionsOpen by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Pair<Uri, MomentumBackup.Preview>?>(null) }
    var weeklyEnabled by remember { mutableStateOf(WeeklyBackupSettings.enabled(context)) }
    var weeklyFolder by remember { mutableStateOf(WeeklyBackupSettings.folder(context)) }
    var lastBackupAt by remember { mutableLongStateOf(WeeklyBackupSettings.lastBackupAt(context)) }
    var databaseVersion by remember { mutableIntStateOf(0) }
    var integrityMessage by remember { mutableStateOf<String?>(null) }
    val themeMode by AppearancePreferences.themeMode.collectAsStateWithLifecycle()
    val reduceMotion by AppearancePreferences.reduceMotion.collectAsStateWithLifecycle()
    val dynamicAccent by AppearancePreferences.dynamicAccent.collectAsStateWithLifecycle()
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
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
                .onSuccess {
                    WeeklyBackupSettings.setFolder(context, uri)
                    weeklyFolder = uri.toString()
                    if (weeklyEnabled) WeeklyBackupSettings.setEnabled(context, true)
                }
                .onFailure {
                    backupMessage = "Could not save folder access: ${it.message ?: "permission unavailable"}"
                }
        }
    }
    LaunchedEffect(context) {
        databaseVersion = withContext(Dispatchers.IO) {
            AppDatabase.get(context).openHelper.readableDatabase.version
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
        val integrityHealthy = integrityMessage == "ok"

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = MomentumTokens.screenPadding,
                vertical = MomentumTokens.space2
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsHero(
                    themeMode = themeMode,
                    visibleCount = visibleCount,
                    totalModules = modules.size,
                    hiddenCount = hiddenCount,
                    lastBackupAt = lastBackupAt
                )
            }
            item {
                SectionHeader("At a glance")
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile(
                            title = "Theme",
                            value = themeMode.label,
                            detail = if (reduceMotion) "Reduced motion" else "Full motion",
                            icon = Icons.Rounded.Palette,
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            title = "Home modules",
                            value = "$visibleCount/${modules.size}",
                            detail = if (hiddenCount == 0) "Everything is visible" else "$hiddenCount hidden safely",
                            icon = Icons.Rounded.Dashboard,
                            accent = AppModule.Settings.accent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile(
                            title = "Last backup",
                            value = if (lastBackupAt == 0L) "Never" else shortDate(lastBackupAt),
                            detail = if (weeklyEnabled) "Weekly backup on" else "Manual backup only",
                            icon = Icons.Rounded.Backup,
                            accent = AppModule.Supabase.accent,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            title = "Database",
                            value = if (databaseVersion == 0) "…" else "v$databaseVersion",
                            detail = if (integrityHealthy) "Integrity verified" else "Stored on this device",
                            icon = Icons.Rounded.Storage,
                            accent = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            item { SectionHeader("Appearance") }
            item {
                AppearanceCard(
                    themeMode = themeMode,
                    reduceMotion = reduceMotion,
                    dynamicAccent = dynamicAccent,
                    onThemeChange = { AppearancePreferences.setThemeMode(context, it) },
                    onReduceMotionChange = { AppearancePreferences.setReduceMotion(context, it) },
                    onDynamicAccentChange = { AppearancePreferences.setDynamicAccent(context, it) }
                )
            }
            item {
                SectionHeader(
                    title = "Home modules",
                    subtitle = "$visibleCount of ${modules.size} shown on Home",
                    actionLabel = "Show all",
                    onAction = {
                        settingsViewModel.showAllModules()
                        version++
                    }
                )
            }
            items(modules, key = { "${version}-${it.name}" }) { module ->
                ModuleToggleCard(
                    module = module,
                    visible = moduleVisibility[module.name] ?: true,
                    onCheckedChange = { checked ->
                        settingsViewModel.setModuleVisible(module.name, checked)
                        version++
                    }
                )
            }
            item {
                InfoBanner(
                    icon = Icons.Rounded.Lock,
                    text = "Hiding a module only removes it from Home. All of its records stay safely on this device.",
                    tint = AppModule.Vault.accent
                )
            }
            item { SectionHeader("Data & safety") }
            item {
                DataHealthCard(
                    databaseVersion = databaseVersion,
                    integrityMessage = integrityMessage,
                    weeklyEnabled = weeklyEnabled,
                    weeklyFolder = weeklyFolder,
                    lastBackupAt = lastBackupAt,
                    onWeeklyChange = { enabled ->
                        weeklyEnabled = enabled
                        WeeklyBackupSettings.setEnabled(context, enabled)
                    },
                    onPickFolder = { folderPicker.launch(null) },
                    onCheckIntegrity = {
                        scope.launch {
                            backupBusy = true
                            integrityMessage = try {
                                withContext(Dispatchers.IO) {
                                    AppDatabase.get(context).openHelper.readableDatabase
                                        .query("PRAGMA integrity_check").use { cursor ->
                                            if (cursor.moveToFirst()) cursor.getString(0) else "No result"
                                        }
                                }
                            } catch (e: Exception) {
                                "Integrity check failed: ${e.message ?: "unknown error"}"
                            } finally {
                                backupBusy = false
                            }
                        }
                    }
                )
            }
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

            item { SectionHeader("About", subtitle = "Local-first by design") }
            item { AboutCard() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsHero(
    themeMode: ThemeMode,
    visibleCount: Int,
    totalModules: Int,
    hiddenCount: Int,
    lastBackupAt: Long
) {
    HeroCard(gradient = BrandGradient, contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "CONTROL CENTER",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                    color = Color.White.copy(alpha = 0.78f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Settings",
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Tune Zylo to the way you work.",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.zylo_logo),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsPill(Icons.Rounded.Palette, themeMode.label)
            SettingsPill(Icons.Rounded.Dashboard, "$visibleCount of $totalModules modules")
            SettingsPill(
                if (lastBackupAt > 0) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                if (lastBackupAt > 0) "Backed up" else "No backup yet"
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Home visibility",
                Modifier.weight(1f),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.82f)
            )
            Text(
                "$visibleCount on · $hiddenCount off",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        Spacer(Modifier.height(7.dp))
        StackedBar(
            listOf(
                ChartSlice("Shown", visibleCount.toFloat().coerceAtLeast(0.001f), Color.White),
                ChartSlice("Hidden", hiddenCount.toFloat().coerceAtLeast(0.001f), Color.White.copy(alpha = 0.30f))
            ),
            height = 9.dp,
            track = Color.White.copy(alpha = 0.14f)
        )
    }
}

@Composable
private fun SettingsPill(icon: ImageVector, text: String) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = Color.White)
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun AppearanceCard(
    themeMode: ThemeMode,
    reduceMotion: Boolean,
    dynamicAccent: Boolean,
    onThemeChange: (ThemeMode) -> Unit,
    onReduceMotionChange: (Boolean) -> Unit,
    onDynamicAccentChange: (Boolean) -> Unit
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
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Reduce motion", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    "Turns off glow blooms and ambient animation",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = reduceMotion, onCheckedChange = onReduceMotionChange)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dynamic colours", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        "Use colours from your wallpaper"
                    } else {
                        "Available on Android 12 and later"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = dynamicAccent,
                onCheckedChange = onDynamicAccentChange,
                enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Accent preview",
                Modifier.weight(1f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Neon glass",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(BrandGradient))
        )
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
private fun DataHealthCard(
    databaseVersion: Int,
    integrityMessage: String?,
    weeklyEnabled: Boolean,
    weeklyFolder: String?,
    lastBackupAt: Long,
    onWeeklyChange: (Boolean) -> Unit,
    onPickFolder: () -> Unit,
    onCheckIntegrity: () -> Unit
) {
    val healthy = integrityMessage == "ok"
    PremiumCard(accent = AppModule.Supabase.accent, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Storage, AppModule.Supabase.accent, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Storage & health", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    if (databaseVersion == 0) "Checking database…" else "Database version $databaseVersion",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(if (healthy) "Healthy" else "On device", if (healthy) Positive else AppModule.Supabase.accent)
        }
        Spacer(Modifier.height(12.dp))
        SettingInfoRow(
            label = "Last weekly backup",
            value = if (lastBackupAt == 0L) "None yet" else DateFormat.getDateTimeInstance().format(java.util.Date(lastBackupAt))
        )
        SettingInfoRow(
            label = "Backup folder",
            value = if (weeklyFolder == null) "Not selected" else "Ready"
        )
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Automatic weekly backup", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    "Copies records to your chosen folder",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = weeklyEnabled,
                enabled = weeklyFolder != null,
                onCheckedChange = onWeeklyChange
            )
        }
        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            if (weeklyFolder == null) "Choose backup folder" else "Change backup folder",
            onClick = onPickFolder,
            icon = Icons.Rounded.FolderOpen
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton(
            "Check database integrity",
            onClick = onCheckIntegrity,
            icon = Icons.Rounded.FactCheck
        )
        integrityMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                fontSize = 12.sp,
                color = if (healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
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
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBubble(icon, tint, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun AboutCard() {
    PremiumCard(accent = AppModule.Settings.accent, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.AutoAwesome, AppModule.Settings.accent, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Zylo ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "Account required · local-first storage",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill("Sign-in required", AppModule.Settings.accent)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "General records use Zylo's app-private Room database; Zylo does not separately encrypt it. " +
                "Vault records sync encrypted, but account recovery stores the key in Supabase, where project administrators may access it.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingInfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun shortDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(java.util.Date(epochMillis))
