@file:Suppress("FunctionNaming", "CyclomaticComplexMethod", "TooGenericExceptionCaught", "TooManyFunctions")

package com.faheem.habits.features.supabase

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faheem.habits.core.data.SyncState
import com.faheem.habits.core.data.supabase.HttpErrorSanitizer
import com.faheem.habits.core.data.supabase.SupabaseProvider
import com.faheem.habits.core.data.supabase.SupabaseRestClient
import com.faheem.habits.core.data.sync.LocalAppDataReset
import com.faheem.habits.core.data.sync.SupabaseSyncRepository
import com.faheem.habits.core.data.sync.ProjectWipeManager
import com.faheem.habits.core.data.sync.SYNC_STATUS_TABLE
import com.faheem.habits.core.data.sync.SyncModule
import com.faheem.habits.core.data.sync.summarizeSyncStatus
import com.faheem.habits.features.settings.SettingsViewModel
import com.faheem.habits.core.ui.AppModule
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
import com.faheem.habits.core.ui.GlassCard
import com.faheem.habits.core.ui.GlassTopBar
import com.faheem.habits.core.ui.SettingsGroup
import com.faheem.habits.core.ui.SettingsRow
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

private fun CloudRecord.belongsTo(module: SyncModule): Boolean = when (module) {
    SyncModule.Habits -> table in setOf(
        "momentum_habits",
        "momentum_completions",
        "momentum_habit_skips",
        "momentum_habit_reminders"
    )
    SyncModule.ToDos -> table == "momentum_todos"
    SyncModule.Money -> table in setOf("momentum_expenses", "momentum_category_budgets")
    SyncModule.Loans -> table in setOf(
        "momentum_loans",
        "momentum_loan_payments",
        "momentum_loan_people",
        "momentum_loan_attachments"
    )
    SyncModule.Contacts -> table == "momentum_contacts"
    SyncModule.Files -> table == "momentum_files"
    SyncModule.LifeRules -> table in setOf("momentum_life_rules", "momentum_life_rule_files")
    SyncModule.Reminders -> table == "momentum_custom_notifications"
    SyncModule.Vault -> table == "momentum_vault_entries"
}

private fun SyncModule.homeModule(): AppModule? =
    AppModule.entries.firstOrNull { it.name == name && it != AppModule.Settings }

private fun homeModulesWithoutSync(): List<AppModule> {
    val syncedNames = SyncModule.entries.mapNotNull { it.homeModule()?.name }.toSet()
    return AppModule.entries.filter { it != AppModule.Settings && it.name !in syncedNames }
}

private data class SupabaseMetrics(
    val counts: List<CloudRecord>
)

private fun loadCachedSupabaseMetrics(context: Context): SupabaseMetrics {
    val prefs = context.getSharedPreferences("momentum_cloud_metrics", Context.MODE_PRIVATE)
    return SupabaseMetrics(SUPABASE_TABLES.map { (table, label) ->
        CloudRecord(table, label, prefs.getInt(table, 0))
    })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupabaseScreen(onOpenProfile: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cloudViewModel: CloudSyncViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val settingsViewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val cloudUi by cloudViewModel.uiState.collectAsStateWithLifecycle()
    val homeVisibility by settingsViewModel.moduleVisibility.collectAsStateWithLifecycle()
    val syncStates = cloudUi.syncStates
    val syncWorkState = cloudUi.workState
    val workerError = cloudUi.workerError
    val syncBusy = syncWorkState == WorkInfo.State.RUNNING ||
        syncWorkState == WorkInfo.State.ENQUEUED || syncWorkState == WorkInfo.State.BLOCKED
    var metrics by remember { mutableStateOf<SupabaseMetrics?>(loadCachedSupabaseMetrics(context.applicationContext)) }
    var cloudReachable by remember { mutableStateOf<Boolean?>(null) }
    var reachError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var wipingProject by remember { mutableStateOf(false) }
    var showProjectWipeConfirm by remember { mutableStateOf(false) }
    var canWipeProject by remember { mutableStateOf(false) }
    var selectedModule by remember { mutableStateOf<SyncModule?>(null) }
    var selectedHomeModule by remember { mutableStateOf<AppModule?>(null) }

    suspend fun load() {
        metrics = loadSupabaseMetrics(context.applicationContext) { result ->
            cloudReachable = result.getOrDefault(false)
            reachError = result.exceptionOrNull()?.message?.let { HttpErrorSanitizer.forDisplay(it) }
        }
    }

    fun refresh() {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            try {
                if (!SupabaseProvider.isConfigured) {
                    error = explainSupabaseError("Supabase is not configured.")
                    cloudReachable = false
                    reachError = "Supabase is not configured."
                } else {
                    cloudReachable = null
                    reachError = null
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
        cloudViewModel.syncNow()
    }

    fun deleteModuleData(module: SyncModule) {
        scope.launch {
            try {
                SupabaseSyncRepository(context.applicationContext).queueModuleDelete(module)
                metrics = loadCachedSupabaseMetrics(context.applicationContext)
            } catch (e: Exception) {
                error = explainSupabaseError(e.message)
            }
        }
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

    BackHandler(enabled = selectedModule != null || selectedHomeModule != null) {
        if (selectedModule != null) selectedModule = null else selectedHomeModule = null
    }

    selectedModule?.let { module ->
        CloudSyncModuleSettingsScreen(
            module = module,
            records = current?.counts.orEmpty().filter { it.belongsTo(module) },
            enabled = cloudUi.moduleEnabled[module] ?: true,
            homeModule = module.homeModule(),
            homeVisible = module.homeModule()?.let { homeVisibility[it.name] ?: true },
            online = cloudReachable == true,
            signedIn = cloudUi.signedIn,
            workState = syncWorkState,
            error = error,
            onBack = { selectedModule = null },
            onRefresh = ::refresh,
            onEnabledChange = { cloudViewModel.setModuleEnabled(module, it) },
            onHomeVisibleChange = { visible ->
                module.homeModule()?.let { settingsViewModel.setModuleVisible(it.name, visible) }
            },
            onSync = ::syncNow,
            onDeleteModuleData = { deleteModuleData(module) }
        )
        return
    }

    selectedHomeModule?.let { module ->
        HomeModuleVisibilityScreen(
            module = module,
            visible = homeVisibility[module.name] ?: true,
            onBack = { selectedHomeModule = null },
            onVisibilityChange = { settingsViewModel.setModuleVisible(module.name, it) }
        )
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
            HeroCard(contentPadding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "CLOUD & SYNC",
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
                            cloudUi.accountEmail ?: if (cloudUi.signedIn) "Your account is connected" else "Sign in to back up and sync",
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
                            cloudReachable == true -> "Connected"
                            loading -> "Checking"
                            else -> "Offline"
                        }
                    )
                }
            }
        }
        if (cloudUi.signedIn) item {
            SyncCard(
                syncStates = syncStates,
                workState = syncWorkState,
                workerError = workerError,
                failedTables = current?.counts?.count { it.error != null } ?: 0,
                onSync = ::syncNow
            )
        }
        item {
            OverviewGrid(
                reachable = cloudReachable,
                syncStates = syncStates
            )
        }
        if (cloudUi.signedIn) {
            item {
                SectionHeader("Modules")
            }
            items(SyncModule.entries, key = { it.name }) { module ->
                CloudSyncModuleCard(
                    module = module,
                    enabled = cloudUi.moduleEnabled[module] ?: true,
                    onClick = { selectedModule = module }
                )
            }
        }
        items(homeModulesWithoutSync(), key = { "home-${it.name}" }) { module ->
            HomeModuleVisibilityCard(
                module = module,
                visible = homeVisibility[module.name] ?: true,
                onClick = { selectedHomeModule = module }
            )
        }
        if (!cloudUi.signedIn) item {
            GlassCard(contentPadding = PaddingValues(18.dp)) {
                Text("Sign in to back up and sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Sign in to sync this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Open Profile", onOpenProfile)
            }
        }
        if (canWipeProject) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Danger zone")
                    Button(
                        onClick = { showProjectWipeConfirm = true },
                        enabled = !wipingProject && !syncBusy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        if (wipingProject) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (wipingProject) "Wiping project cloud data…" else "Wipe project cloud data")
                    }
                }
            }
        }
        if (reachError != null) {
            item {
                InfoBanner(
                    icon = Icons.Rounded.WarningAmber,
                    text = explainSupabaseError(reachError),
                    tint = MaterialTheme.colorScheme.error
                )
            }
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
            item { RecordMixCard(current.counts, loading && cloudReachable != false) }
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
        if (current == null) item { RecordMixCard(emptyList(), loading && cloudReachable != false) }
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
    val summary = summarizeSyncStatus(
        states = syncStates,
        failedTables = failedTables,
        workRunning = workState == WorkInfo.State.RUNNING,
        workQueued = workState == WorkInfo.State.ENQUEUED || workState == WorkInfo.State.BLOCKED,
        workFailed = workState == WorkInfo.State.FAILED
    )
    val pending = summary.pending
    val lastError = (syncStates.firstOrNull { !it.lastError.isNullOrBlank() }?.lastError ?: workerError)
        ?.let { HttpErrorSanitizer.forDisplay(it) }
    val statusColor = when {
        summary.needsAttention -> MaterialTheme.colorScheme.error
        summary.hasSynced -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    val statusText = when {
        summary.running -> "Syncing"
        summary.needsAttention -> "Needs attention"
        summary.waiting -> "Waiting to sync"
        summary.hasSynced -> "Synced"
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
            if (summary.running) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                StatusPill(statusText, statusColor)
            }
        }
        if (syncStates.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SyncStat("Pending", pending.toString(), Modifier.weight(1f))
                SyncStat("Conflicts", summary.conflicts.toString(), Modifier.weight(1f))
                SyncStat("Tracked tables", summary.trackedTables.toString(), Modifier.weight(1f))
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
                    HttpErrorSanitizer.forDisplay(detail),
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = when {
                summary.running -> "Syncing…"
                summary.needsAttention -> "Retry sync"
                summary.waiting && pending == 0 -> "Queued…"
                else -> "Sync now"
            },
            onClick = onSync,
            icon = Icons.Rounded.CloudSync,
            enabled = !summary.running,
            loading = summary.running
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
    reachable: Boolean?,
    syncStates: List<SyncState>
) {
    val lastSync = lastSuccessfulSync(syncStates)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(
                title = "Connection",
                value = when (reachable) {
                    true -> "Online"
                    false -> "Offline"
                    null -> "Checking"
                },
                detail = when (reachable) {
                    true -> "Connected"
                    false -> "Unavailable"
                    null -> "Checking cloud"
                },
                icon = Icons.Rounded.Cloud,
                accent = if (reachable == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f).height(140.dp)
            )
            MetricTile(
                title = "Last sync",
                value = lastSync?.let { timeLabel(it) } ?: "Never",
                detail = lastSync?.let { dayLabel(it) } ?: "Not synced yet",
                icon = Icons.Rounded.Schedule,
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f).height(140.dp)
            )
        }
    }
}

@Composable
private fun RecordMixCard(counts: List<CloudRecord>, loading: Boolean) {
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
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
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
private fun CloudSyncModuleSettingsScreen(
    module: SyncModule,
    records: List<CloudRecord>,
    enabled: Boolean,
    homeModule: AppModule?,
    homeVisible: Boolean?,
    online: Boolean,
    signedIn: Boolean,
    workState: WorkInfo.State?,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onHomeVisibleChange: (Boolean) -> Unit,
    onSync: () -> Unit,
    onDeleteModuleData: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val syncBusy = workState == WorkInfo.State.RUNNING ||
        workState == WorkInfo.State.ENQUEUED || workState == WorkInfo.State.BLOCKED
    val totalRows = records.filter { it.error == null }.sumOf { it.count }
    val unavailableCount = records.count { it.error != null }

    Column(Modifier.fillMaxSize()) {
        GlassTopBar(
            title = module.label,
            onBack = onBack,
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh cloud data")
                }
            }
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MomentumTokens.screenPadding,
                top = MomentumTokens.space2,
                end = MomentumTokens.screenPadding,
                bottom = 64.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsGroup("Sync settings") {
                    SettingsRow(
                        title = "Sync ${module.label}",
                        supportingText = if (enabled) "Enabled" else "Paused · cloud data is kept",
                        icon = Icons.Rounded.CloudSync,
                        checked = enabled,
                        onCheckedChange = onEnabledChange
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = if (syncBusy) "Syncing enabled modules…" else "Sync enabled modules",
                        onClick = onSync,
                        enabled = !syncBusy,
                        loading = syncBusy,
                        icon = Icons.Rounded.CloudSync
                    )
                }
            }
            if (homeModule != null && homeVisible != null) {
                item {
                    SettingsGroup("Home screen") {
                        SettingsRow(
                            title = "Show ${homeModule.label} on Home",
                            supportingText = if (homeVisible) "Shown" else "Hidden",
                            icon = homeModule.icon,
                            checked = homeVisible,
                            onCheckedChange = onHomeVisibleChange
                        )
                    }
                }
            }
            item {
                SectionHeader(
                    "Cloud tables",
                    subtitle = if (records.isEmpty()) "Table counts unavailable"
                    else "$totalRows records across ${records.size} tables"
                )
            }
            item {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = signedIn,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete ${module.label} data")
                }
            }
            if (records.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.Cloud,
                        "No cloud tables available",
                        ""
                    )
                }
            } else {
                itemsIndexed(records, key = { _, record -> record.table }) { index, record ->
                    CloudRecordCard(
                        record = record,
                        accent = chartColor(index),
                        online = online
                    )
                }
            }
            if (unavailableCount > 0) {
                item {
                    Text(
                        "$unavailableCount table(s) could not be read. Check the error shown on each table.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            error?.let { message ->
                item {
                    InfoBanner(
                        icon = Icons.Rounded.WarningAmber,
                        text = message,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${module.label} data?") },
            text = {
                Text(if (online) {
                    "This removes the module’s data from this device and its cloud copy."
                } else {
                    "This removes the module’s data from this device now and queues its cloud deletion until you are online."
                })
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeleteModuleData() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HomeModuleVisibilityScreen(
    module: AppModule,
    visible: Boolean,
    onBack: () -> Unit,
    onVisibilityChange: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        GlassTopBar(title = module.label, onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = MomentumTokens.screenPadding,
                vertical = MomentumTokens.space2
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsGroup("Home screen") {
                    SettingsRow(
                        title = "Show ${module.label} on Home",
                        supportingText = if (visible) "Shown" else "Hidden",
                        icon = module.icon,
                        checked = visible,
                        onCheckedChange = onVisibilityChange
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeModuleVisibilityCard(
    module: AppModule,
    visible: Boolean,
    onClick: () -> Unit
) {
    PremiumCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(module.icon, module.accent, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(module.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    if (visible) "Shown on Home" else "Hidden from Home",
                    fontSize = 11.5.sp,
                    color = if (visible) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.tertiary
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "Manage ${module.label} visibility",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun CloudSyncModuleCard(
    module: SyncModule,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = when (module) {
        SyncModule.Vault -> MaterialTheme.colorScheme.tertiary
        SyncModule.Loans -> MaterialTheme.colorScheme.secondary
        SyncModule.Files -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }
    val icon = when (module) {
        SyncModule.Vault -> Icons.Rounded.Lock
        SyncModule.Files -> Icons.Rounded.Folder
        SyncModule.ToDos -> Icons.Rounded.TaskAlt
        SyncModule.Reminders -> Icons.Rounded.Schedule
        else -> Icons.Rounded.CloudSync
    }
    PremiumCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, tint, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(module.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(if (enabled) "On" else "Paused",
                    fontSize = 11.5.sp,
                    color = if (!enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "Open ${module.label} settings",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun CloudRecordCard(
    record: CloudRecord,
    accent: Color,
    online: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val tint = if (record.error == null) accent else MaterialTheme.colorScheme.error
    GlassCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(recordIcon(record.label), tint, 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(record.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(if (record.error != null) "Unavailable" else "Cloud table",
                    fontSize = 11.5.sp,
                    color = if (record.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (record.error != null) {
                StatusPill("Unavailable", MaterialTheme.colorScheme.error, icon = Icons.Rounded.WarningAmber)
            } else {
                Text(record.count.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent)
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse table" else "Expand table")
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text(record.table, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (online && record.error != null) {
                Text(explainSupabaseError(record.error), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            } else {
                Text("${record.count} rows", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
    counts.filter { it.count > 0 }.forEach { record ->
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

private suspend fun loadSupabaseMetrics(
    context: Context,
    onReachabilityLoaded: (Result<Boolean>) -> Unit
): SupabaseMetrics {
    val client = SupabaseRestClient(context)
    return coroutineScope {
        val reach = async(Dispatchers.IO) { runCatching { client.isReachable() } }
        val counts = async(Dispatchers.IO) {
            coroutineScope {
                SUPABASE_TABLES.map { (table, label) ->
                    async {
                        runCatching { client.count(table) }.fold(
                            onSuccess = { CloudRecord(table, label, it) },
                        onFailure = {
                            val cached = context.getSharedPreferences("momentum_cloud_metrics", Context.MODE_PRIVATE)
                                .getInt(table, 0)
                            CloudRecord(table, label, cached, it.message)
                        }
                        )
                    }
                }.awaitAll()
            }
        }
        val reachResult = reach.await()
        onReachabilityLoaded(reachResult)
        val loadedCounts = counts.await()
        context.getSharedPreferences("momentum_cloud_metrics", Context.MODE_PRIVATE).edit().apply {
            loadedCounts.filter { it.error == null }.forEach { putInt(it.table, it.count) }
        }.apply()
        SupabaseMetrics(counts = loadedCounts)
    }
}

private fun syncSummary(states: List<SyncState>): String {
    val synced = states.filter { it.tableName != SYNC_STATUS_TABLE }
    val last = lastSuccessfulSync(states)
        ?: return "Not synced yet"
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
    // Error text is only used to choose guidance; raw server details are never
    // shown because they can contain tokens, account ids or personal data.
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
            "This Supabase account is not marked as an admin. Set app_metadata.role=admin for it using a trusted Supabase admin workflow."
        detail.contains("Sign in", ignoreCase = true) ||
            detail.contains("401") ||
            detail.contains("JWT", ignoreCase = true) ||
            detail.contains("token", ignoreCase = true) ->
            "Sign in to Supabase in the app first, then tap Refresh."
        detail.contains("requested function was not found", ignoreCase = true) ||
            (detail.contains("functions/v1/", ignoreCase = true) && detail.contains("404")) ->
            "The admin-wipe-cloud Edge Function is not deployed to this Supabase project. Deploy supabase/functions/admin-wipe-cloud and check the function's server-side Supabase keys and your app_metadata.role=admin setting."
        detail.contains("relation", ignoreCase = true) ||
            detail.contains("404") ->
            "Run docs/supabase-schema.sql in the Supabase SQL Editor, then tap Refresh."
        else ->
            "Check your Supabase URL, publishable key, internet connection, and project status."
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
