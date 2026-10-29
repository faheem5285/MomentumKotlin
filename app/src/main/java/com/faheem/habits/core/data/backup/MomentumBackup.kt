package com.faheem.habits.core.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.faheem.habits.core.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Merge-only JSON backup for non-secret Momentum records.
 * Password Vault ciphertext is deliberately excluded because its Android Keystore key
 * is device-bound. File entries export metadata/URIs only, not the file bytes.
 */
object MomentumBackup {
    private const val VERSION = 1
    private const val MAX_BYTES = 10 * 1024 * 1024
    private const val MAX_ROWS = 10_000
    private const val PREFS = "momentum_backup_imports"

    suspend fun export(context: Context, destination: Uri) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val root = JSONObject()
            .put("format", "momentum-backup")
            .put("version", VERSION)
            .put("backupId", UUID.randomUUID().toString())
            .put("exportedAt", System.currentTimeMillis())
            .put("contents", "non-secret app records; vault entries and file contents excluded")
        val habits = db.habitDao().getAllForBackup()
        val habitJson = JSONArray()
        habits.forEach { habitJson.put(JSONObject()
            .put("id", habit.id).put("name", habit.name).put("description", habit.description)
            .put("icon", habit.icon).put("colorIndex", habit.colorIndex).put("timeOfDay", habit.timeOfDay)
            .put("daysMask", habit.daysMask).put("archived", habit.archived).put("createdEpochDay", habit.createdEpochDay)) }
        val completions = JSONArray()
        db.habitDao().getCompletionsForBackup().forEach { completions.put(JSONObject().put("habitId", it.habitId).put("epochDay", it.epochDay)) }
        val reminders = JSONArray()
        db.habitDao().getRemindersForBackup().forEach { reminders.put(JSONObject()
            .put("habitId", it.habitId).put("hour", it.hour).put("minute", it.minute).put("daysMask", it.daysMask)
            .put("enabled", it.enabled).put("dateEpochDay", it.dateEpochDay ?: JSONObject.NULL)) }
        val expenses = JSONArray()
        db.expenseDao().getAllForBackup().forEach { expenses.put(JSONObject().put("title", it.title).put("amount", it.amount).put("category", it.category).put("epochDay", it.epochDay)) }
        val loans = JSONArray()
        db.loanDao().getAllForBackup().forEach { loans.put(JSONObject().put("person", it.person).put("amount", it.amount).put("lent", it.lent).put("note", it.note).put("epochDay", it.epochDay).put("settled", it.settled)) }
        val files = JSONArray()
        db.fileDao().getAllForBackup().forEach { files.put(JSONObject().put("name", it.name).put("uri", it.uri).put("mime", it.mime).put("addedEpochDay", it.addedEpochDay)) }
        val todos = JSONArray()
        db.toDoDao().getAllForBackup().forEach { todos.put(JSONObject().put("title", it.title).put("notes", it.notes).put("dueEpochDay", it.dueEpochDay)
            .put("completed", it.completed).put("category", it.category).put("priority", it.priority)
            .put("reminderHour", it.reminderHour ?: JSONObject.NULL).put("reminderMinute", it.reminderMinute ?: JSONObject.NULL)) }
        root.put("habits", habitJson).put("completions", completions).put("reminders", reminders)
            .put("expenses", expenses).put("loans", loans).put("files", files).put("todos", todos)
        val bytes = root.toString(2).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Backup is larger than the 10 MB safety limit." }
        val output = context.contentResolver.openOutputStream(destination, "w")
            ?: error("Could not open the selected backup destination.")
        output.use { it.write(bytes) }
    }

    suspend fun restore(context: Context, source: Uri): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(source) ?: error("Could not open the selected backup file.")
        val bytes = input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_BYTES) { "Backup exceeds the 10 MB safety limit." }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optString("format") == "momentum-backup") { "This is not a Momentum backup." }
        require(root.optInt("version", -1) == VERSION) { "This backup version is not supported." }
        val backupId = root.optString("backupId")
        require(backupId.isNotBlank() && backupId.length <= 100) { "Backup ID is missing or invalid." }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        require(!prefs.getBoolean(backupId, false)) { "This backup file has already been restored on this device." }
        val keys = listOf("habits", "completions", "reminders", "expenses", "loans", "files", "todos")
        keys.forEach { key -> require(root.optJSONArray(key) != null) { "Backup is missing '$key' data." }; require(root.getJSONArray(key).length() <= MAX_ROWS) { "Too many records in '$key'." } }

        val db = AppDatabase.get(context)
        var count = 0
        db.withTransaction {
            val habitIds = mutableMapOf<Int, Int>()
            val habits = root.getJSONArray("habits")
            for (i in 0 until habits.length()) {
                val o = habits.getJSONObject(i)
                require(o.optString("name").isNotBlank() && o.optString("name").length <= 300) { "Invalid habit name." }
                val oldId = o.getInt("id")
                val newId = db.habitDao().insertHabit(Habit(
                    name = o.getString("name"), description = o.optString("description"),
                    icon = o.optString("icon", "star"), colorIndex = o.optInt("colorIndex"),
                    timeOfDay = o.optInt("timeOfDay"), daysMask = o.optInt("daysMask", ALL_DAYS),
                    archived = o.optBoolean("archived"), createdEpochDay = o.optLong("createdEpochDay")
                )).toInt()
                habitIds[oldId] = newId; count++
            }
            val completions = root.getJSONArray("completions")
            for (i in 0 until completions.length()) {
                val o = completions.getJSONObject(i)
                val mapped = habitIds[o.getInt("habitId")] ?: error("Completion refers to a missing habit.")
                db.habitDao().insertCompletion(Completion(mapped, o.getLong("epochDay"))); count++
            }
            val reminders = root.getJSONArray("reminders")
            for (i in 0 until reminders.length()) {
                val o = reminders.getJSONObject(i)
                val mapped = habitIds[o.getInt("habitId")] ?: error("Reminder refers to a missing habit.")
                val date = if (o.isNull("dateEpochDay")) null else o.getLong("dateEpochDay")
                require(o.getInt("hour") in 0..23 && o.getInt("minute") in 0..59) { "Invalid reminder time." }
                db.habitDao().insertReminder(HabitReminder(habitId = mapped, hour = o.getInt("hour"), minute = o.getInt("minute"),
                    daysMask = o.optInt("daysMask", ALL_DAYS), enabled = o.optBoolean("enabled", true), dateEpochDay = date)); count++
            }
            val expenses = root.getJSONArray("expenses")
            for (i in 0 until expenses.length()) {
                val o = expenses.getJSONObject(i); val amount = o.getDouble("amount")
                require(amount.isFinite() && amount >= 0 && o.getString("title").length <= 300) { "Invalid expense record." }
                db.expenseDao().insert(Expense(title = o.getString("title"), amount = amount, category = o.getString("category"), epochDay = o.getLong("epochDay"))); count++
            }
            val loans = root.getJSONArray("loans")
            for (i in 0 until loans.length()) {
                val o = loans.getJSONObject(i); val amount = o.getDouble("amount")
                require(amount.isFinite() && amount >= 0 && o.getString("person").length <= 300) { "Invalid loan record." }
                db.loanDao().insert(Loan(person = o.getString("person"), amount = amount, lent = o.getBoolean("lent"), note = o.optString("note"),
                    epochDay = o.getLong("epochDay"), settled = o.optBoolean("settled"))); count++
            }
            val files = root.getJSONArray("files")
            for (i in 0 until files.length()) {
                val o = files.getJSONObject(i)
                db.fileDao().insert(FileItem(name = o.getString("name"), uri = o.getString("uri"), mime = o.getString("mime"), addedEpochDay = o.getLong("addedEpochDay"))); count++
            }
            val todos = root.getJSONArray("todos")
            for (i in 0 until todos.length()) {
                val o = todos.getJSONObject(i)
                val hour = if (o.isNull("reminderHour")) null else o.getInt("reminderHour")
                val minute = if (o.isNull("reminderMinute")) null else o.getInt("reminderMinute")
                require(hour == null || hour in 0..23) { "Invalid task reminder hour." }
                require(minute == null || minute in 0..59) { "Invalid task reminder minute." }
                db.toDoDao().insert(ToDoItem(title = o.getString("title"), notes = o.optString("notes"), dueEpochDay = o.getLong("dueEpochDay"),
                    completed = o.optBoolean("completed"), category = o.optString("category", "Personal"), priority = o.optInt("priority", 1),
                    reminderHour = hour, reminderMinute = minute)); count++
            }
        }
        prefs.edit().putBoolean(backupId, true).apply()
        "Restored $count records. Existing records were kept. Vault entries and file contents were not restored."
    }
}
