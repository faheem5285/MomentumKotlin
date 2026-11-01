@file:Suppress("FunctionNaming")

package com.faheem.habits.features.settings
import com.faheem.habits.core.data.*
import com.faheem.habits.core.data.backup.MomentumBackup
import com.faheem.habits.core.data.backup.WeeklyBackupSettings
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faheem.habits.core.settings.AppearancePreferences
import com.faheem.habits.core.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat

private const val PREFS_APP = "momentum_app_settings"
private fun modulePref(module: AppModule) = "module_enabled_${module.name}"

fun isModuleVisible(context: Context, module: AppModule): Boolean =
    module == AppModule.Settings || context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
        .getBoolean(modulePref(module), true)

@Composable
fun AppSettingsScreen() {
    val context = LocalContext.current
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
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch { backupBusy = true; backupMessage = try { MomentumBackup.export(context, uri); "Backup exported. Vault secrets and file contents are excluded." } catch (e: Exception) { "Export failed: ${e.message ?: "unknown error"}" } finally { backupBusy = false } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { backupBusy = true; try { pendingImport = uri to MomentumBackup.preview(context, uri) } catch (e: Exception) { backupMessage = "Import preview failed: ${e.message ?: "unknown error"}" } finally { backupBusy = false } }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                .onSuccess { WeeklyBackupSettings.setFolder(context, uri); weeklyFolder = uri.toString(); if (weeklyEnabled) WeeklyBackupSettings.setEnabled(context, true) }
                .onFailure { backupMessage = "Could not save folder access: ${it.message ?: "permission unavailable"}" }
        }
    }
    LaunchedEffect(context) {
        databaseVersion = withContext(Dispatchers.IO) { AppDatabase.get(context).openHelper.readableDatabase.version }
    }
    pendingImport?.let { (uri, preview) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import preview") },
            text = { Text("${preview.habits} habits, ${preview.expenses} expenses, ${preview.todos} to-dos, ${preview.loans} loans and ${preview.files} file records will be imported. File contents and Vault entries are excluded.") },
            confirmButton = {
                Row {
                    TextButton(onClick = { pendingImport = null; scope.launch { backupBusy = true; backupMessage = try { MomentumBackup.restore(context, uri, replace = true) } catch (e: Exception) { "Import failed: ${e.message ?: "unknown error"}" } finally { backupBusy = false } } }) { Text("Replace records") }
                    TextButton(onClick = { pendingImport = null; scope.launch { backupBusy = true; backupMessage = try { MomentumBackup.restore(context, uri, replace = false) } catch (e: Exception) { "Import failed: ${e.message ?: "unknown error"}" } finally { backupBusy = false } } }) { Text("Merge") }
                }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } }
        )
    }
    val modules = AppModule.entries.filter { it != AppModule.Settings }
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
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp, 6.dp, 18.dp, 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroCard {
                    Text("App settings", fontSize = 13.sp, color = Color.White.copy(alpha = .84f))
                    Text("Make Momentum yours", fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Choose which tools appear on your Home screen. Hiding a module keeps all of its saved data.",
                        fontSize = 12.5.sp, color = Color.White.copy(alpha = .9f)
                    )
                }
            }
            item { SectionHeader("Data & backup", subtitle = "Local copies and database health") }
            item {
                PremiumCard(contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.Storage, MaterialTheme.colorScheme.primary, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Storage", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Database version ${if (databaseVersion == 0) "checking…" else databaseVersion}",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(if (integrityMessage == "ok") "Healthy" else "On device", if (integrityMessage == "ok") Positive else MaterialTheme.colorScheme.primary)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Automatic weekly backup", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                "Copies records to your chosen folder",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = weeklyEnabled, enabled = weeklyFolder != null, onCheckedChange = { weeklyEnabled = it; WeeklyBackupSettings.setEnabled(context, it) })
                    }
                    Spacer(Modifier.height(6.dp))
                    SecondaryButton(
                        if (weeklyFolder == null) "Choose backup folder" else "Change backup folder",
                        onClick = { folderPicker.launch(null) },
                        icon = Icons.Rounded.FolderOpen
                    )
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(
                        "Check database integrity",
                        onClick = { scope.launch { backupBusy = true; integrityMessage = try { withContext(Dispatchers.IO) { AppDatabase.get(context).openHelper.readableDatabase.query("PRAGMA integrity_check").use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "No result" } } } catch (e: Exception) { "Integrity check failed: ${e.message ?: "unknown error"}" } finally { backupBusy = false } } },
                        icon = Icons.Rounded.FactCheck
                    )
                    integrityMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, fontSize = 12.sp, color = if (it == "ok") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                PremiumCard(contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.Backup, MaterialTheme.colorScheme.primary, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Backup & restore", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Export or merge your app records", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Restores add records without deleting existing data. Password Vault entries and actual file contents are excluded.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Export", onClick = { exportLauncher.launch("momentum-backup.json") }, modifier = Modifier.weight(1f), icon = Icons.Rounded.FileUpload)
                        SecondaryButton("Restore", onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }, modifier = Modifier.weight(1f), icon = Icons.Rounded.FileDownload)
                    }
                    if (backupBusy) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))
                    }
                    backupMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { SectionHeader("Appearance", subtitle = "Theme and motion") }
            item {
                PremiumCard(contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.Palette, MaterialTheme.colorScheme.secondary, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Theme", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Match the system or lock Momentum to light or dark",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { AppearancePreferences.setThemeMode(context, mode) },
                                label = { Text(mode.label) },
                                leadingIcon = {
                                    Icon(
                                        when (mode) {
                                            ThemeMode.System -> Icons.Rounded.Contrast
                                            ThemeMode.Light -> Icons.Rounded.LightMode
                                            ThemeMode.Dark -> Icons.Rounded.DarkMode
                                        },
                                        null,
                                        Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Reduce motion", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                "Turns off glow blooms and ambient animation",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = reduceMotion,
                            onCheckedChange = { AppearancePreferences.setReduceMotion(context, it) }
                        )
                    }
                }
            }
            item { SectionHeader("Preferences") }
            item {
                PremiumCard(onClick = { remindersOpen = true }, contentPadding = PaddingValues(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.NotificationsActive, MaterialTheme.colorScheme.primary, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Notifications & reminders", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Manage schedules, categories and channels", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            item {
                PremiumCard(onClick = { permissionsOpen = true }, contentPadding = PaddingValues(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.Security, MaterialTheme.colorScheme.secondary, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Permissions & background setup", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Review Android access and background settings", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Home screen modules", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Choose what appears on Home", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit().clear().apply()
                        version++
                    }) { Text("Show all") }
                }
            }
            items(modules, key = { "${version}-${it.name}" }) { module ->
                val visible = isModuleVisible(context, module)
                PremiumCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(module.icon, module.gradient.first(), 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(module.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                if (visible) "Shown on Home" else "Hidden from Home",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = visible, onCheckedChange = { checked ->
                            context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit()
                                .putBoolean(modulePref(module), checked).apply()
                            version++
                        })
                    }
                }
            }
            item {
                InfoBanner(
                    icon = Icons.Rounded.Info,
                    text = "Changes take effect when you return to Home. Your records stay on this device when a module is hidden.",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SettingInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}
