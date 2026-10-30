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
import java.math.BigDecimal
import java.math.RoundingMode

/** Versioned JSON backup for non-vault data. Vault plaintext is never read or written here. */
object MomentumBackup {
    private const val VERSION = 2
    private const val MAX_BYTES = 10 * 1024 * 1024
    private const val MAX_ROWS = 10_000
    private const val APP_PREFS = "momentum_app_settings"
    private const val MONEY_PREFS = "momentum"

    data class Preview(val habits: Int, val expenses: Int, val todos: Int, val files: Int, val loans: Int, val replace: Boolean = false)

    suspend fun export(context: Context, destination: Uri) = withContext(Dispatchers.IO) {
        val bytes = exportBytes(context)
        require(bytes.size <= MAX_BYTES) { "Backup is larger than the 10 MB safety limit." }
        val output = context.contentResolver.openOutputStream(destination, "w") ?: error("Could not open the selected backup destination.")
        output.use { it.write(bytes) }
    }

    internal suspend fun exportBytes(context: Context, db: AppDatabase = AppDatabase.get(context)): ByteArray = withContext(Dispatchers.IO) {
        val root = db.withTransaction {
            val result = JSONObject()
                .put("format", "momentum-backup")
                .put("formatVersion", VERSION)
                .put("schemaVersion", 11)
                .put("exportedAt", System.currentTimeMillis())
                .put("backupId", UUID.randomUUID().toString())
            fun meta(uuid: String, updatedAt: Long, deleted: Boolean, dirty: Boolean) = JSONObject()
                .put("uuid", uuid).put("updatedAt", updatedAt).put("deleted", deleted).put("dirty", dirty)
            val habits = JSONArray()
            db.habitDao().getAllForBackup().forEach { h -> habits.put(meta(h.uuid,h.updatedAt,h.deleted,h.dirty)
                .put("id",h.id).put("name",h.name).put("description",h.description).put("icon",h.icon)
                .put("colorIndex",h.colorIndex).put("timeOfDay",h.timeOfDay).put("daysMask",h.daysMask)
                .put("archived",h.archived).put("createdEpochDay",h.createdEpochDay)) }
            val byId = db.habitDao().getAllForBackup().associateBy { it.id }
            val completions = JSONArray()
            db.habitDao().getCompletionsForBackup().forEach { c -> completions.put(meta(c.uuid,c.updatedAt,c.deleted,c.dirty)
                .put("habitUuid", byId[c.habitId]?.uuid ?: error("Completion has no habit"))
                .put("epochDay",c.epochDay)) }
            val reminders = JSONArray()
            db.habitDao().getRemindersForBackup().forEach { r -> reminders.put(meta(r.uuid,r.updatedAt,r.deleted,r.dirty)
                .put("habitUuid",byId[r.habitId]?.uuid ?: error("Reminder has no habit"))
                .put("hour",r.hour).put("minute",r.minute).put("daysMask",r.daysMask).put("enabled",r.enabled)
                .put("dateEpochDay",r.dateEpochDay ?: JSONObject.NULL)) }
            val expenses = JSONArray()
            db.expenseDao().getAllForBackup().forEach { e -> expenses.put(meta(e.uuid,e.updatedAt,e.deleted,e.dirty)
                .put("title",e.title).put("amountMinorUnits",e.amountMinorUnits).put("category",e.category).put("epochDay",e.epochDay)) }
            val loans = JSONArray()
            db.loanDao().getAllForBackup().forEach { l -> loans.put(meta(l.uuid,l.updatedAt,l.deleted,l.dirty)
                .put("person",l.person).put("amountMinorUnits",l.amountMinorUnits).put("lent",l.lent).put("note",l.note)
                .put("epochDay",l.epochDay).put("settled",l.settled)) }
            val files = JSONArray()
            db.fileDao().getAllForBackup().forEach { f -> files.put(meta(f.uuid,f.updatedAt,f.deleted,f.dirty)
                .put("name",f.name).put("uri",f.uri).put("mime",f.mime).put("addedEpochDay",f.addedEpochDay)) }
            val todos = JSONArray()
            db.toDoDao().getAllForBackup().forEach { t -> todos.put(meta(t.uuid,t.updatedAt,t.deleted,t.dirty)
                .put("title",t.title).put("notes",t.notes).put("dueEpochDay",t.dueEpochDay).put("completed",t.completed)
                .put("category",t.category).put("priority",t.priority)
                .put("reminderHour",t.reminderHour ?: JSONObject.NULL).put("reminderMinute",t.reminderMinute ?: JSONObject.NULL)) }
            result.put("habits",habits).put("completions",completions).put("reminders",reminders)
                .put("expenses",expenses).put("loans",loans).put("files",files).put("todos",todos)
                .put("budgets", exportPreferences(context.getSharedPreferences(MONEY_PREFS, Context.MODE_PRIVATE), setOf("budget", "budget_minor_units"), "money_limit_"))
                .put("settings", exportPreferences(context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE), emptySet(), "module_enabled_").put("permission_setup_seen", context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).getBoolean("permission_setup_seen", false)))
            result
        }
        val bytes = root.toString(2).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Backup is larger than the 10 MB safety limit." }
        bytes
    }

    suspend fun preview(context: Context, source: Uri): Preview = withContext(Dispatchers.IO) {
        val root = readRoot(context, source)
        Preview(root.getJSONArray("habits").length(), root.getJSONArray("expenses").length(), root.getJSONArray("todos").length(),
            root.getJSONArray("files").length(), root.getJSONArray("loans").length())
    }

    suspend fun restore(context: Context, source: Uri, replace: Boolean): String = withContext(Dispatchers.IO) {
        restoreRoot(context, readRoot(context, source), replace, AppDatabase.get(context))
    }

    internal suspend fun restoreBytes(context: Context, bytes: ByteArray, replace: Boolean, db: AppDatabase = AppDatabase.get(context)): String = withContext(Dispatchers.IO) {
        restoreRoot(context, parseRoot(bytes), replace, db)
    }

    private suspend fun restoreRoot(context: Context, root: JSONObject, replace: Boolean, db: AppDatabase): String {
        var added = 0
        db.withTransaction {
            if (replace) {
                db.toDoDao().deleteAll(); db.fileDao().deleteAll(); db.loanDao().deleteAll(); db.expenseDao().deleteAll()
                db.habitDao().deleteAllReminders(); db.habitDao().deleteAllCompletions(); db.habitDao().deleteAllHabits()
                clearExportedPreferences(context.getSharedPreferences(MONEY_PREFS, Context.MODE_PRIVATE), setOf("budget", "budget_minor_units"), "money_limit_")
                clearExportedPreferences(context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE), setOf("permission_setup_seen"), "module_enabled_")
            }
            val habitIds = mutableMapOf<String, Int>()
            val habits = root.getJSONArray("habits")
            for (i in 0 until habits.length()) {
                val o=habits.getJSONObject(i); val uuid=validUuid(o)
                require(o.getString("name").isNotBlank() && o.getString("name").length<=300) { "Invalid habit name." }
                val existing=db.habitDao().getHabitByUuid(uuid)
                val id=existing?.id ?: db.habitDao().insertImportedHabit(Habit(name=o.getString("name"),description=o.optString("description"),
                    icon=o.optString("icon","star"),colorIndex=o.optInt("colorIndex"),timeOfDay=o.optInt("timeOfDay"),daysMask=o.optInt("daysMask",ALL_DAYS),
                    archived=o.optBoolean("archived"),createdEpochDay=o.optLong("createdEpochDay"),uuid=uuid,updatedAt=o.optLong("updatedAt"),
                    deleted=o.optBoolean("deleted"),dirty=o.optBoolean("dirty",true))).toInt().takeIf { it>0 }
                    ?: db.habitDao().getHabitByUuid(uuid)?.id ?: error("Could not import habit.")
                if(existing==null) added++
                habitIds[uuid]=id
            }
            val completions=root.getJSONArray("completions")
            for(i in 0 until completions.length()) { val o=completions.getJSONObject(i); val uuid=validUuid(o); val parent=validUuid(o,"habitUuid"); val habitId=habitIds[parent] ?: db.habitDao().getHabitByUuid(parent)?.id ?: error("Completion refers to a missing habit.")
                if(db.habitDao().getCompletionByUuid(uuid)==null) { db.habitDao().insertImportedCompletion(Completion(habitId,o.getLong("epochDay"),uuid,o.optLong("updatedAt"),o.optBoolean("deleted"),o.optBoolean("dirty",true))); added++ } }
            val reminders=root.getJSONArray("reminders")
            for(i in 0 until reminders.length()) { val o=reminders.getJSONObject(i); val uuid=validUuid(o); if(db.habitDao().getReminderByUuid(uuid)!=null) continue
                val habitId=habitIds[validUuid(o,"habitUuid")] ?: error("Reminder refers to a missing habit."); val hour=o.getInt("hour"); val minute=o.getInt("minute")
                require(hour in 0..23 && minute in 0..59) { "Invalid reminder time." }
                val date=if(o.isNull("dateEpochDay")) null else o.getLong("dateEpochDay")
                db.habitDao().insertImportedReminder(HabitReminder(habitId=habitId,hour=hour,minute=minute,daysMask=o.optInt("daysMask",ALL_DAYS),enabled=o.optBoolean("enabled",true),dateEpochDay=date,
                    uuid=uuid,updatedAt=o.optLong("updatedAt"),deleted=o.optBoolean("deleted"),dirty=o.optBoolean("dirty",true))); added++ }
            val expenses=root.getJSONArray("expenses")
            for(i in 0 until expenses.length()) { val o=expenses.getJSONObject(i); val uuid=validUuid(o); val amount=o.getLong("amountMinorUnits")
                require(amount>=0 && o.getString("title").length<=300) { "Invalid expense record." }
                if(db.expenseDao().getByUuid(uuid)==null) { db.expenseDao().insertImported(Expense(title=o.getString("title"),amountMinorUnits=amount,category=o.getString("category"),epochDay=o.getLong("epochDay"),uuid=uuid,updatedAt=o.optLong("updatedAt"),deleted=o.optBoolean("deleted"),dirty=o.optBoolean("dirty",true))); added++ } }
            val loans=root.getJSONArray("loans")
            for(i in 0 until loans.length()) { val o=loans.getJSONObject(i); val uuid=validUuid(o); val amount=o.getLong("amountMinorUnits")
                require(amount>=0 && o.getString("person").length<=300) { "Invalid loan record." }
                if(db.loanDao().getByUuid(uuid)==null) { db.loanDao().insertImported(Loan(person=o.getString("person"),amountMinorUnits=amount,lent=o.getBoolean("lent"),note=o.optString("note"),epochDay=o.getLong("epochDay"),settled=o.optBoolean("settled"),uuid=uuid,updatedAt=o.optLong("updatedAt"),deleted=o.optBoolean("deleted"),dirty=o.optBoolean("dirty",true))); added++ } }
            val files=root.getJSONArray("files")
            for(i in 0 until files.length()) { val o=files.getJSONObject(i); val uuid=validUuid(o)
                if(db.fileDao().getByUuid(uuid)==null) { db.fileDao().insertImported(FileItem(name=o.getString("name"),uri=o.getString("uri"),mime=o.getString("mime"),addedEpochDay=o.getLong("addedEpochDay"),uuid=uuid,updatedAt=o.optLong("updatedAt"),deleted=o.optBoolean("deleted"),dirty=o.optBoolean("dirty",true))); added++ } }
            val todos=root.getJSONArray("todos")
            for(i in 0 until todos.length()) { val o=todos.getJSONObject(i); val uuid=validUuid(o); val hour=if(o.isNull("reminderHour")) null else o.getInt("reminderHour"); val minute=if(o.isNull("reminderMinute")) null else o.getInt("reminderMinute")
                require((hour==null || hour in 0..23)&&(minute==null || minute in 0..59)) { "Invalid task reminder time." }
                if(db.toDoDao().getByUuid(uuid)==null) { db.toDoDao().insertImported(ToDoItem(title=o.getString("title"),notes=o.optString("notes"),dueEpochDay=o.getLong("dueEpochDay"),completed=o.optBoolean("completed"),category=o.optString("category","Personal"),priority=o.optInt("priority",1),reminderHour=hour,reminderMinute=minute,uuid=uuid,updatedAt=o.optLong("updatedAt"),deleted=o.optBoolean("deleted"),dirty=o.optBoolean("dirty",true))); added++ } }
            importPreferences(root.optJSONObject("budgets"), context.getSharedPreferences(MONEY_PREFS, Context.MODE_PRIVATE), setOf("budget", "budget_minor_units"), "money_limit_")
            importPreferences(root.optJSONObject("settings"), context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE), setOf("permission_setup_seen"), "module_enabled_")
        }
        return "${if(replace) "Replaced with" else "Added"} $added new records. Repeating an import will not duplicate UUIDs. Vault entries are kept separately and were not imported."
    }

    private fun exportPreferences(prefs: android.content.SharedPreferences, exact: Set<String>, prefix: String): JSONObject = JSONObject().apply {
        prefs.all.filterKeys { it in exact || it.startsWith(prefix) }.forEach { (key,value) -> when(value) {
            is Boolean -> put(key,value); is Int -> put(key,value); is Long -> put(key,value); is Float -> put(key,value.toDouble()); is String -> put(key,value)
        } }
    }
    private fun importPreferences(values: JSONObject?, prefs: android.content.SharedPreferences, exact: Set<String>, prefix: String) {
        if(values==null) return
        val editor=prefs.edit()
        for(i in 0 until values.length()) { val key=values.names()?.getString(i) ?: continue; if(key !in exact && !key.startsWith(prefix)) continue
            when(val value=values.get(key)) { is Boolean -> editor.putBoolean(key,value); is Int -> editor.putInt(key,value); is Long -> editor.putLong(key,value); is Double -> editor.putFloat(key,value.toFloat()); is String -> editor.putString(key,value) }
        }
        check(editor.commit()) { "Could not restore app settings." }
    }
    private fun clearExportedPreferences(prefs: android.content.SharedPreferences, exact: Set<String>, prefix: String) {
        val editor=prefs.edit()
        prefs.all.keys.filter { it in exact || it.startsWith(prefix) }.forEach(editor::remove)
        check(editor.commit()) { "Could not replace app settings." }
    }
    private fun validUuid(obj: JSONObject, key: String = "uuid"): String {
        val value=obj.optString(key); require(value.length in 1..64 && runCatching { UUID.fromString(value); true }.getOrDefault(false)) { "Missing or invalid UUID." }; return value
    }
    private fun readRoot(context: Context, source: Uri): JSONObject {
        val input=context.contentResolver.openInputStream(source) ?: error("Could not open the selected backup file.")
        val bytes=input.use { stream -> val out=ByteArrayOutputStream(); val buffer=ByteArray(8192); var total=0; while(true) { val n=stream.read(buffer); if(n<0) break; total+=n; require(total<=MAX_BYTES) { "Backup exceeds the 10 MB safety limit." }; out.write(buffer,0,n) }; out.toByteArray() }
        return parseRoot(bytes)
    }

    private fun parseRoot(bytes: ByteArray): JSONObject {
        val root=JSONObject(bytes.toString(Charsets.UTF_8)); require(root.optString("format")=="momentum-backup") { "This is not a Momentum backup." }
        if(root.optInt("formatVersion",-1)==1 || root.optInt("version",-1)==1) upgradeV1(root)
        require(root.optInt("formatVersion",-1)==VERSION && root.optInt("schemaVersion",-1)==11) { "This backup version is not supported." }
        require(root.optLong("exportedAt",0)>0) { "Backup timestamp is missing." }
        listOf("habits","completions","reminders","expenses","loans","files","todos").forEach { key -> val rows=root.optJSONArray(key) ?: error("Backup is missing '$key' data."); require(rows.length()<=MAX_ROWS) { "Too many records in '$key'." } }
        return root
    }

    private fun upgradeV1(root: JSONObject) {
        val backupId=root.optString("backupId"); require(backupId.isNotBlank()) { "Backup ID is missing." }
        val exportedAt=root.optLong("exportedAt",System.currentTimeMillis())
        val habitUuids=mutableMapOf<Int,String>()
        fun stable(table:String,index:Int)=UUID.nameUUIDFromBytes("$backupId:$table:$index".toByteArray(Charsets.UTF_8)).toString()
        fun addMeta(rows:JSONArray,table:String,transform:(JSONObject)->Unit={}) {
            for(i in 0 until rows.length()) { val item=rows.getJSONObject(i); item.put("uuid",stable(table,i)).put("updatedAt",exportedAt).put("deleted",false).put("dirty",true); transform(item) }
        }
        addMeta(root.getJSONArray("habits"),"habits") { item -> habitUuids[item.getInt("id")]=item.getString("uuid") }
        addMeta(root.getJSONArray("completions"),"completions") { item -> item.put("habitUuid",habitUuids[item.getInt("habitId")] ?: error("Completion refers to a missing habit.")) }
        addMeta(root.getJSONArray("reminders"),"reminders") { item -> item.put("habitUuid",habitUuids[item.getInt("habitId")] ?: error("Reminder refers to a missing habit.")) }
        listOf("expenses","loans").forEach { key -> addMeta(root.getJSONArray(key),key) { item ->
            val legacy=item.getDouble("amount"); require(legacy.isFinite()); val minor=BigDecimal.valueOf(legacy).setScale(2,RoundingMode.HALF_UP).movePointRight(2).longValueExact()
            item.remove("amount"); item.put("amountMinorUnits",minor)
        } }
        listOf("files","todos").forEach { key -> addMeta(root.getJSONArray(key),key) }
        root.put("formatVersion",VERSION).put("schemaVersion",11).put("exportedAt",exportedAt)
            .put("budgets",JSONObject()).put("settings",JSONObject())
    }
}
