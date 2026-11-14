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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.SyncState
import com.faheem.habits.core.data.supabase.StorageUsage
import com.faheem.habits.core.data.supabase.SupabaseAuthApi
import com.faheem.habits.core.data.supabase.SupabaseProvider
import com.faheem.habits.core.data.supabase.SupabaseRestClient
import com.faheem.habits.core.data.sync.SupabaseSyncRepository
import com.faheem.habits.core.data.sync.ProjectWipeManager
import com.faheem.habits.core.data.sync.SupabaseSyncScheduler
import com.faheem.habits.core.data.sync.SYNC_STATUS_TABLE
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val syncStates by database.syncStateDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val syncWorkInfos by remember(context) {
        WorkManager.getInstance(context.applicationContext)
            .getWorkInfosForUniqueWorkFlow("momentum-sync")
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val syncWorkState = syncWorkInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }?.state
        ?: syncWorkInfos.firstOrNull {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
        }?.state
        ?: syncWorkInfos.lastOrNull()?.state
    val workerError = syncWorkInfos.lastOrNull { it.state == WorkInfo.State.FAILED }
        ?.takeIf { syncWorkState == WorkInfo.State.FAILED }
        ?.outputData?.getString("error")
    val syncBusy = syncWorkState == WorkInfo.State.RUNNING ||
        syncWorkState == WorkInfo.State.ENQUEUED || syncWorkState == WorkInfo.State.BLOCKED
    var metrics by remember { mutableStateOf<SupabaseMetrics?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var wipingProject by remember { mutableStateOf(false) }
    var showProjectWipeConfirm by remember { mutableStateOf(false) }
    var canWipeProject by remember { mutableStateOf(false) }
    var signedOut by remember { mutableStateOf(false) }

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
        if (syncBusy) return
        error = null
        SupabaseSyncScheduler.enqueue(context.applicationContext)
    }

    LaunchedEffect(syncWorkState) {
        if (syncWorkState == WorkInfo.State.SUCCEEDED) refresh()
    }

    fun wipeProjectCloudData() {
        if (wipingProject) return
        scope.launch {
            wipingProject = true
            error = null
            try {
                withContext(Dispatchers.IO) {
                    SupabaseRestClient(context.applicationContext).wipeProjectCloudData()
                }
                val wipeCheck = ProjectWipeManager.applyIfNeeded(context.applicationContext)
                if (wipeCheck == ProjectWipeManager.CheckResult.IN_PROGRESS) {
                    error = "Cloud cleanup is still running. Local app data has not been reset yet."
                }
            } catch (e: Exception) {
                // Failed or partial cloud cleanup must never reset this device's local data.
                error = explainSupabaseError(e.message)
            } finally {
                wipingProject = false
            }
        }
    }

    LaunchedEffect(context) {
        refresh()
        canWipeProject = withContext(Dispatchers.IO) {
            runCatching {
                SupabaseRestClient(context.applicationContext).isCurrentUserAdmin()
            }.getOrDefault(false)
        }
    }
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
                            when (syncWorkState) {
                                WorkInfo.State.RUNNING -> "Syncing workspace…"
                                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Waiting to sync"
                                else -> "Cloud workspace"
                            },
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
                        SupabasePill(Icons.Rounded.Storage, formatStorageBytes(storage))
                    }
                }
            }
        }
        if (!signedOut) item {
            SyncCard(
                syncStates = syncStates,
                workState = syncWorkState,
                workerError = workerError,
                failedTables = current?.counts?.count { it.error != null } ?: 0,
                onSync = ::syncNow
            )
        }
        if (!signedOut) item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(
                    onClick = {
                        scope.launch {
                            SupabaseAuthApi.signOut(context.applicationContext)
                            signedOut = true
                            error = null
                            metrics = null
                        }
                    },
                    enabled = !syncBusy && !wipingProject
                ) {
                    Icon(Icons.Rounded.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out of Supabase")
                }
            }
        }
        if (canWipeProject && !signedOut) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(
                        onClick = { showProjectWipeConfirm = true },
                        enabled = !wipingProject && !syncBusy
                    ) {
                        if (wipingProject) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Delete, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (wipingProject) "Deleting project cloud data…" else "Delete all users’ cloud data")
                    }
                }
            }
        }
        if (signedOut) {
            item {
                EmptyState(
                    Icons.Rounded.Cloud,
                    "Signed out",
                    "This device’s Supabase session was cleared. Sign in again before syncing."
                )
            }
        } else if (loading && current == null) {
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

    if (showProjectWipeConfirm && canWipeProject) {
        AlertDialog(
            onDismissRequest = { if (!wipingProject) showProjectWipeConfirm = false },
            title = { Text("Delete all project cloud data?") },
            text = {
                Text(
                    "This permanently deletes synced records and files for every user in this Supabase project. " +
                        "Login accounts stay, but every updated app installation clears its local records, settings, " +
                        "and app files and signs out when it reconnects and opens. Offline devices reset later. " +
                        "Android system usage history and OS permission grants are not controlled by this app. " +
                        "Only a Supabase admin can do this."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showProjectWipeConfirm = false
                        wipeProjectCloudData()
                    },
                    enabled = !wipingProject
                ) { Text("Delete project data") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showProjectWipeConfirm = false },
                    enabled = !wipingProject
                ) { Text("Cancel") }
            }
        )
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
    workState: WorkInfo.State?,
    workerError: String?,
    failedTables: Int,
    onSync: () -> Unit
) {
    val pending = syncStates.sumOf { it.pendingCount }
    val conflicts = syncStates.sumOf { it.conflictCount }
    val trackedTableCount = syncStates.count { it.tableName != SYNC_STATUS_TABLE }
    val lastError = syncStates.firstOrNull { !it.lastError.isNullOrBlank() }?.lastError ?: workerError
    val syncErrors = syncStates.count { !it.lastError.isNullOrBlank() }
    val hasSynced = syncStates.any { it.lastSyncEpochMillis > 0 }
    val syncing = workState == WorkInfo.State.RUNNING
    val waiting = workState == WorkInfo.State.ENQUEUED ||
        workState == WorkInfo.State.BLOCKED || pending > 0
    val needsAttention = !waiting && (workState == WorkInfo.State.FAILED || failedTables > 0 || syncErrors > 0)
    val statusColor = when {
        needsAttention -> MaterialTheme.colorScheme.error
        hasSynced -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    val statusText = when {
        syncing -> "Syncing"
        waiting -> "Waiting to sync"
        needsAttention -> "Needs attention"
        hasSynced -> "Synced"
        else -> "Waiting to sync"
    }
    var showTechnicalDetail by remember { mutableStateOf(false) }
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
                SyncStat("Tracked tables", trackedTableCount.toString(), Modifier.weight(1f))
            }
        }
        lastError?.let { detail ->
            Spacer(Modifier.height(10.dp))
            Text(
                syncErrorSummary(detail),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = { showTechnicalDetail = !showTechnicalDetail }) {
                Text(if (showTechnicalDetail) "Hide error details" else "Show error details")
            }
            if (showTechnicalDetail) {
                Text(
                    detail,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = when {
                syncing -> "Syncing…"
                waiting -> "Waiting to sync"
                needsAttention -> "Retry sync"
                else -> "Sync now"
            },
            onClick = onSync,
            icon = Icons.Rounded.CloudSync,
            enabled = !syncing && !waiting,
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
    val lastSync = lastSuccessfulSync(syncStates)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(
                title = "Estimated records",
                value = totalRecords.toString(),
                detail = "$readableTables of ${metrics.counts.size} tables readable; large tables may be estimated",
                icon = Icons.Rounded.TableChart,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "Cloud storage",
                value = metrics.storage?.let(::formatStorageBytes) ?: "—",
                detail = metrics.storage?.let {
                    "${it.objectCount}${if (it.truncated) "+" else ""} object${if (it.objectCount == 1 && !it.truncated) "" else "s"} scanned"
                }
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
                        "${formatStorageBytes(it)} used · ${it.objectCount}${if (it.truncated) "+" else ""} object${if (it.objectCount == 1 && !it.truncated) "" else "s"}"
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
                storage?.let(::formatStorageBytes) ?: "—",
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
    return coroutineScope {
        val reach = async { runCatching { client.isReachable() } }
        val counts = async {
            SUPABASE_TABLES.map { (table, label) ->
                async {
                    runCatching { client.count(table) }.fold(
                        onSuccess = { CloudRecord(table, label, it) },
                        onFailure = { CloudRecord(table, label, 0, it.message) }
                    )
                }
            }.awaitAll()
        }
        val storage = async { runCatching { client.storageUsage("momentum-files") } }
        val reachResult = reach.await()
        val storageResult = storage.await()
        SupabaseMetrics(
            reachable = reachResult.getOrDefault(false),
            reachError = reachResult.exceptionOrNull()?.message,
            counts = counts.await(),
            storage = storageResult.getOrNull(),
            storageError = storageResult.exceptionOrNull()?.message
        )
    }
}

private fun syncSummary(states: List<SyncState>): String {
    val synced = states.filter { it.tableName != SYNC_STATUS_TABLE }
    val last = lastSuccessfulSync(states)
        ?: return "Not synced yet. Changes sync automatically when a connection is available."
    val pending = synced.sumOf { it.pendingCount }
    val conflicts = synced.sumOf { it.conflictCount }
    return buildString {
        append("Last successful sync ${dayLabel(last)} at ${timeLabel(last)}")
        if (pending > 0) append(" · $pending pending")
        if (conflicts > 0) append(" · $conflicts conflicts")
    }
}

private fun lastSuccessfulSync(states: List<SyncState>): Long? =
    states.firstOrNull { it.tableName == SYNC_STATUS_TABLE }
        ?.lastSyncEpochMillis?.takeIf { it > 0 }
        ?: states.asSequence()
            .filter { it.tableName != SYNC_STATUS_TABLE && it.lastSyncEpochMillis > 0 }
            .maxOfOrNull { it.lastSyncEpochMillis }

private fun timeLabel(epochMillis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))

private fun dayLabel(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))

private fun explainSupabaseError(message: String?): String {
    val detail = message.orEmpty()
    return when {
        detail.contains("A project wipe is already running", ignoreCase = true) ->
            "A project cleanup is already running. Wait for it to finish, then refresh."
        detail.contains("staleLockRecoveryRequired", ignoreCase = true) ->
            "Cloud cleanup stopped unexpectedly. Your local data was kept. The cloud write lock is still active; retry after 10 minutes."
        detail.contains("cloudCleanupMayBePartial", ignoreCase = true) ->
            "Cloud cleanup did not finish. Your local data was kept. Review the details, then retry Delete all users’ cloud data."
        detail.contains("not configured", ignoreCase = true) ->
            "Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY to your user Gradle properties, then rebuild the app."
        detail.contains("not allowed to wipe project data", ignoreCase = true) ->
            "This Supabase account is not marked as an admin. Set app_metadata.role=admin for it using a trusted Supabase admin workflow. Details: $detail"
        detail.contains("Sign in", ignoreCase = true) ||
            detail.contains("401") ||
            detail.contains("JWT", ignoreCase = true) ||
            detail.contains("token", ignoreCase = true) ->
            "Sign in to Supabase in the app first, then tap Refresh. Details: $detail"
        detail.contains("requested function was not found", ignoreCase = true) ||
            (detail.contains("functions/v1/", ignoreCase = true) && detail.contains("404")) ->
            "The admin-wipe-cloud Edge Function is not deployed to this Supabase project. Deploy supabase/functions/admin-wipe-cloud and check the function's server-side Supabase keys and your app_metadata.role=admin setting. Details: $detail"
        detail.contains("relation", ignoreCase = true) ||
            detail.contains("404") ->
            "Run docs/supabase-schema.sql in the Supabase SQL Editor, then tap Refresh. Details: $detail"
        else ->
            "Check your Supabase URL, publishable key, internet connection, and project status. Details: ${
                detail.ifBlank { "unknown error" }
            }"
    }
}

private fun syncErrorSummary(detail: String): String = when {
    detail.contains("cleanup is in progress", ignoreCase = true) ->
        "Cloud sync is paused until project cleanup finishes. Retry after it completes."
    detail.contains("401") || detail.contains("Sign in", ignoreCase = true) ||
        detail.contains("JWT", ignoreCase = true) ->
        "Your Supabase session may have expired. Sign in again, then retry sync."
    detail.contains("403") || detail.contains("permission", ignoreCase = true) ||
        detail.contains("denied", ignoreCase = true) ->
        "Supabase denied this sync. Check the account and table permissions before retrying."
    detail.contains("429") || (500..599).any { detail.contains("($it)") } ->
        "Supabase is temporarily unavailable or rate-limiting requests. The app will retry."
    detail.contains("timeout", ignoreCase = true) || detail.contains("connect", ignoreCase = true) ||
        detail.contains("network", ignoreCase = true) || detail.contains("ioexception", ignoreCase = true) ||
        detail.contains("host", ignoreCase = true) || detail.contains("socket", ignoreCase = true) ->
        "The connection interrupted sync. It will retry when the network is available."
    else -> "Sync did not finish. Review the error details, then retry."
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

private fun formatStorageBytes(storage: StorageUsage): String =
    "${if (storage.truncated) "≥" else ""}${formatBytes(storage.bytes)}"
