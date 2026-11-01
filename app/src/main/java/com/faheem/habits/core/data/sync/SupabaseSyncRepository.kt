@file:Suppress("TooManyFunctions")

package com.faheem.habits.core.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.faheem.habits.core.data.*
import com.faheem.habits.core.data.backup.MomentumBackup
import com.faheem.habits.core.data.supabase.SupabaseRestClient
import java.io.File
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

private val SYNC_EPOCH = "1970-01-01T00:00:00Z"

class SupabaseSyncRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.get(context),
    private val client: SupabaseRestClient = SupabaseRestClient(context),
    private val fileStorage: FileStorageSync = FileStorageSync(context, db, client)
) {
    suspend fun sync(): SyncRunResult = withContext(Dispatchers.IO) {
        val paused = db.syncStateDao().get("momentum_vault_entries")?.paused == true
        if (paused) return@withContext SyncRunResult()
        createFirstSyncBackup()
        fileStorage.uploadPending()
        val device = DeviceIdentity.get(context)
        var pushed = 0
        var pulled = 0
        var conflicts = 0
        var pending = 0
        suspend fun syncEntity(
            table: String,
            rows: List<JsonObject>,
            uuids: List<String>,
            markClean: suspend (List<String>) -> Unit,
            apply: suspend (JsonObject) -> Int
        ) {
            val stateDao = db.syncStateDao()
            val old = stateDao.get(table) ?: SyncState(table)
            pushed += client.upsert(table, buildJsonArray { rows.forEach { add(it) } })
            if (uuids.isNotEmpty()) markClean(uuids)
            val remote = client.pull(table, old.cursor.ifBlank { SYNC_EPOCH })
            var cursor = old.cursor
            remote.forEach { element ->
                val json = element.jsonObject
                cursor = maxOf(cursor, json.string("updated_at"))
                conflicts += apply(json)
            }
            pulled += remote.size
            val remaining = rows.count { it["id"]?.jsonPrimitive?.contentOrNull in uuids }
            pending += remaining
            stateDao.upsert(old.copy(cursor = cursor, pendingCount = remaining,
                conflictCount = old.conflictCount + conflicts, lastSyncEpochMillis = System.currentTimeMillis(),
                lastError = null))
        }

        val habits = db.habitDao().getAllForSync()
        val habitIds = habits.associate { it.id to it.uuid }
        syncEntity("momentum_habits", habits.filter { it.dirty }.map { it.toCloud(device) }, habits.filter { it.dirty }.map { it.uuid },
            db.habitDao()::markHabitsClean) { applyHabit(it) }
        val completions = db.habitDao().getCompletionsForBackup()
        syncEntity("momentum_completions", completions.filter { it.dirty }.map { it.toCloud(device, habitIds[it.habitId].orEmpty()) },
            completions.filter { it.dirty }.map { it.uuid }, db.habitDao()::markCompletionsClean) { applyCompletion(it) }
        val skips = db.habitDao().getAllSkipsForSync()
        syncEntity("momentum_habit_skips", skips.filter { it.dirty }.map { it.toCloud(device, habitIds[it.habitId].orEmpty()) },
            skips.filter { it.dirty }.map { it.uuid }, db.habitDao()::markSkipsClean) { applySkip(it) }
        val reminders = db.habitDao().getAllRemindersForSync()
        syncEntity("momentum_habit_reminders", reminders.filter { it.dirty }.map { it.toCloud(device, habitIds[it.habitId].orEmpty()) },
            reminders.filter { it.dirty }.map { it.uuid }, db.habitDao()::markRemindersClean) { applyReminder(it) }

        val expenses = db.expenseDao().getAllForSync()
        syncEntity("momentum_expenses", expenses.filter { it.dirty }.map { it.toCloud(device) },
            expenses.filter { it.dirty }.map { it.uuid }, db.expenseDao()::markClean) { applyExpense(it) }
        val budgets = db.expenseDao().getAllBudgetsForSync()
        syncEntity("momentum_category_budgets", budgets.filter { it.dirty }.map { it.toCloud(device) },
            budgets.filter { it.dirty }.map { it.uuid }, db.expenseDao()::markBudgetsClean) { applyBudget(it) }

        val loans = db.loanDao().getAllForSync()
        val loanIds = loans.associate { it.id to it.uuid }
        syncEntity("momentum_loans", loans.filter { it.dirty }.map { it.toCloud(device) },
            loans.filter { it.dirty }.map { it.uuid }, db.loanDao()::markClean) { applyLoan(it) }
        val payments = db.loanDao().getAllPaymentsForSync()
        syncEntity("momentum_loan_payments", payments.filter { it.dirty }.map { it.toCloud(device, loanIds[it.loanId].orEmpty()) },
            payments.filter { it.dirty }.map { it.uuid }, db.loanDao()::markPaymentsClean) { applyPayment(it) }

        val files = db.fileDao().getAllForSync()
        syncEntity("momentum_files", files.filter { it.dirty }.map { it.toCloud(device) },
            files.filter { it.dirty }.map { it.uuid }, db.fileDao()::markClean) { applyFile(it) }
        val todos = db.toDoDao().getAllForSync()
        val todoIds = todos.associate { it.id to it.uuid }
        syncEntity("momentum_todos", todos.filter { it.dirty }.map { it.toCloud(device, todoIds[it.parentId].orEmpty()) },
            todos.filter { it.dirty }.map { it.uuid }, db.toDoDao()::markClean) { applyTodo(it) }
        val vault = db.vaultDao().getAll()
        syncEntity("momentum_vault_entries", vault.filter { it.dirty }.map { it.toCloud(device) },
            vault.filter { it.dirty }.map { it.uuid }, db.vaultDao()::markClean) { applyVault(it) }
        fileStorage.restoreMissingFiles()
        SyncRunResult(pushed, pulled, conflicts, pending)
    }

    suspend fun setPaused(paused: Boolean) {
        val state = db.syncStateDao().get("momentum_vault_entries") ?: SyncState("momentum_vault_entries")
        db.syncStateDao().upsert(state.copy(paused = paused))
    }

    suspend fun deleteCloudData() {
        listOf("momentum_habits", "momentum_completions", "momentum_habit_skips", "momentum_habit_reminders",
            "momentum_expenses", "momentum_category_budgets", "momentum_loans", "momentum_loan_payments",
            "momentum_files", "momentum_todos", "momentum_vault_entries").forEach { client.deleteAll(it) }
    }

    private suspend fun applyHabit(r: JsonObject): Int {
        val old = db.habitDao().getHabitByUuid(r.string("id"))
        if (!accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, r)) return if (old?.dirty == true) 1 else 0
        db.habitDao().upsertHabit(r.habit(old?.id ?: 0)); return 0
    }
    private suspend fun applyCompletion(r: JsonObject): Int {
        val habit = db.habitDao().getHabitByUuid(r.string("habit_id")) ?: return 0
        val old = db.habitDao().getCompletionByUuid(r.string("id"))
        if (!accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, r)) return if (old?.dirty == true) 1 else 0
        db.habitDao().upsertCompletion(r.completion(habit.id, old?.let { it.habitId to it.epochDay }?.second ?: r.long("epoch_day"))); return 0
    }
    private suspend fun applySkip(r: JsonObject): Int {
        val habit = db.habitDao().getHabitByUuid(r.string("habit_id")) ?: return 0
        val old = db.habitDao().getSkipByUuid(r.string("id"))
        if (!accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to it.deviceId }, r)) return if (old?.dirty == true) 1 else 0
        db.habitDao().upsertSkipForSync(r.skip(habit.id)); return 0
    }
    private suspend fun applyReminder(r: JsonObject): Int {
        val habit = db.habitDao().getHabitByUuid(r.string("habit_id")) ?: return 0
        val old = db.habitDao().getReminderByUuid(r.string("id"))
        if (!accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, r)) return if (old?.dirty == true) 1 else 0
        db.habitDao().upsertReminderForSync(r.reminder(habit.id, old?.id ?: 0)); return 0
    }
    private suspend fun applyExpense(r: JsonObject): Int {
        val old = db.expenseDao().getByUuid(r.string("id"))
        if (!accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, r)) return if (old?.dirty == true) 1 else 0
        db.expenseDao().upsertExpense(r.expense(old?.id ?: 0)); return 0
    }
    private suspend fun applyBudget(r: JsonObject): Int {
        val old = db.expenseDao().getBudgetByUuid(r.string("id"))
        if (!accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to it.deviceId }, r)) return if (old?.dirty == true) 1 else 0
        db.expenseDao().upsertBudget(r.budget()); return 0
    }
    private suspend fun applyLoan(r: JsonObject): Int {
        val old = db.loanDao().getByUuid(r.string("id"))
        if (!accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, r)) return if (old?.dirty == true) 1 else 0
        db.loanDao().upsertLoan(r.loan(old?.id ?: 0)); return 0
    }
    private suspend fun applyPayment(r: JsonObject): Int {
        val loan = db.loanDao().getByUuid(r.string("loan_id")) ?: return 0
        val old = db.loanDao().getPaymentByUuid(r.string("id"))
        if (!accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to it.deviceId }, r)) return if (old?.dirty == true) 1 else 0
        db.loanDao().upsertPayment(r.payment(loan.id, old?.id ?: 0)); return 0
    }
    private suspend fun applyFile(r: JsonObject): Int {
        val old = db.fileDao().getByUuid(r.string("id"))
        if (!accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, r)) return if (old?.dirty == true) 1 else 0
        db.fileDao().upsertForSync(r.file(old?.id ?: 0)); return 0
    }
    private suspend fun applyTodo(r: JsonObject): Int {
        val old = db.toDoDao().getByUuid(r.string("id"))
        if (!accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to "" }, r)) return if (old?.dirty == true) 1 else 0
        val parent = r["parent_id"]?.jsonPrimitive?.contentOrNull?.let { db.toDoDao().getByUuid(it)?.id }
        db.toDoDao().upsertForSync(r.todo(old?.id ?: 0, parent)); return 0
    }
    private suspend fun applyVault(r: JsonObject): Int {
        val old = db.vaultDao().getByUuid(r.string("id"))
        if (!accept(old?.updatedAt, old?.dirty == true, old?.let { it.updatedAt to it.deviceId }, r)) return if (old?.dirty == true) 1 else 0
        db.vaultDao().upsert(r.vault(old?.id ?: 0)); return 0
    }

    private fun accept(localTime: Long?, dirty: Boolean, local: Pair<Long, String>?, r: JsonObject): Boolean {
        if (localTime == null) return true
        val localCandidate = local ?: return true
        val remote = SyncCandidate(r.string("id"), r.instantMillis("updated_at"), r.string("device_id"), r.deleted())
        val candidate = SyncCandidate(
            r.string("id"), localCandidate.first,
            localCandidate.second.ifBlank { DeviceIdentity.get(context) }, false
        )
        return remote.winsOver(candidate)
    }
    private suspend fun createFirstSyncBackup() {
        val marker = File(context.filesDir, "sync-first-local-backup.json")
        if (!marker.exists()) marker.outputStream().use { it.write(MomentumBackup.exportBytes(context)) }
    }
}

object SupabaseSyncScheduler {
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<SupabaseSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, java.time.Duration.ofSeconds(30)).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork("momentum-sync", ExistingWorkPolicy.KEEP, request)
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
private fun CategoryBudget.toCloud(d: String) = base(uuid, updatedAt, d.ifBlank { deviceId }, deleted).merge(buildJsonObject { put("category", category); put("month_key", monthKey); put("limit_minor_units", limitMinorUnits) })
private fun Loan.toCloud(d: String) = base(uuid, updatedAt, d, deleted).merge(buildJsonObject { put("person", person); put("amount_minor_units", amountMinorUnits); put("lent", lent); put("note", note); put("epoch_day", epochDay); put("settled", settled); put("due_epoch_day", dueEpochDay) })
private fun LoanPayment.toCloud(d: String, loanUuid: String) = base(uuid, updatedAt, d.ifBlank { deviceId }, deleted).merge(buildJsonObject { put("loan_id", loanUuid); put("amount_minor_units", amountMinorUnits); put("epoch_day", epochDay); put("note", note) })
private fun FileItem.toCloud(d: String) = base(uuid, updatedAt, d, deleted).merge(buildJsonObject { put("name", name); put("uri", uri); put("mime", mime); put("added_epoch_day", addedEpochDay); put("tags", tags); put("folder", folder); put("last_accessed_epoch_day", lastAccessedEpochDay); put("missing", missing) })
private fun ToDoItem.toCloud(d: String, parentUuid: String) = base(uuid, updatedAt, d, deleted).merge(buildJsonObject { put("title", title); put("notes", notes); put("due_epoch_day", dueEpochDay); put("due_epoch_millis", dueEpochMillis); put("completed", completed); put("category", category); put("priority", priority); put("reminder_hour", reminderHour); put("reminder_minute", reminderMinute); put("repeat_rule", repeatRule); put("project", project); put("labels", labels); put("parent_id", parentUuid.ifBlank { null }); put("sort_order", sortOrder) })
private fun VaultRecord.toCloud(d: String) = base(uuid, updatedAt, d.ifBlank { deviceId }, deleted).merge(buildJsonObject { put("nonce", "\\x${nonce.hex()}"); put("ciphertext", "\\x${ciphertext.hex()}") })

private fun JsonObject.habit(id: Int) = Habit(id, string("name"), string("description"), string("icon"), int("color_index"), int("time_of_day"), int("days_mask"), bool("archived"), long("created_epoch_day"), string("id"), instantMillis("updated_at"), deleted(), false, string("schedule_type"), int("frequency_per_week"), int("every_n_days", 1), int("target_quantity"), string("quantity_unit"), int("timer_minutes"), string("category"), int("streak_freezes"))
private fun JsonObject.completion(habitId: Int, epoch: Long) = Completion(habitId, epoch, string("id"), instantMillis("updated_at"), deleted(), false, string("note"), int("quantity"), long("duration_seconds"), long("completed_at_epoch_millis"))
private fun JsonObject.skip(habitId: Int) = HabitSkip(habitId, long("epoch_day"), string("reason"), string("id"), instantMillis("updated_at"), deleted(), false, string("device_id"))
private fun JsonObject.reminder(habitId: Int, id: Int) = HabitReminder(id, habitId, int("hour"), int("minute"), int("days_mask"), bool("enabled"), nullableLong("date_epoch_day"), string("id"), instantMillis("updated_at"), deleted(), false)
private fun JsonObject.expense(id: Int) = Expense(id, string("title"), long("amount_minor_units"), string("category"), long("epoch_day"), string("id"), instantMillis("updated_at"), deleted(), false, string("account"), string("recurring_rule"), string("category_icon"), int("category_color"))
private fun JsonObject.budget() = CategoryBudget(string("category"), string("month_key"), long("limit_minor_units"), string("id"), instantMillis("updated_at"), deleted(), false, string("device_id"))
private fun JsonObject.loan(id: Int) = Loan(id, string("person"), long("amount_minor_units"), bool("lent"), string("note"), long("epoch_day"), bool("settled"), string("id"), instantMillis("updated_at"), deleted(), false, nullableLong("due_epoch_day"))
private fun JsonObject.payment(loanId: Int, id: Int) = LoanPayment(id, loanId, long("amount_minor_units"), long("epoch_day"), string("note"), string("id"), instantMillis("updated_at"), deleted(), false, string("device_id"))
private fun JsonObject.file(id: Int) = FileItem(id, string("name"), string("uri"), string("mime"), long("added_epoch_day"), string("id"), instantMillis("updated_at"), deleted(), false, string("tags"), string("folder"), long("last_accessed_epoch_day"), bool("missing"))
private fun JsonObject.todo(id: Int, parent: Int?) = ToDoItem(id, string("title"), string("notes"), long("due_epoch_day"), bool("completed"), string("category"), int("priority"), nullableInt("reminder_hour"), nullableInt("reminder_minute"), string("id"), instantMillis("updated_at"), deleted(), false, nullableLong("due_epoch_millis"), string("repeat_rule"), string("project"), string("labels"), parent, long("sort_order"))
private fun JsonObject.vault(id: Int) = VaultRecord(id, getBytea("nonce"), getBytea("ciphertext"), instantMillis("updated_at"), string("id"), deleted(), false, string("device_id"))

private fun JsonObject.string(n: String) = this[n]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.int(n: String, default: Int = 0) = string(n).toIntOrNull() ?: default
private fun JsonObject.long(n: String) = string(n).toLongOrNull() ?: 0L
private fun JsonObject.nullableLong(n: String) = this[n]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
private fun JsonObject.nullableInt(n: String) = this[n]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
private fun JsonObject.bool(n: String) = this[n]?.jsonPrimitive?.booleanOrNull ?: false
private fun JsonObject.deleted() = this["deleted_at"]?.jsonPrimitive?.contentOrNull != null
private fun JsonObject.instantMillis(n: String) = try { Instant.parse(string(n)).toEpochMilli() } catch (_: DateTimeParseException) { 0L }
private fun JsonObject.getBytea(n: String) = string(n).removePrefix("\\x").chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
