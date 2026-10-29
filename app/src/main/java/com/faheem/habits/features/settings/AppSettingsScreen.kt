package com.faheem.habits.features.settings
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val modules = AppModule.entries.filter { it != AppModule.Settings }
    BackHandler(enabled = remindersOpen) { remindersOpen = false }
    if (remindersOpen) {
        ReminderSettingsScreen(onBack = { remindersOpen = false })
        return
    }

    key(version) { LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HeroCard {
                Text("App settings", fontSize = 14.sp, color = Color.White.copy(alpha = .86f))
                Text("Make Momentum yours", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Choose which tools appear on your Home screen. Hiding a module keeps all of its saved data.", fontSize = 13.sp, color = Color.White.copy(alpha = .9f))
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().clickable { remindersOpen = true },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.NotificationsActive, MaterialTheme.colorScheme.primary, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Notifications & reminders", fontWeight = FontWeight.SemiBold)
                        Text("Manage schedules, categories and channels", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Apps, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Home screen modules", fontWeight = FontWeight.Bold)
                    Text("Turn modules on or off", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit().clear().apply()
                    version++
                }) { Text("Show all") }
            }
        }
        items(modules, key = { "${version}-${it.name}" }) { module ->
            val visible = isModuleVisible(context, module)
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(module.icon, module.gradient.first(), 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(module.label, fontWeight = FontWeight.SemiBold)
                        Text(if (visible) "Shown on Home" else "Hidden from Home", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Changes take effect when you return to Home. Your habit history, files, finance records, and vault entries stay on this device when a module is hidden.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } }
}
