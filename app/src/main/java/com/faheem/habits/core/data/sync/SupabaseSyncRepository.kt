@file:Suppress(
    "TooManyFunctions",
    "CyclomaticComplexMethod",
    "TooGenericExceptionCaught",
    "ReturnCount",
    "WildcardImport",
    "LoopWithTooManyJumpStatements",
    "UnusedParameter"
)

package com.faheem.habits.core.data.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.faheem.habits.core.data.*
import com.faheem.habits.core.data.backup.LocalBackupCrypto
import com.faheem.habits.core.data.backup.MomentumBackup
import com.faheem.habits.core.data.supabase.SupabaseRestClient
import com.faheem.habits.core.data.supabase.SupabaseAuthApi
import com.faheem.habits.core.data.supabase.HttpErrorSanitizer
import com.faheem.habits.features.vault.VaultCrypto
import com.faheem.habits.core.ui.AppModule
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

private const val SYNC_EPOCH = "1970-01-01T00:00:00Z"
private const val PULL_PAGE = 200
private const val PUSH_BATCH = 100
private const val SYNC_WORK_NAME = "momentum-sync"
private const val SYNC_PERIODIC_WORK_NAME = "momentum-sync-periodic"
private val syncRunMutex = Mutex()
private suspend fun <T> withSyncLock(block: suspend () -> T): T =
    syncRunMutex.withLock { withContext(Dispatchers.IO) { block() } }

private val allCloudTables = listOf(
    "profiles", "momentum_habits", "momentum_completions", "momentum_habit_skips", "momentum_habit_reminders",
    "momentum_expenses", "momentum_category_budgets", "momentum_loan_people", "momentum_loans",
    "momentum_loan_attachments", "momentum_loan_payments", "momentum_contacts", "momentum_files",
    "momentum_todos", "momentum_life_rules", "momentum_life_rule_files", "momentum_custom_notifications",
    "momentum_vault_entries"
)

class SupabaseSyncRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.get(context),
    private val client: SupabaseRestClient = SupabaseRestClient(context),
    private val fileStorage: FileStorageSync = FileStorageSync(context, db, client)
) {
    suspend fun syncVaultEntries(
        accountKey: ByteArray? = null,
        fullPull: Boolean = false
    ): Int = withSyncLock {
        if (!SyncModuleSettings.isEnabled(context, SyncModule.Vault)) return@withSyncLock 0
        if (ProjectWipeManager.applyIfNeeded(context) != ProjectWipeManager.CheckResult.UNCHANGED) return@withSyncLock 0
        val table = "momentum_vault_entries"
        val stateDao = db.syncStateDao()
        val old = stateDao.get(table) ?: SyncState(table)
        val device = DeviceIdentity.get(context)
        try {
            val records = db.vaultDao().getAllForSync()
            val dirty = records.filter { it.dirty && (accountKey == null || it.matchesVaultKey(accountKey)) }
            val payload = dirty.map { it.toCloud(device) }
            val pushed = pushAndMarkClean(table, payload, db.vaultDao()::markClean)
            var cursor = if (fullPull) {
                SyncCursor(SYNC_EPOCH).encode()
            } else {
                old.cursor.ifBlank { SyncCursor(SYNC_EPOCH).encode() }
            }
            var pulled = 0
            while (true) {
                val batch = client.pull(table, cursor, PULL_PAGE)
                if (batch.isEmpty()) break
                val previousCursor = cursor
                db.withTransaction {
                    batch.forEach { element ->
                        val row = element.jsonObject
                        cursor = SyncCursor(row.string("updated_at"), row.string("id")).encode()
                        applyVault(row)
                    }
                }
                pulled += batch.size
                if (batch.size < PULL_PAGE || cursor == previousCursor) break
            }
            stateDao.upsert(
                old.copy(
                    cursor = cursor,
                    pendingCount = db.vaultDao().getAllForSync().count { it.dirty },
                    lastSyncEpochMillis = System.currentTimeMillis(),
                    lastError = null
                )
            )
            pushed + pulled
        } catch (failure: Exception) {
            stateDao.upsert(old.copy(lastError = HttpErrorSanitizer.forDisplay(failure.message)))
            throw failure
        }
    }

    suspend fun sync(): SyncRunResult = withSyncLock {
        when (ProjectWipeManager.applyIfNeeded(context)) {
            ProjectWipeManager.CheckResult.IN_PROGRESS ->
                return@withSyncLock SyncRunResult(projectWipeInProgress = true)
            ProjectWipeManager.CheckResult.APPLIED -> return@withSyncLock SyncRunResult()
            ProjectWipeManager.CheckResult.UNCHANGED -> Unit
        }
        runPendingModuleDeletes()
        val paused = db.syncStateDao().get("momentum_vault_entries")?.paused == true
        if (paused) return@withSyncLock SyncRunResult()
        createFirstSyncBackup()
        val device = DeviceIdentity.get(context)
        var pushed = 0
        var pulled = 0
        var conflicts = 0
        val failures = mutableListOf<Throwable>()
        suspend fun attempt(table: String? = null, module: SyncModule? = null, block: suspend () -> Unit) {
            if (module != null && !SyncModuleSettings.isEnabled(context, module)) return
            try {
                block()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failures += failure
                if (table != null) {
                    val stateDao = db.syncStateDao()
                    val state = stateDao.get(table) ?: SyncState(table)
                    stateDao.upsert(state.copy(lastError = HttpErrorSanitizer.forDisplay(failure.message)))
                }
            }
        }
        suspend fun syncEntity(
            table: String,
            rows: List<JsonObject>,
            uuids: List<String>,
            markClean: suspend (List<String>) -> Unit,
            apply: suspend (JsonObject) -> Int
        ) {
            val stateDao = db.syncStateDao()
            val old = stateDao.get(table) ?: SyncState(table)
            pushed += pushAndMarkClean(table, rows, uuids.toSet(), markClean)
            var cursor = old.cursor.ifBlank { SyncCursor(SYNC_EPOCH).encode() }
            var tableConflicts = 0
            var tablePulled = 0
            while (true) {
                val batch = client.pull(table, cursor.ifBlank { SYNC_EPOCH }, PULL_PAGE)
                if (batch.isEmpty()) break
                val previousCursor = cursor
                db.withTransaction {
                    batch.forEach { element ->
                        val json = element.jsonObject
                        cursor = SyncCursor(json.string("updated_at"), json.string("id")).encode()
                        tableConflicts += apply(json)
                    }
                }
                tablePulled += batch.size
                if (batch.size < PULL_PAGE || cursor == previousCursor) break
            }
            conflicts += tableConflicts
            pulled += tablePulled
            stateDao.upsert(old.copy(cursor = cursor, pendingCount = dirtyCount(table),
                conflictCount = tableConflicts, lastSyncEpochMillis = System.currentTimeMillis(),
                lastError = null))
        }

        if (SyncModuleSettings.isEnabled(context, SyncModule.Files)) attempt { fileStorage.uploadPending() }

        val habits = db.habitDao().getAllForSync()
        val habitIds = habits.associate { it.id to it.uuid }
        attempt("momentum_habits", SyncModule.Habits) {
            val dirty = habits.filter { it.dirty }
            syncEntity("momentum_habits", dirty.map { it.toCloud(device) }, dirty.map { it.uuid },
                db.habitDao()::markHabitsClean) { applyHabit(it) }
        }
        val completions = db.habitDao().getCompletionsForBackup()
        attempt("momentum_completions", SyncModule.Habits) {
            val dirty = completions.filter { it.dirty }
            syncEntity("momentum_completions", dirty.map { it.toCloud(device, habitIds[it.habitId].orEmpty()) },
                dirty.map { it.uuid }, db.habitDao()::markCompletionsClean) { applyCompletion(it) }
        }
        val skips = db.habitDao().getAllSkipsForSync()
        attempt("momentum_habit_skips", SyncModule.Habits) {
            val dirty = skips.filter { it.dirty }
            syncEntity("momentum_habit_skips", dirty.map { it.toCloud(device, habitIds[it.habitId].orEmpty()) },
                dirty.map { it.uuid }, db.habitDao()::markSkipsClean) { applySkip(it) }
        }
        val reminders = db.habitDao().getAllRemindersForSync()
        attempt("momentum_habit_reminders", SyncModule.Habits) {
            val dirty = reminders.filter { it.dirty }
            syncEntity("momentum_habit_reminders", dirty.map { it.toCloud(device, habitIds[it.habitId].orEmpty()) },
                dirty.map { it.uuid }, db.habitDao()::markRemindersClean) { applyReminder(it) }
        }

        val expenses = db.expenseDao().getAllForSync()
        attempt("momentum_expenses", SyncModule.Money) {
            val dirty = expenses.filter { it.dirty }
            syncEntity("momentum_expenses", dirty.map { it.toCloud(device) },
                dirty.map { it.uuid }, db.expenseDao()::markClean) { applyExpense(it) }
        }

        val contacts = db.contactDao().getAllForSync()
        attempt("momentum_contacts", SyncModule.Contacts) {
            val dirty = contacts.filter { it.dirty }
            syncEntity("momentum_contacts", dirty.map { it.toCloud(device) },
                dirty.map { it.uuid }, db.contactDao()::markClean) { applyContact(it) }
        }
        val budgets = db.expenseDao().getAllBudgetsForSync()
        attempt("momentum_category_budgets", SyncModule.Money) {
            val dirty = budgets.filter { it.dirty }
            syncEntity("momentum_category_budgets", dirty.map { it.toCloud(device) },
                dirty.map { it.uuid }, db.expenseDao()::markBudgetsClean) { applyBudget(it) }
        }

        val people = db.loanDao().getAllPeopleForSync()
        attempt("momentum_loan_people", SyncModule.Loans) {
            val dirty = people.filter { it.dirty }
            syncEntity("momentum_loan_people", dirty.map { it.toCloud(device) },
                dirty.map { it.uuid }, db.loanDao()::markPeopleClean) { applyLoanPerson(it) }
        }
        val syncedPeople = db.loanDao().getAllPeopleForSync()
        val personIds = syncedPeople.associate { it.id to it.uuid }
        val loans = db.loanDao().getAllForSync()
        attempt("momentum_loans", SyncModule.Loans) {
            val dirty = loans.filter { it.dirty }
            syncEntity("momentum_loans", dirty.map { it.toCloud(device, it.personId?.let(personIds::get)) },
                dirty.map { it.uuid }, db.loanDao()::markClean) { applyLoan(it) }
        }
        val syncedLoans = db.loanDao().getAllForSync()
        val loanIds = syncedLoans.associate { it.id to it.uuid }
        val attachments = db.loanDao().getAllAttachmentsForSync()
        attempt("momentum_loan_attachments", SyncModule.Loans) {
            val dirty = attachments.filter { it.dirty }
            syncEntity("momentum_loan_attachments", dirty.map { it.toCloud(device, loanIds[it.loanId].orEmpty()) },
                dirty.map { it.uuid }, db.loanDao()::markAttachmentsClean) { applyLoanAttachment(it) }
        }
        val payments = db.loanDao().getAllPaymentsForSync()
        attempt("momentum_loan_payments", SyncModule.Loans) {
            val dirty = payments.filter { it.dirty }
            syncEntity("momentum_loan_payments", dirty.map { it.toCloud(device, loanIds[it.loanId].orEmpty()) },
                dirty.map { it.uuid }, db.loanDao()::markPaymentsClean) { applyPayment(it) }
        }

        val files = db.fileDao().getAllForSync()
        attempt("momentum_files", SyncModule.Files) {
            val dirty = files.filter { it.dirty }
            syncEntity("momentum_files", dirty.map { it.toCloud(device) },
                dirty.map { it.uuid }, db.fileDao()::markClean) { applyFile(it) }
        }
        val todos = db.toDoDao().getAllForSync()
        val todoIds = todos.associate { it.id to it.uuid }
        attempt("momentum_todos", SyncModule.ToDos) {
            val dirty = todos.filter { it.dirty }
            syncEntity("momentum_todos", dirty.map { it.toCloud(device, todoIds[it.parentId].orEmpty()) },
                dirty.map { it.uuid }, db.toDoDao()::markClean) { applyTodo(it) }
        }
        val lifeRules = db.lifeRuleDao().getAllForSync()
        attempt("momentum_life_rules", SyncModule.LifeRules) {
            val dirty = lifeRules.filter { it.dirty }
            syncEntity("momentum_life_rules", dirty.map { it.toCloud(device) },
                dirty.map { it.uuid }, db.lifeRuleDao()::markClean) { applyLifeRule(it) }
        }
        val syncedRules = db.lifeRuleDao().getAllForSync()
        val ruleIds = syncedRules.associate { it.id to it.uuid }
        val ruleFiles = db.lifeRuleDao().getAllFilesForSync()
        attempt("momentum_life_rule_files", SyncModule.LifeRules) {
            val dirty = ruleFiles.filter { it.dirty }
            syncEntity("momentum_life_rule_files", dirty.map { it.toCloud(device, ruleIds[it.ruleId].orEmpty()) },
                dirty.map { it.uuid }, db.lifeRuleDao()::markFilesClean) { applyLifeRuleFile(it) }
        }
        val customNotifications = db.customNotificationDao().getAllForSync()
        attempt("momentum_custom_notifications", SyncModule.Reminders) {
            val dirty = customNotifications.filter { it.dirty }
            syncEntity("momentum_custom_notifications", dirty.map { it.toCloud(device) },
                dirty.map { it.uuid }, db.customNotificationDao()::markClean) { applyCustomNotification(it) }
        }
        attempt("momentum_vault_entries", SyncModule.Vault) {
            val accountKey = client.fetchVaultKey()
            try {
                val vault = db.vaultDao().getAllForSync()
                val dirty = vault.filter {
                    it.dirty && (accountKey == null || it.matchesVaultKey(accountKey))
                }
                syncEntity("momentum_vault_entries", dirty.map { it.toCloud(device) },
                    dirty.map { it.uuid }, db.vaultDao()::markClean) { applyVault(it) }
            } finally {
                accountKey?.fill(0)
            }
        }
        if (SyncModuleSettings.isEnabled(context, SyncModule.Files)) attempt { fileStorage.restoreMissingFiles() }
        failures.firstOrNull()?.let { throw it }
        if (SyncModuleSettings.isEnabled(context, SyncModule.Files)) fileStorage.deleteTombstonedObjects()
        val statusDao = db.syncStateDao()
        val status = statusDao.get(SYNC_STATUS_TABLE) ?: SyncState(SYNC_STATUS_TABLE)
        statusDao.upsert(status.copy(lastSyncEpochMillis = System.currentTimeMillis(), lastError = null))
        SyncRunResult(pushed, pulled, conflicts, pending = SupabaseSyncScheduler.pendingCount(db))
    }

    suspend fun setPaused(paused: Boolean) {
        val state = db.syncStateDao().get("momentum_vault_entries") ?: SyncState("momentum_vault_entries")
        db.syncStateDao().upsert(state.copy(paused = paused))
    }

    suspend fun deleteCloudData() {
        allCloudTables.forEach { client.deleteAll(it) }
    }

    suspend fun deleteCloudDataUpdatedToday() {
        val startOfDay = ZonedDateTime.now(ZoneId.systemDefault()).toLocalDate()
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
        allCloudTables.forEach { client.deleteUpdatedSince(it, startOfDay) }
    }

    suspend fun deleteCloudTable(table: String) = client.deleteAll(table)

    suspend fun deleteCloudTableUpdatedToday(table: String) {
        val startOfDay = ZonedDateTime.now(ZoneId.systemDefault()).toLocalDate()
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
        client.deleteUpdatedSince(table, startOfDay)
    }

    suspend fun resetModule(module: AppModule) = withContext(Dispatchers.IO) {
        val tables = moduleTables(module)
        require(tables.isNotEmpty()) { "${module.label} does not contain synced records." }
        if (module == AppModule.Vault) client.deleteAllVaultKeys()
        tables.forEach { client.deleteAll(it) }
        val database = db.openHelper.writableDatabase
        database.beginTransaction()
        try {
            localTables(module).forEach { database.execSQL("DELETE FROM $it") }
            tables.forEach { database.execSQL("DELETE FROM sync_state WHERE tableName = ?", arrayOf(it)) }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    /** Clears this device immediately and keeps the cloud deletion queued for the same account. */
    suspend fun queueModuleDelete(module: SyncModule) = withContext(Dispatchers.IO) {
        val appModule = if (module == SyncModule.Reminders) AppModule.Settings else AppModule.valueOf(module.name)
        val tables = moduleTables(appModule)
        require(tables.isNotEmpty()) { "${module.label} has no synced data." }
        require(!SupabaseAuthApi.currentUserId(context).isNullOrBlank()) {
            "Sign in before deleting synced module data."
        }
        val store = PendingCloudDeleteStore(context)
        store.enqueue(if (module == SyncModule.Vault) tables + PENDING_VAULT_KEYS else tables)

        val database = db.openHelper.writableDatabase
        database.beginTransaction()
        try {
            localTables(appModule).forEach { database.execSQL("DELETE FROM `$it`") }
            tables.forEach { database.execSQL("DELETE FROM sync_state WHERE tableName = ?", arrayOf(it)) }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        SupabaseSyncScheduler.enqueue(context, force = true)
    }

    private suspend fun runPendingModuleDeletes() {
        val store = runCatching { PendingCloudDeleteStore(context) }.getOrNull() ?: return
        for (table in store.pending().toList()) {
            if (table == PENDING_VAULT_KEYS) client.deleteAllVaultKeys() else client.deleteAll(table)
            store.complete(table)
        }
    }

    private fun moduleTables(module: AppModule): List<String> = when (module) {
        AppModule.Habits -> listOf(
            "momentum_habits", "momentum_completions", "momentum_habit_skips", "momentum_habit_reminders"
        )
        AppModule.Money -> listOf("momentum_expenses", "momentum_category_budgets")
        AppModule.Loans -> listOf("momentum_loan_people", "momentum_loans", "momentum_loan_attachments", "momentum_loan_payments")
        AppModule.Contacts -> listOf("momentum_contacts")
        AppModule.Files -> listOf("momentum_files")
        AppModule.ToDos -> listOf("momentum_todos")
        AppModule.LifeRules -> listOf("momentum_life_rules", "momentum_life_rule_files")
        AppModule.Vault -> listOf("momentum_vault_entries")
        AppModule.Settings -> listOf("momentum_custom_notifications")
        AppModule.Profile -> listOf("profiles")
        else -> emptyList()
    }

    private fun localTables(module: AppModule): List<String> = when (module) {
        AppModule.Habits -> listOf("completions", "habit_skips", "habit_reminders", "habits")
        AppModule.Money -> listOf("category_budgets", "expenses")
        AppModule.Loans -> listOf("loan_attachments", "loan_payments", "loans", "loan_people")
        AppModule.Contacts -> listOf("contacts")
        AppModule.LifeRules -> listOf("life_rule_files", "life_rules")
        AppModule.Files -> listOf("library_files")
        AppModule.ToDos -> listOf("todo_items")
        AppModule.Settings -> listOf("custom_notifications")
        AppModule.Vault -> listOf("vault_entries")
        AppModule.Profile -> emptyList()
        else -> emptyList()
    }

    private suspend fun applyHabit(r: JsonObject): Int {
        val old = db.habitDao().getHabitByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        db.habitDao().upsertHabit(r.habit(old?.id ?: 0)); return 0
    }
    private suspend fun applyCompletion(r: JsonObject): Int {
        val habit = db.habitDao().getHabitByUuid(r.string("habit_id")) ?: return 0
        val old = db.habitDao().getCompletionByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        db.habitDao().upsertCompletion(r.completion(habit.id, old?.let { it.habitId to it.epochDay }?.second ?: r.long("epoch_day"))); return 0
    }
    private suspend fun applySkip(r: JsonObject): Int {
        val habit = db.habitDao().getHabitByUuid(r.string("habit_id")) ?: return 0
        val old = db.habitDao().getSkipByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to it.deviceId }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        db.habitDao().upsertSkipForSync(r.skip(habit.id)); return 0
    }
    private suspend fun applyReminder(r: JsonObject): Int {
        val habit = db.habitDao().getHabitByUuid(r.string("habit_id")) ?: return 0
        val old = db.habitDao().getReminderByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        db.habitDao().upsertReminderForSync(r.reminder(habit.id, old?.id ?: 0)); return 0
    }
    private suspend fun applyExpense(r: JsonObject): Int {
        val old = db.expenseDao().getByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        db.expenseDao().upsertExpense(r.expense(old?.id ?: 0)); return 0
    }
    private suspend fun applyContact(r: JsonObject): Int {
        val old = db.contactDao().getByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        db.contactDao().upsertForSync(r.contact(old?.id ?: 0)); return 0
    }
    private suspend fun applyLoanPerson(r: JsonObject): Int {
        val old = db.loanDao().getPersonByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        db.loanDao().upsertPersonForSync(r.loanPerson(old?.id ?: 0)); return 0
    }
    private suspend fun applyBudget(r: JsonObject): Int {
        val old = db.expenseDao().getBudgetByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to it.deviceId }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        db.expenseDao().upsertBudget(r.budget()); return 0
    }
    private suspend fun applyLoan(r: JsonObject): Int {
        val old = db.loanDao().getByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        val personId = r.string("person_id").takeIf { it.isNotBlank() }?.let { db.loanDao().getPersonByUuid(it)?.id }
        db.loanDao().upsertLoan(r.loan(old?.id ?: 0, personId)); return 0
    }
    private suspend fun applyLoanAttachment(r: JsonObject): Int {
        val old = db.loanDao().getAttachmentByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        val loan = db.loanDao().getByUuid(r.string("loan_id")) ?: return 0
        db.loanDao().upsertAttachmentForSync(r.loanAttachment(loan.id, old?.id ?: 0)); return 0
    }
    private suspend fun applyPayment(r: JsonObject): Int {
        val loan = db.loanDao().getByUuid(r.string("loan_id")) ?: return 0
        val old = db.loanDao().getPaymentByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to it.deviceId }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        db.loanDao().upsertPayment(r.payment(loan.id, old?.id ?: 0)); return 0
    }
    private suspend fun applyFile(r: JsonObject): Int {
        val old = db.fileDao().getByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        db.fileDao().upsertForSync(r.file(old?.id ?: 0)); return 0
    }
    private suspend fun applyTodo(r: JsonObject): Int {
        val old = db.toDoDao().getByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        val parent = r["parent_id"].primitiveContentOrNull()?.let { db.toDoDao().getByUuid(it)?.id }
        db.toDoDao().upsertForSync(r.todo(old?.id ?: 0, parent)); return 0
    }
    private suspend fun applyLifeRule(r: JsonObject): Int {
        val old = db.lifeRuleDao().getByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        db.lifeRuleDao().upsertForSync(r.lifeRule(old?.id ?: 0)); return 0
    }
    private suspend fun applyLifeRuleFile(r: JsonObject): Int {
        val old = db.lifeRuleDao().getFileByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        val rule = db.lifeRuleDao().getByUuid(r.string("rule_id")) ?: return 0
        db.lifeRuleDao().upsertFileForSync(r.lifeRuleFile(rule.id, old?.id ?: 0)); return 0
    }
    private suspend fun applyCustomNotification(r: JsonObject): Int {
        val old = db.customNotificationDao().getByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        db.customNotificationDao().upsertForSync(r.customNotification(old?.id ?: 0)); return 0
    }
    private suspend fun applyVault(r: JsonObject): Int {
        val old = db.vaultDao().getByUuid(r.string("id"))
        val decision = accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to it.deviceId }, old?.deleted == true, r)
        if (!decision.applyRemote) return decision.conflictCount()
        db.vaultDao().upsert(r.vault(old?.id ?: 0)); return 0
    }

    private fun VaultRecord.matchesVaultKey(key: ByteArray): Boolean =
        try {
            VaultCrypto.decrypt(nonce, ciphertext, key)
            true
        } catch (_: Exception) {
            false
        }

    private fun accept(
        localTime: Long?,
        dirty: Boolean,
        local: Pair<Long, String>?,
        localDeleted: Boolean,
        r: JsonObject
    ): SyncDecision {
        if (localTime == null || local == null) return SyncDecision(applyRemote = true)
        val localCandidate = SyncCandidate(
            r.string("id"),
            local.first,
            local.second.ifBlank { DeviceIdentity.get(context) },
            localDeleted,
            dirty
        )
        val remote = SyncCandidate(
            r.string("id"),
            r.instantMillis("updated_at"),
            r.string("device_id"),
            r.deleted()
        )
        return decideRemote(localCandidate, remote)
    }

    private fun SyncDecision.conflictCount(): Int = if (conflict) 1 else 0
    /**
     * One bounded, encrypted snapshot per account before its first sync. The
     * old plaintext marker is removed on sight. Account ids are hashed in the
     * filename so no email or UUID is written to disk in plain text.
     */
    private suspend fun createFirstSyncBackup() = withContext(Dispatchers.IO) {
        val legacy = File(context.filesDir, FirstSyncBackup.LEGACY_PLAINTEXT_FILE)
        if (legacy.exists()) legacy.delete()
        val marker = File(context.filesDir, FirstSyncBackup.fileNameForAccount(AppDatabase.activeUserId(context)))
        if (marker.exists()) return@withContext
        val bytes = MomentumBackup.exportBytes(context)
        require(bytes.size <= FirstSyncBackup.MAX_BYTES) { "Backup is larger than the 10 MB safety limit." }
        val encrypted = LocalBackupCrypto.encrypt(context, bytes)
        val temporary = File(marker.parentFile, "${marker.name}.swap")
        temporary.outputStream().use { it.write(encrypted) }
        check(temporary.renameTo(marker)) { "Could not save the first-sync backup." }
    }

    private suspend fun pushAndMarkClean(
        table: String,
        rows: List<JsonObject>,
        markClean: suspend (List<String>) -> Unit
    ): Int = pushAndMarkClean(table, rows, rows.map { it.string("id") }.toSet(), markClean)

    private suspend fun pushAndMarkClean(
        table: String,
        rows: List<JsonObject>,
        expectedIds: Set<String>,
        markClean: suspend (List<String>) -> Unit
    ): Int {
        var pushed = 0
        rows.chunked(PUSH_BATCH).forEach { batch ->
            pushed += client.upsert(table, buildJsonArray { batch.forEach { add(it) } })
            markCleanIfUnchanged(table, batch, expectedIds, markClean)
        }
        return pushed
    }

    private suspend fun markCleanIfUnchanged(
        cloudTable: String,
        rows: List<JsonObject>,
        expectedIds: Set<String>,
        markClean: suspend (List<String>) -> Unit
    ) {
        val localTable = localTableFor(cloudTable)
        db.withTransaction {
            val sqlDb = db.openHelper.writableDatabase
            val unchangedIds = rows.mapNotNull { row ->
                val uuid = row.string("id")
                val updatedAt = row.instantMillis("updated_at")
                if (uuid !in expectedIds) return@mapNotNull null
                sqlDb.query(
                    "SELECT 1 FROM `$localTable` WHERE uuid = ? AND updatedAt = ? LIMIT 1",
                    arrayOf(uuid, updatedAt.toString())
                ).use { cursor -> uuid.takeIf { cursor.moveToFirst() } }
            }
            if (unchangedIds.isNotEmpty()) markClean(unchangedIds)
        }
    }

    private suspend fun dirtyCount(cloudTable: String): Int {
        val localTable = localTableFor(cloudTable)
        return db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM `$localTable` WHERE dirty = 1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }

    private fun localTableFor(cloudTable: String): String = when (cloudTable) {
        "momentum_habits" -> "habits"
        "momentum_completions" -> "completions"
        "momentum_habit_skips" -> "habit_skips"
        "momentum_habit_reminders" -> "habit_reminders"
        "momentum_expenses" -> "expenses"
        "momentum_category_budgets" -> "category_budgets"
        "momentum_loan_people" -> "loan_people"
        "momentum_loans" -> "loans"
        "momentum_loan_attachments" -> "loan_attachments"
        "momentum_loan_payments" -> "loan_payments"
        "momentum_contacts" -> "contacts"
        "momentum_files" -> "library_files"
        "momentum_todos" -> "todo_items"
        "momentum_life_rules" -> "life_rules"
        "momentum_life_rule_files" -> "life_rule_files"
        "momentum_custom_notifications" -> "custom_notifications"
        "momentum_vault_entries" -> "vault_entries"
        else -> error("Unsupported sync table: $cloudTable")
    }
}

object SupabaseSyncScheduler {
    internal val dirtyLocalTables = listOf(
        "habits", "completions", "habit_skips", "habit_reminders", "expenses", "category_budgets",
        "loan_people", "loans", "loan_attachments", "loan_payments", "contacts", "library_files",
        "todo_items", "life_rules", "life_rule_files", "custom_notifications", "vault_entries"
    )

    fun hasDirtyChanges(context: Context): Boolean = pendingCount(AppDatabase.get(context.applicationContext)) > 0

    /** Accurate count of local records still waiting to be pushed after a sync run. */
    fun pendingCount(db: AppDatabase): Int {
        val sqlDb = db.openHelper.readableDatabase
        return dirtyLocalTables.sumOf { table ->
            sqlDb.query("SELECT COUNT(*) FROM `$table` WHERE dirty = 1").use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }
    }

    private fun syncConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /** Periodic reconciliation is opt-in through sign-in and deferred on low battery. */
    fun configurePeriodic(context: Context) {
        val app = context.applicationContext
        if (SupabaseAuthApi.currentUserId(app).isNullOrBlank()) return
        val request = PeriodicWorkRequestBuilder<SupabaseSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(syncConstraints())
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                java.time.Duration.ofSeconds(15)
            )
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            SYNC_PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun enqueue(context: Context, force: Boolean = false) {
        // Cloud sync is opt-in through sign-in. A local-only user may have
        // dirty records; they stay on this device until an account is linked.
        val app = context.applicationContext
        if (SupabaseAuthApi.currentUserId(app).isNullOrBlank()) return
        configurePeriodic(app)
        val request = OneTimeWorkRequestBuilder<SupabaseSyncWorker>()
            .setConstraints(syncConstraints())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, java.time.Duration.ofSeconds(15)).build()
        // Keep a follow-up run if records change during an active sync.
        val policy = if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
        WorkManager.getInstance(app).enqueueUniqueWork(SYNC_WORK_NAME, policy, request)
    }

    suspend fun cancel(context: Context) {
        withContext(Dispatchers.IO) {
            val manager = WorkManager.getInstance(context.applicationContext)
            manager.cancelUniqueWork(SYNC_WORK_NAME).result.get()
            manager.cancelUniqueWork(SYNC_PERIODIC_WORK_NAME).result.get()
        }
    }
}

private fun base(id: String, time: Long, device: String, deleted: Boolean) = buildJsonObject {
    put("id", id); put("updated_at", Instant.ofEpochMilli(time).toString())
    put("deleted_at", if (deleted) Instant.ofEpochMilli(time).toString() else null); put("device_id", device); put("version", 1)
}
private fun JsonObject.merge(other: JsonObject) = buildJsonObject {
    forEach { (key, value) -> put(key, value) }
    other.forEach { (key, value) -> put(key, value) }
}
private fun Habit.toCloud(d: String) = base(uuid, updatedAt, d, deleted).merge(buildJsonObject {
    put("name", name); put("description", description); put("icon", icon); put("color_index", colorIndex); put("time_of_day", timeOfDay)
    put("days_mask", daysMask); put("archived", archived); put("created_epoch_day", createdEpochDay); put("schedule_type", scheduleType)
    put("frequency_per_week", frequencyPerWeek); put("every_n_days", everyNDays); put("target_quantity", targetQuantity); put("quantity_unit", quantityUnit)
    put("timer_minutes", timerMinutes); put("category", category); put("streak_freezes", streakFreezes)
})
private fun Completion.toCloud(d: String, habitUuid: String) = base(uuid, updatedAt, d, deleted).merge(buildJsonObject {
    put("habit_id", habitUuid); put("epoch_day", epochDay); put("note", note); put("quantity", quantity); put("duration_seconds", durationSeconds); put("completed_at_epoch_millis", completedAtEpochMillis)
})
private fun HabitSkip.toCloud(d: String, habitUuid: String) = base(uuid, updatedAt, d.ifBlank { deviceId }, deleted).merge(buildJsonObject { put("habit_id", habitUuid); put("epoch_day", epochDay); put("reason", reason) })
private fun HabitReminder.toCloud(d: String, habitUuid: String) = base(uuid, updatedAt, d, deleted).merge(buildJsonObject { put("habit_id", habitUuid); put("hour", hour); put("minute", minute); put("days_mask", daysMask); put("enabled", enabled); put("date_epoch_day", dateEpochDay) })
private fun Expense.toCloud(d: String) = base(uuid, updatedAt, d, deleted).merge(buildJsonObject { put("title", title); put("amount_minor_units", amountMinorUnits); put("category", category); put("epoch_day", epochDay); put("account", account); put("recurring_rule", recurringRule); put("category_icon", categoryIcon); put("category_color", categoryColor) })
private fun Contact.toCloud(d: String) = base(uuid, updatedAt, d, deleted).merge(buildJsonObject { put("name", name); put("phone", phone); put("email", email); put("address", address); put("notes", notes); put("profile_image_path", if (shouldUploadProfileImage(profileImageUri)) "contacts/$uuid/profile" else "") })
private fun CategoryBudget.toCloud(d: String) = base(uuid, updatedAt, d.ifBlank { deviceId }, deleted).merge(buildJsonObject { put("category", category); put("month_key", monthKey); put("limit_minor_units", limitMinorUnits) })
private fun LoanPerson.toCloud(d: String) = base(uuid, updatedAt, d, deleted).merge(buildJsonObject { put("name", name); put("phone", phone); put("address", address); put("profile_image_path", if (shouldUploadProfileImage(profileImageUri)) "loan-people/$uuid/profile" else ""); put("notes", notes); put("archived", archived) })
private fun Loan.toCloud(d: String, personUuid: String?) = base(uuid, updatedAt, d, deleted).merge(buildJsonObject { put("person", person); put("person_id", personUuid); put("amount_minor_units", amountMinorUnits); put("lent", lent); put("note", note); put("epoch_day", epochDay); put("settled", settled); put("due_epoch_day", dueEpochDay); put("title", title); put("status", status) })
private fun LoanAttachment.toCloud(d: String, loanUuid: String) = base(uuid, updatedAt, d, deleted).merge(buildJsonObject { put("loan_id", loanUuid); put("name", name); put("object_path", "loan-attachments/$uuid"); put("mime", mime); put("added_at", Instant.ofEpochMilli(addedAt).toString()) })
private fun LoanPayment.toCloud(d: String, loanUuid: String) = base(uuid, updatedAt, d.ifBlank { deviceId }, deleted).merge(buildJsonObject { put("loan_id", loanUuid); put("amount_minor_units", amountMinorUnits); put("epoch_day", epochDay); put("note", note) })
private fun FileItem.toCloud(d: String) = base(uuid, updatedAt, d, deleted).merge(buildJsonObject { put("name", name); put("uri", uri); put("mime", mime); put("added_epoch_day", addedEpochDay); put("tags", tags); put("folder", folder); put("last_accessed_epoch_day", lastAccessedEpochDay); put("missing", missing) })
private fun ToDoItem.toCloud(d: String, parentUuid: String) = base(uuid, updatedAt, d, deleted).merge(buildJsonObject { put("title", title); put("notes", notes); put("due_epoch_day", dueEpochDay); put("due_epoch_millis", dueEpochMillis); put("completed", completed); put("category", category); put("priority", priority); put("reminder_hour", reminderHour); put("reminder_minute", reminderMinute); put("repeat_rule", repeatRule); put("project", project); put("labels", labels); put("parent_id", parentUuid.ifBlank { null }); put("sort_order", sortOrder) })
private fun LifeRule.toCloud(d: String) = base(uuid, updatedAt, d, deleted).merge(buildJsonObject { put("title", title); put("body", body); put("kind", kind); put("pinned", pinned); put("created_epoch_day", createdEpochDay) })
private fun LifeRuleFile.toCloud(d: String, ruleUuid: String) = base(uuid, updatedAt, d, deleted).merge(buildJsonObject { put("rule_id", ruleUuid); put("name", name); put("object_path", "life-rule-files/$uuid"); put("mime", mime); put("folder_path", folderPath); put("added_at", Instant.ofEpochMilli(addedAt).toString()) })
private fun CustomNotification.toCloud(d: String) = base(uuid, updatedAt, d, deleted).merge(buildJsonObject { put("title", title); put("message", message); put("repeat_mode", repeatMode); put("hour", hour); put("minute", minute); put("date_epoch_day", dateEpochDay); put("days_mask", daysMask); put("day_of_month", dayOfMonth); put("enabled", enabled); put("last_delivered_epoch_millis", lastDeliveredEpochMillis) })
private fun VaultRecord.toCloud(d: String) = toCloudJson(d)

internal fun VaultRecord.toCloudJson(device: String): JsonObject = buildJsonObject {
    put("id", uuid)
    put("nonce", "\\x${nonce.hex()}")
    put("ciphertext", "\\x${ciphertext.hex()}")
    put("updated_at", Instant.ofEpochMilli(updatedAt).toString())
    put("deleted_at", if (deleted) Instant.ofEpochMilli(updatedAt).toString() else null)
    put("device_id", device.ifBlank { deviceId })
    put("version", 1)
}

private fun JsonObject.habit(id: Int) = Habit(id, string("name"), string("description"), string("icon"), int("color_index"), int("time_of_day"), int("days_mask"), bool("archived"), long("created_epoch_day"), string("id"), instantMillis("updated_at"), deleted(), false, string("schedule_type"), int("frequency_per_week"), int("every_n_days", 1), int("target_quantity"), string("quantity_unit"), int("timer_minutes"), string("category"), int("streak_freezes"))
private fun JsonObject.completion(habitId: Int, epoch: Long) = Completion(habitId, epoch, string("id"), instantMillis("updated_at"), deleted(), false, string("note"), int("quantity"), long("duration_seconds"), long("completed_at_epoch_millis"))
private fun JsonObject.skip(habitId: Int) = HabitSkip(habitId, long("epoch_day"), string("reason"), string("id"), instantMillis("updated_at"), deleted(), false, string("device_id"))
private fun JsonObject.reminder(habitId: Int, id: Int) = HabitReminder(id, habitId, int("hour"), int("minute"), int("days_mask"), bool("enabled"), nullableLong("date_epoch_day"), string("id"), instantMillis("updated_at"), deleted(), false)
private fun JsonObject.expense(id: Int) = Expense(id, string("title"), long("amount_minor_units"), string("category"), long("epoch_day"), string("id"), instantMillis("updated_at"), deleted(), false, string("account"), string("recurring_rule"), string("category_icon"), int("category_color"))
private fun JsonObject.contact(id: Int) = Contact(id, string("name"), string("phone"), string("email"), string("address"), string("notes"), profilePathToStoredUri(string("profile_image_path")), string("id"), instantMillis("updated_at"), deleted(), false)
private fun JsonObject.budget() = CategoryBudget(string("category"), string("month_key"), long("limit_minor_units"), string("id"), instantMillis("updated_at"), deleted(), false, string("device_id"))
private fun JsonObject.loan(id: Int, personId: Int?) = Loan(id, string("person"), long("amount_minor_units"), bool("lent"), string("note"), long("epoch_day"), bool("settled"), string("id"), instantMillis("updated_at"), deleted(), false, nullableLong("due_epoch_day"), string("title"), string("status").ifBlank { "pending" }, personId)
private fun JsonObject.loanPerson(id: Int) = LoanPerson(id, string("name"), string("phone"), string("address"), profilePathToStoredUri(string("profile_image_path")), string("notes"), bool("archived"), string("id"), instantMillis("updated_at"), deleted(), false)
private fun JsonObject.loanAttachment(loanId: Int, id: Int) = LoanAttachment(id, loanId, string("name"), string("object_path"), string("mime"), instantMillis("added_at"), string("id"), instantMillis("updated_at"), deleted(), false)
private fun JsonObject.payment(loanId: Int, id: Int) = LoanPayment(id, loanId, long("amount_minor_units"), long("epoch_day"), string("note"), string("id"), instantMillis("updated_at"), deleted(), false, string("device_id"))
private fun JsonObject.file(id: Int) = FileItem(id, string("name"), string("uri"), string("mime"), long("added_epoch_day"), string("id"), instantMillis("updated_at"), deleted(), false, string("tags"), string("folder"), long("last_accessed_epoch_day"), bool("missing"))
private fun JsonObject.todo(id: Int, parent: Int?) = ToDoItem(id, string("title"), string("notes"), long("due_epoch_day"), bool("completed"), string("category"), int("priority"), nullableInt("reminder_hour"), nullableInt("reminder_minute"), string("id"), instantMillis("updated_at"), deleted(), false, nullableLong("due_epoch_millis"), string("repeat_rule"), string("project"), string("labels"), parent, long("sort_order"))
private fun JsonObject.lifeRule(id: Int) = LifeRule(id, string("title"), string("body"), string("kind"), bool("pinned"), long("created_epoch_day"), string("id"), instantMillis("updated_at"), deleted(), false)
private fun JsonObject.lifeRuleFile(ruleId: Int, id: Int) = LifeRuleFile(id, ruleId, string("name"), string("object_path"), string("mime"), string("folder_path"), instantMillis("added_at"), string("id"), instantMillis("updated_at"), deleted(), false)
private fun JsonObject.customNotification(id: Int) = CustomNotification(id, string("title"), string("message"), string("repeat_mode").ifBlank { "once" }, int("hour", 9), int("minute"), nullableLong("date_epoch_day"), int("days_mask", ALL_DAYS), int("day_of_month", 1), bool("enabled"), long("last_delivered_epoch_millis"), string("id"), instantMillis("updated_at"), deleted(), false)
private fun JsonObject.vault(id: Int) = VaultRecord(id, getBytea("nonce"), getBytea("ciphertext"), instantMillis("updated_at"), string("id"), deleted(), false, string("device_id"))
