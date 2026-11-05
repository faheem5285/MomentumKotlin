@file:Suppress("FunctionNaming", "CyclomaticComplexMethod", "TooGenericExceptionCaught", "TooManyFunctions")

package com.faheem.habits.features.supabase

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.SyncState
import com.faheem.habits.core.data.supabase.StorageUsage
import com.faheem.habits.core.data.supabase.SupabaseProvider
import com.faheem.habits.core.data.supabase.SupabaseRestClient
import com.faheem.habits.core.data.sync.SupabaseSyncRepository
import com.faheem.habits.core.ui.ChartLegend
import com.faheem.habits.core.ui.ChartPlaceholder
import com.faheem.habits.core.ui.ChartSlice
import com.faheem.habits.core.ui.DonutChart
import com.faheem.habits.core.ui.EmptyState
import com.faheem.habits.core.ui.HeroCard
import com.faheem.habits.core.ui.HeroRefreshButton
import com.faheem.habits.core.ui.IconBubble
import com.faheem.habits.core.ui.InfoBanner
import com.faheem.habits.core.ui.MetricTile
import com.faheem.habits.core.ui.MomentumTokens
import com.faheem.habits.core.ui.PremiumCard
import com.faheem.habits.core.ui.PrimaryButton
import com.faheem.habits.core.ui.SectionHeader
import com.faheem.habits.core.ui.StatusPill
import com.faheem.habits.core.ui.chartColor
import com.faheem.habits.core.ui.chartSlices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val SUPABASE_TABLES = listOf(
    "momentum_habits" to "Habits",
    "momentum_completions" to "Completions",
    "momentum_habit_skips" to "Habit skips",
    "momentum_habit_reminders" to "Habit reminders",
    "momentum_expenses" to "Expenses",
    "momentum_category_budgets" to "Budgets",
    "momentum_loans" to "Loans",
    "momentum_loan_payments" to "Loan payments",
    "momentum_contacts" to "Contacts",
    "momentum_files" to "File records",
    "momentum_todos" to "To-dos",
    "momentum_vault_entries" to "Vault entries",
    "momentum_loan_people" to "Loan people",
    "momentum_loan_attachments" to "Loan attachments",
    "momentum_life_rules" to "Life rules",
    "momentum_life_rule_files" to "Life rule files",
    "momentum_custom_notifications" to "Custom notifications"
)

private data class CloudRecord(val table: String, val label: String, val count: Int, val error: String? = null)

private data class SupabaseMetrics(
    val reachable: Boolean,
    val reachError: String?,
    val counts: List<CloudRecord>,
    val storage: StorageUsage?,
    val storageError: String?
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupabaseScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.get(context.applicationContext) }
    val syncStates by database.syncStateDao().observeAll().collectAsState(initial = emptyList())
    var metrics by remember { mutableStateOf<SupabaseMetrics?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }

    suspend fun load() {
        metrics = withContext(Dispatchers.IO) { loadSupabaseMetrics(context.applicationContext) }
    }

    fun refresh() {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            try {
                if (!SupabaseProvider.isConfigured) {
                    error = explainSupabaseError("Supabase is not configured.")
                } else {
                    load()
                }
            } catch (e: Exception) {
                error = explainSupabaseError(e.message)
            } finally {
                loading = false
            }
        }
    }

    fun syncNow() {
        if (syncing) return
        scope.launch {
            syncing = true
            error = null
            try {
                withContext(Dispatchers.IO) { SupabaseSyncRepository(context.applicationContext).sync() }
                load()
            } catch (e: Exception) {
                error = explainSupabaseError(e.message)
                runCatching { load() }
            } finally {
                syncing = false
            }
        }
    }

    LaunchedEffect(context) { refresh() }
    val current = metrics
    val totalRecords = current?.counts?.filter { it.error == null }?.sumOf { it.count } ?: 0
    val readableTables = current?.counts?.count { it.error == null } ?: 0

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = MomentumTokens.screenPadding,
            vertical = MomentumTokens.space2
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HeroCard(contentPadding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "SUPABASE CLOUD",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (syncing) "Syncing workspace…" else "Cloud workspace",
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.6).sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Your records, files and sync health in one place.",
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                    HeroRefreshButton(::refresh)
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SupabasePill(
                        Icons.Rounded.Cloud,
                        when {
                            current?.reachable == true -> "Connected"
                            loading -> "Checking"
                            else -> "Offline"
                        }
                    )
                    if (current != null) {
                        SupabasePill(Icons.Rounded.TableChart, "$totalRecords records")
                    }
                    current?.storage?.let { storage ->
                        SupabasePill(Icons.Rounded.Storage, formatBytes(storage.bytes))
                    }
                }
            }
        }
        item {
            SyncCard(
                syncStates = syncStates,
                syncing = syncing,
                failedTables = current?.counts?.count { it.error != null } ?: 0,
                onSync = ::syncNow
            )
        }
        if (loading && current == null) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (error != null && current == null) {
            item { EmptyState(Icons.Rounded.Cloud, "Supabase fetch failed", error.orEmpty()) }
        } else if (current != null) {
            item {
                OverviewGrid(
                    metrics = current,
                    totalRecords = totalRecords,
                    readableTables = readableTables,
                    syncStates = syncStates
                )
            }
            item { StorageUsageCard(current.storage, totalRecords, readableTables, current.counts.size) }
            if (current.reachError != null) {
                item {
                    InfoBanner(
                        icon = Icons.Rounded.WarningAmber,
                        text = explainSupabaseError(current.reachError),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (current.storageError != null) {
                item {
                    InfoBanner(
                        icon = Icons.Rounded.Storage,
                        text = explainSupabaseError(current.storageError),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            item { RecordMixCard(current.counts) }
            item {
                SectionHeader(
                    "Cloud records",
                    subtitle = "$readableTables of ${current.counts.size} tables readable for this account"
                )
            }
            itemsIndexed(current.counts) { index, record ->
                CloudRecordCard(
                    record = record,
                    accent = chartColor(index),
                    onDeleteToday = {
                        scope.launch {
                            try {
                                SupabaseSyncRepository(context.applicationContext)
                                    .deleteCloudTableUpdatedToday(record.table)
                                load()
                            } catch (e: Exception) {
                                error = explainSupabaseError(e.message)
                            }
                        }
                    },
                    onDeleteAll = {
                        scope.launch {
                            try {
                                SupabaseSyncRepository(context.applicationContext)
                                    .deleteCloudTable(record.table)
                                load()
                            } catch (e: Exception) {
                                error = explainSupabaseError(e.message)
                            }
                        }
                    }
                )
            }
            if (error != null) {
                item {
                    InfoBanner(
                        icon = Icons.Rounded.WarningAmber,
                        text = error.orEmpty(),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SupabasePill(icon: ImageVector, text: String) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = Color.White)
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}

@Composable
private fun SyncCard(
    syncStates: List<SyncState>,
    syncing: Boolean,
    failedTables: Int,
    onSync: () -> Unit
) {
    val pending = syncStates.sumOf { it.pendingCount }
    val conflicts = syncStates.sumOf { it.conflictCount }
    val syncErrors = syncStates.count { !it.lastError.isNullOrBlank() }
    val hasSynced = syncStates.any { it.lastSyncEpochMillis > 0 }
    val needsAttention = pending > 0 || conflicts > 0 || failedTables > 0 || syncErrors > 0
    val statusColor = when {
        needsAttention -> MaterialTheme.colorScheme.tertiary
        hasSynced -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    val statusText = when {
        syncing -> "Syncing"
        needsAttention -> "Review"
        hasSynced -> "Healthy"
        else -> "Ready"
    }
    PremiumCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.CloudSync, statusColor, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Two-way sync", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    syncSummary(syncStates),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (syncing) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                StatusPill(statusText, statusColor)
            }
        }
        if (syncStates.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SyncStat("Pending", pending.toString(), Modifier.weight(1f))
                SyncStat("Conflicts", conflicts.toString(), Modifier.weight(1f))
                SyncStat("Tracked tables", syncStates.size.toString(), Modifier.weight(1f))
            }
        }
        syncStates.firstOrNull { !it.lastError.isNullOrBlank() }?.lastError?.let { lastError ->
            Spacer(Modifier.height(10.dp))
            Text(
                explainSupabaseError(lastError),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = if (syncing) "Syncing…" else "Sync now",
            onClick = onSync,
            icon = Icons.Rounded.CloudSync,
            enabled = !syncing,
            loading = syncing
        )
    }
}

@Composable
private fun SyncStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OverviewGrid(
    metrics: SupabaseMetrics,
    totalRecords: Int,
    readableTables: Int,
    syncStates: List<SyncState>
) {
    val lastSync = syncStates.filter { it.lastSyncEpochMillis > 0 }.maxOfOrNull { it.lastSyncEpochMillis }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(
                title = "Visible records",
                value = totalRecords.toString(),
                detail = "$readableTables of ${metrics.counts.size} tables readable",
                icon = Icons.Rounded.TableChart,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "Cloud storage",
                value = metrics.storage?.let { formatBytes(it.bytes) } ?: "—",
                detail = metrics.storage?.let { "${it.objectCount} object${if (it.objectCount == 1) "" else "s"}" }
                    ?: "Storage usage unavailable",
                icon = Icons.Rounded.Storage,
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(
                title = "Connection",
                value = if (metrics.reachable) "Online" else "Offline",
                detail = if (metrics.reachable) "Publishable-key access" else "Check the project or network",
                icon = Icons.Rounded.Cloud,
                accent = if (metrics.reachable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "Last sync",
                value = lastSync?.let { timeLabel(it) } ?: "Never",
                detail = lastSync?.let { dayLabel(it) } ?: "Tap Sync now to start",
                icon = Icons.Rounded.Schedule,
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StorageUsageCard(
    storage: StorageUsage?,
    totalRecords: Int,
    readableTables: Int,
    tableCount: Int
) {
    PremiumCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Storage, MaterialTheme.colorScheme.secondary, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Your Supabase storage", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    storage?.let {
                        "${formatBytes(it.bytes)} used · ${it.objectCount} object${if (it.objectCount == 1) "" else "s"}"
                    } ?: "Usage unavailable",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Your database records", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "$totalRecords rows",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            "$readableTables of $tableCount account tables readable under row level security",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Used", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                storage?.let { formatBytes(it.bytes) } ?: "—",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Total project quota is managed by Supabase and is not exposed through the mobile publishable-key API. Check Supabase Dashboard → Settings → Usage.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecordMixCard(counts: List<CloudRecord>) {
    val slices = recordSlices(counts)
    val total = slices.sumOf { it.value.toInt() }
    PremiumCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Record mix", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "Where your visible rows live",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (slices.isNotEmpty()) {
                Text(
                    "$total rows",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (slices.isEmpty()) {
            ChartPlaceholder("No cloud rows are visible for this account yet")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(
                    slices,
                    Modifier.weight(1f),
                    centerValue = total.toString(),
                    centerLabel = "rows"
                )
                ChartLegend(slices, Modifier.weight(1.1f), maxRows = 6)
            }
        }
    }
}

@Composable
private fun CloudRecordCard(
    record: CloudRecord,
    accent: Color,
    onDeleteToday: () -> Unit,
    onDeleteAll: () -> Unit
) {
    var pendingAction by remember { mutableStateOf<String?>(null) }
    val tint = if (record.error == null) accent else MaterialTheme.colorScheme.error
    PremiumCard(contentPadding = PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(recordIcon(record.label), tint, 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(record.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    when {
                        record.error != null -> "Could not read this table"
                        record.count == 0 -> "No rows yet"
                        else -> "Synced to Supabase"
                    },
                    fontSize = 11.5.sp,
                    color = if (record.error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (record.error == null) {
                Text(
                    record.count.toString(),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            } else {
                StatusPill("Unavailable", MaterialTheme.colorScheme.error, icon = Icons.Rounded.WarningAmber)
            }
        }
        if (record.error == null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { pendingAction = "today" }) {
                    Text("Delete today")
                }
                TextButton(onClick = { pendingAction = "all" }) {
                    Text("Delete all")
                }
            }
        }
        record.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                explainSupabaseError(it),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(if (action == "today") "Delete today's records?" else "Delete all records?") },
            text = {
                Text(
                    if (action == "today") {
                        "This removes today's ${record.label.lowercase()} rows from Supabase."
                    } else {
                        "This permanently removes all ${record.label.lowercase()} rows from Supabase."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAction = null
                        if (action == "today") onDeleteToday() else onDeleteAll()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun recordIcon(label: String): ImageVector = when (label) {
    "Habits", "Completions", "Habit skips", "Habit reminders" -> Icons.Rounded.CheckCircle
    "Expenses", "Budgets", "Loans", "Loan payments" -> Icons.Rounded.TableChart
    "File records" -> Icons.Rounded.Folder
    "To-dos" -> Icons.Rounded.TaskAlt
    "Vault entries" -> Icons.Rounded.Lock
    else -> Icons.Rounded.Cloud
}

private fun recordSlices(counts: List<CloudRecord>): List<ChartSlice> {
    val totals = linkedMapOf<String, Float>()
    counts.filter { it.error == null && it.count > 0 }.forEach { record ->
        val domain = when (record.label) {
            "Habits", "Completions", "Habit skips", "Habit reminders" -> "Habits"
            "Expenses", "Budgets", "Loans", "Loan payments", "Loan people", "Loan attachments" -> "Money"
            "File records", "Life rule files" -> "Files"
            "Life rules" -> "Life rules"
            "Custom notifications" -> "Other"
            "To-dos" -> "To-dos"
            "Vault entries" -> "Vault"
            else -> "Other"
        }
        totals[domain] = (totals[domain] ?: 0f) + record.count
    }
    return chartSlices(totals)
}

private suspend fun loadSupabaseMetrics(context: Context): SupabaseMetrics {
    val client = SupabaseRestClient(context)
    val reach = runCatching { client.isReachable() }
    val counts = SUPABASE_TABLES.map { (table, label) ->
        runCatching { client.count(table) }.fold(
    onSuccess = { CloudRecord(table, label, it) },
    onFailure = { CloudRecord(table, label, 0, it.message) }
        )
    }
    val storage = runCatching { client.storageUsage("momentum-files") }
    return SupabaseMetrics(
        reachable = reach.getOrDefault(false),
        reachError = reach.exceptionOrNull()?.message,
        counts = counts,
        storage = storage.getOrNull(),
        storageError = storage.exceptionOrNull()?.message
    )
}

private fun syncSummary(states: List<SyncState>): String {
    val synced = states.filter { it.lastSyncEpochMillis > 0 }
    if (synced.isEmpty()) return "Not synced yet. Tap Sync now to fetch cloud records."
    val last = synced.maxOf { it.lastSyncEpochMillis }
    val pending = synced.sumOf { it.pendingCount }
    val conflicts = synced.sumOf { it.conflictCount }
    return buildString {
        append("Last sync ${dayLabel(last)} at ${timeLabel(last)}")
        if (pending > 0) append(" · $pending pending")
        if (conflicts > 0) append(" · $conflicts conflicts")
    }
}

private fun timeLabel(epochMillis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))

private fun dayLabel(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))

private fun explainSupabaseError(message: String?): String {
    val detail = message.orEmpty()
    return when {
        detail.contains("not configured", ignoreCase = true) ->
            "Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY to your user Gradle properties, then rebuild the app."
        detail.contains("Sign in", ignoreCase = true) ||
            detail.contains("401") || detail.contains("403") ||
            detail.contains("JWT", ignoreCase = true) ||
            detail.contains("token", ignoreCase = true) ->
            "Sign in to Supabase in the app first, then tap Refresh. Details: $detail"
        detail.contains("relation", ignoreCase = true) ||
            detail.contains("404") ->
            "Run docs/supabase-schema.sql in the Supabase SQL Editor, then tap Refresh. Details: $detail"
        else ->
            "Check your Supabase URL, publishable key, internet connection, and project status. Details: ${
                detail.ifBlank { "unknown error" }
            }"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}
