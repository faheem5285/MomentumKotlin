package com.faheem.habits.core.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.faheem.habits.core.data.*
import com.faheem.habits.core.settings.MomentumSettingsBackup
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
    private const val VERSION = BackupFormatPolicy.VERSION
    private const val CURRENT_SCHEMA = BackupFormatPolicy.CURRENT_SCHEMA
    private const val FORMAT_VERSION_3 = BackupFormatPolicy.FORMAT_VERSION_3
    private const val SCHEMA_VERSION_20 = BackupFormatPolicy.SCHEMA_VERSION_20
    private const val PREVIOUS_VERSION = BackupFormatPolicy.PREVIOUS_VERSION
    private val LEGACY_SCHEMA_VERSIONS = BackupFormatPolicy.LEGACY_SCHEMAS
    private const val MAX_BYTES = 10 * 1024 * 1024
    /** Authenticated envelopes add a fixed header and tag to the JSON payload. */
    private const val MAX_IMPORT_BYTES = MAX_BYTES + 1_024
    private const val MAX_ROWS = 10_000
    private val BACKUP_TABLE_KEYS = BackupFormatPolicy.TABLE_KEYS

    data class Preview(
        val habits: Int,
        val expenses: Int,
        val todos: Int,
        val files: Int,
        val loans: Int,
        val rules: Int = 0,
        val notifications: Int = 0,
        val contacts: Int = 0,
        val vocabulary: Int = 0,
        val grammar: Int = 0,
        val translations: Int = 0,
        val recordCount: Int = 0,
        val encrypted: Boolean = false
    )

    suspend fun export(
        context: Context,
        destination: Uri,
        passphrase: CharArray? = null
    ) = withContext(Dispatchers.IO) {
        val bytes = exportBytes(context, passphrase = passphrase)
        require(bytes.size <= MAX_IMPORT_BYTES) { "Backup is larger than the 10 MB safety limit." }
        val output = context.contentResolver.openOutputStream(destination, "w") ?: error("Could not open the selected backup destination.")
        output.use { it.write(bytes) }
    }

    suspend fun exportBytes(
        context: Context,
        db: AppDatabase = AppDatabase.get(context),
        passphrase: CharArray? = null
    ): ByteArray = withContext(Dispatchers.IO) {
        val (settingsBackup, budgetBackup) = MomentumSettingsBackup(context).exportPreferences()
        val root = db.withTransaction {
            val result = JSONObject()
                .put("format", "momentum-backup")
                .put("formatVersion", VERSION)
                .put("schemaVersion", CURRENT_SCHEMA)
                .put("exportedAt", System.currentTimeMillis())
                .put("backupId", UUID.randomUUID().toString())
            fun meta(uuid: String, updatedAt: Long, deleted: Boolean, dirty: Boolean) = JSONObject()
                .put("uuid", uuid).put("updatedAt", updatedAt).put("deleted", deleted).put("dirty", dirty)
            val habits = JSONArray()
            db.habitDao().getAllForBackup().forEach { h -> habits.put(meta(h.uuid,h.updatedAt,h.deleted,h.dirty)
                .put("id",h.id).put("name",h.name).put("description",h.description).put("icon",h.icon)
                .put("colorIndex",h.colorIndex).put("timeOfDay",h.timeOfDay).put("daysMask",h.daysMask)
                .put("archived",h.archived).put("createdEpochDay",h.createdEpochDay)
                .put("scheduleType",h.scheduleType).put("frequencyPerWeek",h.frequencyPerWeek)
                .put("everyNDays",h.everyNDays).put("targetQuantity",h.targetQuantity)
                .put("quantityUnit",h.quantityUnit).put("timerMinutes",h.timerMinutes)
                .put("category",h.category).put("streakFreezes",h.streakFreezes)) }
            val byId = db.habitDao().getAllForBackup().associateBy { it.id }
            val completions = JSONArray()
            db.habitDao().getCompletionsForBackup().forEach { c -> completions.put(meta(c.uuid,c.updatedAt,c.deleted,c.dirty)
                .put("habitUuid", byId[c.habitId]?.uuid ?: error("Completion has no habit"))
                .put("epochDay",c.epochDay).put("note",c.note).put("quantity",c.quantity)
                .put("durationSeconds",c.durationSeconds).put("completedAtEpochMillis",c.completedAtEpochMillis)) }
            val habitSkips = JSONArray()
            db.habitDao().getAllSkipsForSync().forEach { skip ->
                habitSkips.put(meta(skip.uuid, skip.updatedAt, skip.deleted, skip.dirty)
                    .put("habitUuid", byId[skip.habitId]?.uuid ?: error("Habit skip has no habit"))
                    .put("epochDay", skip.epochDay).put("reason", skip.reason).put("deviceId", skip.deviceId))
            }
            val reminders = JSONArray()
            db.habitDao().getRemindersForBackup().forEach { r -> reminders.put(meta(r.uuid,r.updatedAt,r.deleted,r.dirty)
                .put("habitUuid",byId[r.habitId]?.uuid ?: error("Reminder has no habit"))
                .put("hour",r.hour).put("minute",r.minute).put("daysMask",r.daysMask).put("enabled",r.enabled)
                .put("dateEpochDay",r.dateEpochDay ?: JSONObject.NULL)) }
            val expenses = JSONArray()
            db.expenseDao().getAllForBackup().forEach { e -> expenses.put(meta(e.uuid,e.updatedAt,e.deleted,e.dirty)
                .put("title",e.title).put("amountMinorUnits",e.amountMinorUnits).put("category",e.category)
                .put("epochDay",e.epochDay).put("account",e.account).put("recurringRule",e.recurringRule)
                .put("categoryIcon",e.categoryIcon).put("categoryColor",e.categoryColor)) }
            val categoryBudgets = JSONArray()
            db.expenseDao().getAllBudgetsForSync().forEach { budget ->
                categoryBudgets.put(meta(budget.uuid, budget.updatedAt, budget.deleted, budget.dirty)
                    .put("category", budget.category).put("monthKey", budget.monthKey)
                    .put("limitMinorUnits", budget.limitMinorUnits).put("deviceId", budget.deviceId))
            }
            val loanPeople = db.loanDao().getAllPeopleForSync()
            val loanPeopleById = loanPeople.associateBy { it.id }
            val people = JSONArray()
            loanPeople.forEach { person ->
                people.put(meta(person.uuid, person.updatedAt, person.deleted, person.dirty)
                    .put("name", person.name).put("phone", person.phone).put("address", person.address)
                    .put("profileImageUri", person.profileImageUri).put("notes", person.notes)
                    .put("archived", person.archived))
            }
            val contacts = JSONArray()
            db.contactDao().getAllForBackup().forEach { contact ->
                contacts.put(meta(contact.uuid, contact.updatedAt, contact.deleted, contact.dirty)
                    .put("name", contact.name).put("phone", contact.phone).put("email", contact.email)
                    .put("address", contact.address).put("notes", contact.notes)
                    .put("profileImageUri", contact.profileImageUri))
            }
            val loans = JSONArray()
            val loanRows = db.loanDao().getAllForBackup()
            val loanIds = loanRows.associate { it.id to it.uuid }
            loanRows.forEach { l -> loans.put(meta(l.uuid,l.updatedAt,l.deleted,l.dirty)
                .put("person",l.person).put("amountMinorUnits",l.amountMinorUnits).put("lent",l.lent).put("note",l.note)
                .put("epochDay",l.epochDay).put("settled",l.settled).put("dueEpochDay",l.dueEpochDay ?: JSONObject.NULL)
                .put("title",l.title).put("status",l.status)
                .put("personUuid", l.personId?.let { loanPeopleById[it]?.uuid } ?: JSONObject.NULL)) }
            val loanAttachments = JSONArray()
            db.loanDao().getAllAttachmentsForSync().forEach { attachment ->
                loanAttachments.put(meta(attachment.uuid, attachment.updatedAt, attachment.deleted, attachment.dirty)
                    .put("loanUuid", loanIds[attachment.loanId] ?: error("Loan attachment has no loan"))
                    .put("name", attachment.name).put("uri", attachment.uri).put("mime", attachment.mime)
                    .put("addedAt", attachment.addedAt))
            }
            val loanPayments = JSONArray()
            db.loanDao().getAllPaymentsForSync().forEach { payment ->
                loanPayments.put(meta(payment.uuid, payment.updatedAt, payment.deleted, payment.dirty)
                    .put("loanUuid", loanIds[payment.loanId] ?: error("Loan payment has no loan"))
                    .put("amountMinorUnits", payment.amountMinorUnits).put("epochDay", payment.epochDay)
                    .put("note", payment.note).put("deviceId", payment.deviceId))
            }
            val files = JSONArray()
            db.fileDao().getAllForBackup().forEach { f -> files.put(meta(f.uuid,f.updatedAt,f.deleted,f.dirty)
                .put("name",f.name).put("uri",f.uri).put("mime",f.mime).put("addedEpochDay",f.addedEpochDay)
                .put("tags",f.tags).put("folder",f.folder).put("lastAccessedEpochDay",f.lastAccessedEpochDay)
                .put("missing",f.missing)) }
            val todos = JSONArray()
            val todoRows = db.toDoDao().getAllForBackup()
            val todoUuidsById = todoRows.associate { it.id to it.uuid }
            todoRows.forEach { t -> todos.put(meta(t.uuid,t.updatedAt,t.deleted,t.dirty)
                .put("title",t.title).put("notes",t.notes).put("dueEpochDay",t.dueEpochDay).put("completed",t.completed)
                .put("category",t.category).put("priority",t.priority)
                .put("reminderHour",t.reminderHour ?: JSONObject.NULL).put("reminderMinute",t.reminderMinute ?: JSONObject.NULL)
                .put("dueEpochMillis",t.dueEpochMillis ?: JSONObject.NULL).put("repeatRule",t.repeatRule)
                .put("project",t.project).put("labels",t.labels)
                .put("parentUuid", t.parentId?.let(todoUuidsById::get) ?: JSONObject.NULL)
                .put("sortOrder",t.sortOrder)) }
            val lifeRules = lifeRulesJson(db)
            val lifeRuleIds = db.lifeRuleDao().getAllForBackup().associate { it.id to it.uuid }
            val lifeRuleFiles = JSONArray()
            db.lifeRuleDao().getAllFilesForSync().forEach { file ->
                lifeRuleFiles.put(meta(file.uuid, file.updatedAt, file.deleted, file.dirty)
                    .put("ruleUuid", lifeRuleIds[file.ruleId] ?: error("Life-rule file has no parent"))
                    .put("name", file.name).put("uri", file.uri).put("mime", file.mime)
                    .put("folderPath", file.folderPath).put("addedAt", file.addedAt))
            }
            val customNotifications = JSONArray()
            db.customNotificationDao().getAllForBackup().forEach { n ->
                customNotifications.put(
                    meta(n.uuid, n.updatedAt, n.deleted, n.dirty)
                        .put("title", n.title).put("message", n.message)
                        .put("repeatMode", n.repeatMode).put("hour", n.hour).put("minute", n.minute)
                        .put("dateEpochDay", n.dateEpochDay ?: JSONObject.NULL)
                        .put("daysMask", n.daysMask).put("dayOfMonth", n.dayOfMonth)
                        .put("enabled", n.enabled)
                        .put("lastDeliveredEpochMillis", n.lastDeliveredEpochMillis)
                )
            }
            val vocabularyWords = JSONArray()
            db.vocabularyDao().allForExport().forEach { word ->
                vocabularyWords.put(JSONObject()
                    .put("id", word.id).put("word", word.word).put("meaning", word.meaning)
                    .put("partOfSpeech", word.partOfSpeech).put("example", word.example)
                    .put("language", word.language).put("tags", word.tags)
                    .put("difficulty", word.difficulty).put("mastered", word.mastered)
                    .put("createdAt", word.createdAt).put("updatedAt", word.updatedAt))
            }
            val grammarNotes = JSONArray()
            db.grammarNoteDao().allForExport().forEach { note ->
                grammarNotes.put(JSONObject()
                    .put("id", note.id).put("title", note.title).put("category", note.category)
                    .put("explanation", note.explanation).put("correctExample", note.correctExample)
                    .put("incorrectExample", note.incorrectExample).put("commonMistake", note.commonMistake)
                    .put("pinned", note.pinned).put("tags", note.tags).put("updatedAt", note.updatedAt))
            }
            val reviewStates = JSONArray()
            db.reviewStateDao().allForExport().forEach { state ->
                reviewStates.put(JSONObject()
                    .put("wordId", state.wordId).put("box", state.box)
                    .put("nextReviewAt", state.nextReviewAt).put("lastReviewedAt", state.lastReviewedAt)
                    .put("correctCount", state.correctCount).put("reviewCount", state.reviewCount))
            }
            val translationHistory = JSONArray()
            db.translationHistoryDao().allForExport().forEach { item ->
                translationHistory.put(JSONObject()
                    .put("id", item.id).put("sourceLanguage", item.sourceLanguage)
                    .put("targetLanguage", item.targetLanguage).put("sourceText", item.sourceText)
                    .put("translatedText", item.translatedText).put("createdAt", item.createdAt)
                    .put("favorite", item.favorite))
            }
            result.put("habits",habits).put("completions",completions).put("habitSkips",habitSkips)
                .put("reminders",reminders)
                .put("expenses",expenses).put("loans",loans).put("files",files).put("todos",todos)
                .put("contacts", contacts).put("loanPeople", people)
                .put("loanAttachments", loanAttachments).put("loanPayments", loanPayments)
                .put("categoryBudgets", categoryBudgets)
                .put("lifeRules", lifeRules).put("lifeRuleFiles", lifeRuleFiles)
                .put("customNotifications", customNotifications)
                .put("vocabularyWords", vocabularyWords).put("grammarNotes", grammarNotes)
                .put("reviewStates", reviewStates).put("translationHistory", translationHistory)
                .put("budgets", budgetBackup)
                .put("settings", settingsBackup)
            result
        }
        val bytes = root.toString(2).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Backup is larger than the 10 MB safety limit." }
        val secret = passphrase?.takeIf { it.isNotEmpty() }
        if (secret == null) bytes else PortableBackupCrypto.encrypt(bytes, secret)
    }

    suspend fun preview(
        context: Context,
        source: Uri,
        passphrase: CharArray? = null
    ): Preview = withContext(Dispatchers.IO) {
        val packed = readBytes(context, source)
        val root = parseRoot(decodeBytes(packed, passphrase))
        val keys = BACKUP_TABLE_KEYS
        Preview(
            habits = root.getJSONArray("habits").length(),
            expenses = root.getJSONArray("expenses").length(),
            todos = root.getJSONArray("todos").length(),
            files = root.getJSONArray("files").length(),
            loans = root.getJSONArray("loans").length(),
            rules = root.optJSONArray("lifeRules")?.length() ?: 0,
            notifications = root.optJSONArray("customNotifications")?.length() ?: 0,
            contacts = root.optJSONArray("contacts")?.length() ?: 0,
            vocabulary = root.optJSONArray("vocabularyWords")?.length() ?: 0,
            grammar = root.optJSONArray("grammarNotes")?.length() ?: 0,
            translations = root.optJSONArray("translationHistory")?.length() ?: 0,
            recordCount = keys.sumOf { root.optJSONArray(it)?.length() ?: 0 },
            encrypted = PortableBackupCrypto.isEncrypted(packed)
        )
    }

    suspend fun restore(
        context: Context,
        source: Uri,
        replace: Boolean,
        passphrase: CharArray? = null
    ): String = withContext(Dispatchers.IO) {
        restoreRoot(context, parseRoot(decodeBytes(readBytes(context, source), passphrase)),
            replace, AppDatabase.get(context))
    }

    internal suspend fun restoreBytes(
        context: Context,
        bytes: ByteArray,
        replace: Boolean,
        db: AppDatabase = AppDatabase.get(context),
        passphrase: CharArray? = null
    ): String = withContext(Dispatchers.IO) {
        restoreRoot(context, parseRoot(decodeBytes(bytes, passphrase)), replace, db)
    }

    private suspend fun restoreRoot(context: Context, root: JSONObject, replace: Boolean, db: AppDatabase): String {
        var added = 0
        db.withTransaction {
            if (replace) {
                db.toDoDao().deleteAll(); db.fileDao().deleteAll(); db.loanDao().deleteAll(); db.expenseDao().deleteAll()
                db.loanDao().deleteAllPeople(); db.contactDao().deleteAll()
                db.expenseDao().deleteAllBudgets(); db.lifeRuleDao().deleteAll()
                db.customNotificationDao().deleteAll()
                db.vocabularyDao().deleteAll(); db.grammarNoteDao().deleteAll()
                db.reviewStateDao().deleteAll(); db.translationHistoryDao().clear()
                db.syncStateDao().deleteAll()
                db.habitDao().deleteAllReminders(); db.habitDao().deleteAllCompletions(); db.habitDao().deleteAllHabits()
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
                    deleted=o.optBoolean("deleted"),dirty=o.optBoolean("dirty",true),
                    scheduleType=o.optString("scheduleType","specific_days"),frequencyPerWeek=o.optInt("frequencyPerWeek"),
                    everyNDays=o.optInt("everyNDays",1),targetQuantity=o.optInt("targetQuantity",1),
                    quantityUnit=o.optString("quantityUnit"),timerMinutes=o.optInt("timerMinutes"),
                    category=o.optString("category","General"),streakFreezes=o.optInt("streakFreezes"))).toInt().takeIf { it>0 }
                    ?: db.habitDao().getHabitByUuid(uuid)?.id ?: error("Could not import habit.")
                if(existing==null) added++
                habitIds[uuid]=id
            }
            val completions=root.getJSONArray("completions")
            for(i in 0 until completions.length()) { val o=completions.getJSONObject(i); val uuid=validUuid(o); val parent=validUuid(o,"habitUuid"); val habitId=habitIds[parent] ?: db.habitDao().getHabitByUuid(parent)?.id ?: error("Completion refers to a missing habit.")
                if(db.habitDao().getCompletionByUuid(uuid)==null) { db.habitDao().insertImportedCompletion(Completion(
                    habitId,o.getLong("epochDay"),uuid,o.optLong("updatedAt"),o.optBoolean("deleted"),o.optBoolean("dirty",true),
                    o.optString("note"),o.optInt("quantity",1),o.optLong("durationSeconds"),o.optLong("completedAtEpochMillis")
                )); added++ } }
            val habitSkips=root.getJSONArray("habitSkips")
            for(i in 0 until habitSkips.length()) {
                val o=habitSkips.getJSONObject(i); val uuid=validUuid(o); val parent=validUuid(o,"habitUuid")
                val habitId=habitIds[parent] ?: db.habitDao().getHabitByUuid(parent)?.id ?: error("Habit skip refers to a missing habit.")
                if(db.habitDao().getSkipByUuid(uuid)==null) {
                    val skip=HabitSkip(habitId,o.getLong("epochDay"),o.optString("reason","freeze"),uuid,
                        o.optLong("updatedAt"),o.optBoolean("deleted"),o.optBoolean("dirty",true),o.optString("deviceId"))
                    if(db.habitDao().getSkip(habitId,skip.epochDay)==null) { db.habitDao().upsertSkip(skip); added++ }
                }
            }
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
                if(db.expenseDao().getByUuid(uuid)==null) { db.expenseDao().insertImported(Expense(title=o.getString("title"),amountMinorUnits=amount,category=o.getString("category"),epochDay=o.getLong("epochDay"),uuid=uuid,updatedAt=o.optLong("updatedAt"),deleted=o.optBoolean("deleted"),dirty=o.optBoolean("dirty",true),
                    account=o.optString("account","Cash"),recurringRule=o.optString("recurringRule"),
                    categoryIcon=o.optString("categoryIcon"),categoryColor=o.optInt("categoryColor"))); added++ } }
            val categoryBudgets=root.getJSONArray("categoryBudgets")
            for(i in 0 until categoryBudgets.length()) {
                val o=categoryBudgets.getJSONObject(i); val uuid=validUuid(o); val category=o.getString("category")
                val monthKey=o.getString("monthKey"); val limit=o.getLong("limitMinorUnits")
                require(category.isNotBlank() && limit>=0) { "Invalid category budget." }
                if(db.expenseDao().getBudgetByUuid(uuid)==null && db.expenseDao().getBudget(category,monthKey)==null) {
                    db.expenseDao().upsertBudget(CategoryBudget(category,monthKey,limit,uuid,o.optLong("updatedAt"),
                        o.optBoolean("deleted"),o.optBoolean("dirty",true),o.optString("deviceId")))
                    added++
                }
            }
            val people=root.getJSONArray("loanPeople")
            val personIds=mutableMapOf<String,Int>()
            for(i in 0 until people.length()) {
                val o=people.getJSONObject(i); val uuid=validUuid(o)
                require(o.getString("name").isNotBlank() && o.getString("name").length<=300) { "Invalid loan person." }
                val existing=db.loanDao().getPersonByUuid(uuid)
                val id=existing?.id ?: db.loanDao().insertPerson(LoanPerson(name=o.getString("name"),
                    phone=o.optString("phone"),address=o.optString("address"),profileImageUri=o.optString("profileImageUri"),
                    notes=o.optString("notes"),archived=o.optBoolean("archived"),uuid=uuid,updatedAt=o.optLong("updatedAt"),
                    deleted=o.optBoolean("deleted"),dirty=o.optBoolean("dirty",true))).toInt()
                if(existing==null) added++
                personIds[uuid]=id
            }
            val contacts=root.getJSONArray("contacts")
            for(i in 0 until contacts.length()) {
                val o=contacts.getJSONObject(i); val uuid=validUuid(o)
                require(o.getString("name").isNotBlank() && o.getString("name").length<=300) { "Invalid contact." }
                if(db.contactDao().getByUuid(uuid)==null) {
                    db.contactDao().insertImported(Contact(name=o.getString("name"),phone=o.optString("phone"),
                        email=o.optString("email"),address=o.optString("address"),notes=o.optString("notes"),
                        profileImageUri=o.optString("profileImageUri"),uuid=uuid,updatedAt=o.optLong("updatedAt"),
                        deleted=o.optBoolean("deleted"),dirty=o.optBoolean("dirty",true)))
                    added++
                }
            }
            val loans=root.getJSONArray("loans")
            for(i in 0 until loans.length()) { val o=loans.getJSONObject(i); val uuid=validUuid(o); val amount=o.getLong("amountMinorUnits")
                require(amount>=0 && o.getString("person").length<=300) { "Invalid loan record." }
                if(db.loanDao().getByUuid(uuid)==null) {
                    val personUuid=if(o.isNull("personUuid")) null else validUuid(o,"personUuid")
                    val personId=personUuid?.let(personIds::get)
                        ?: personUuid?.let { db.loanDao().getPersonByUuid(it)?.id }
                    db.loanDao().insertImported(Loan(person=o.getString("person"),amountMinorUnits=amount,
                        lent=o.getBoolean("lent"),note=o.optString("note"),epochDay=o.getLong("epochDay"),
                        settled=o.optBoolean("settled"),uuid=uuid,updatedAt=o.optLong("updatedAt"),
                        deleted=o.optBoolean("deleted"),dirty=o.optBoolean("dirty",true),
                        dueEpochDay=if(o.isNull("dueEpochDay")) null else o.optLong("dueEpochDay"),
                        title=o.optString("title"),status=o.optString("status","pending"),personId=personId))
                    added++
                }
            }
            val loanIds=root.getJSONArray("loans").let { rows ->
                buildMap {
                    for(i in 0 until rows.length()) {
                        val row=rows.getJSONObject(i); val uuid=validUuid(row)
                        db.loanDao().getByUuid(uuid)?.let { put(uuid,it.id) }
                    }
                }
            }
            val loanAttachments=root.getJSONArray("loanAttachments")
            for(i in 0 until loanAttachments.length()) {
                val o=loanAttachments.getJSONObject(i); val uuid=validUuid(o)
                if(db.loanDao().getAttachmentByUuid(uuid)!=null) continue
                val loanUuid=validUuid(o,"loanUuid")
                val loanId=loanIds[loanUuid] ?: error("Loan attachment refers to a missing loan.")
                db.loanDao().insertAttachment(LoanAttachment(
                    loanId=loanId,name=o.optString("name","Loan proof"),uri=o.getString("uri"),
                    mime=o.optString("mime"),addedAt=o.optLong("addedAt"),uuid=uuid,
                    updatedAt=o.optLong("updatedAt"),deleted=o.optBoolean("deleted"),
                    dirty=o.optBoolean("dirty",true)
                ))
                added++
            }
            val loanPayments=root.getJSONArray("loanPayments")
            for(i in 0 until loanPayments.length()) {
                val o=loanPayments.getJSONObject(i); val uuid=validUuid(o)
                if(db.loanDao().getPaymentByUuid(uuid)!=null) continue
                val loanUuid=validUuid(o,"loanUuid")
                val loanId=loanIds[loanUuid] ?: error("Loan payment refers to a missing loan.")
                val amount=o.getLong("amountMinorUnits")
                require(amount>=0) { "Invalid loan payment." }
                db.loanDao().insertPayment(LoanPayment(
                    loanId=loanId,amountMinorUnits=amount,epochDay=o.getLong("epochDay"),note=o.optString("note"),
                    uuid=uuid,updatedAt=o.optLong("updatedAt"),deleted=o.optBoolean("deleted"),
                    dirty=o.optBoolean("dirty",true),deviceId=o.optString("deviceId")
                ))
                added++
            }
            val files=root.getJSONArray("files")
            for(i in 0 until files.length()) { val o=files.getJSONObject(i); val uuid=validUuid(o)
                if(db.fileDao().getByUuid(uuid)==null) { db.fileDao().insertImported(FileItem(name=o.getString("name"),uri=o.getString("uri"),mime=o.getString("mime"),addedEpochDay=o.getLong("addedEpochDay"),uuid=uuid,updatedAt=o.optLong("updatedAt"),deleted=o.optBoolean("deleted"),dirty=o.optBoolean("dirty",true),
                    tags=o.optString("tags"),folder=o.optString("folder"),lastAccessedEpochDay=o.optLong("lastAccessedEpochDay"),missing=o.optBoolean("missing"))); added++ } }
            val todos=root.getJSONArray("todos")
            val pendingTodos=(0 until todos.length()).map { todos.getJSONObject(it) }.toMutableList()
            val todoIds=mutableMapOf<String,Int>()
            while(pendingTodos.isNotEmpty()) {
                var importedThisPass=0
                val iterator=pendingTodos.iterator()
                while(iterator.hasNext()) {
                    val o=iterator.next(); val uuid=validUuid(o)
                    val parentUuid=if(o.isNull("parentUuid")) null else validUuid(o,"parentUuid")
                    val parentId=parentUuid?.let(todoIds::get)
                        ?: parentUuid?.let { db.toDoDao().getByUuid(it)?.id }
                    if(parentUuid!=null && parentId==null) continue
                    val existing=db.toDoDao().getByUuid(uuid)
                    val id=existing?.id ?: run {
                        val hour=if(o.isNull("reminderHour")) null else o.getInt("reminderHour")
                        val minute=if(o.isNull("reminderMinute")) null else o.getInt("reminderMinute")
                        require((hour==null || hour in 0..23)&&(minute==null || minute in 0..59)) { "Invalid task reminder time." }
                        db.toDoDao().insertImported(ToDoItem(title=o.getString("title"),notes=o.optString("notes"),
                            dueEpochDay=o.getLong("dueEpochDay"),completed=o.optBoolean("completed"),
                            category=o.optString("category","Personal"),priority=o.optInt("priority",1),
                            reminderHour=hour,reminderMinute=minute,uuid=uuid,updatedAt=o.optLong("updatedAt"),
                            deleted=o.optBoolean("deleted"),dirty=o.optBoolean("dirty",true),
                            dueEpochMillis=if(o.isNull("dueEpochMillis")) null else o.optLong("dueEpochMillis"),
                            repeatRule=o.optString("repeatRule"),project=o.optString("project"),labels=o.optString("labels"),
                            parentId=parentId,sortOrder=o.optLong("sortOrder"))).toInt().takeIf { it>0 }
                            ?: db.toDoDao().getByUuid(uuid)?.id ?: error("Could not import task.")
                    }
                    if(existing==null) added++
                    todoIds[uuid]=id
                    iterator.remove()
                    importedThisPass++
                }
                require(importedThisPass>0) { "To-do backup contains a missing or circular parent reference." }
            }
            val lifeRules=root.optJSONArray("lifeRules")
            val ruleIds=mutableMapOf<String,Int>()
            if (lifeRules != null) for(i in 0 until lifeRules.length()) { val o=lifeRules.getJSONObject(i); val uuid=validUuid(o)
                require(o.getString("title").isNotBlank() && o.getString("title").length<=300) { "Invalid life rule title." }
                val existing=db.lifeRuleDao().getByUuid(uuid)
                val id=existing?.id ?: db.lifeRuleDao().upsertForSync(LifeRule(title=o.getString("title"),body=o.optString("body"),kind=o.optString("kind","Rule"),pinned=o.optBoolean("pinned"),createdEpochDay=o.optLong("createdEpochDay"),uuid=uuid,updatedAt=o.optLong("updatedAt"),deleted=o.optBoolean("deleted"),dirty=o.optBoolean("dirty",true))).toInt()
                if(existing==null) added++
                ruleIds[uuid]=id
            }
            val lifeRuleFiles=root.getJSONArray("lifeRuleFiles")
            for(i in 0 until lifeRuleFiles.length()) {
                val o=lifeRuleFiles.getJSONObject(i); val uuid=validUuid(o)
                if(db.lifeRuleDao().getFileByUuid(uuid)!=null) continue
                val ruleUuid=validUuid(o,"ruleUuid")
                val ruleId=ruleIds[ruleUuid] ?: db.lifeRuleDao().getByUuid(ruleUuid)?.id
                    ?: error("Life-rule file refers to a missing entry.")
                db.lifeRuleDao().upsertFileForSync(LifeRuleFile(
                    ruleId=ruleId,name=o.optString("name"),uri=o.getString("uri"),
                    mime=o.optString("mime"),folderPath=o.optString("folderPath"),addedAt=o.optLong("addedAt"),
                    uuid=uuid,updatedAt=o.optLong("updatedAt"),deleted=o.optBoolean("deleted"),
                    dirty=o.optBoolean("dirty",true)
                ))
                added++
            }
            val customNotifications=root.optJSONArray("customNotifications")
            if (customNotifications != null) for(i in 0 until customNotifications.length()) {
                val o=customNotifications.getJSONObject(i); val uuid=validUuid(o)
                val title=o.getString("title"); val hour=o.getInt("hour"); val minute=o.getInt("minute")
                val repeatMode=o.optString("repeatMode","once")
                require(title.isNotBlank() && title.length<=300) { "Invalid notification title." }
                require(hour in 0..23 && minute in 0..59) { "Invalid notification time." }
                require(repeatMode in setOf("once","daily","weekly","monthly")) { "Invalid notification repeat mode." }
                if(db.customNotificationDao().getByUuid(uuid)==null) {
                    val date=if(o.isNull("dateEpochDay")) null else o.getLong("dateEpochDay")
                    db.customNotificationDao().insert(CustomNotification(title=title,message=o.optString("message"),repeatMode=repeatMode,hour=hour,minute=minute,dateEpochDay=date,daysMask=o.optInt("daysMask",ALL_DAYS),dayOfMonth=o.optInt("dayOfMonth",1).coerceIn(1,31),enabled=o.optBoolean("enabled",true),lastDeliveredEpochMillis=o.optLong("lastDeliveredEpochMillis",0),uuid=uuid,updatedAt=o.optLong("updatedAt"),deleted=o.optBoolean("deleted"),dirty=o.optBoolean("dirty",true)))
                    added++
                }
            }
            fun wordKey(language: String, word: String) =
                "${language.trim().lowercase()}\u0000${word.trim().lowercase()}"

            fun noteKey(category: String, title: String) =
                "${category.trim().lowercase()}\u0000${title.trim().lowercase()}"

            fun translationKey(sourceLanguage: String, targetLanguage: String, sourceText: String) =
                "${sourceLanguage.trim().lowercase()}\u0000${targetLanguage.trim().lowercase()}\u0000$sourceText"

            val importedWordIds = mutableMapOf<Long, Long>()
            val existingWordIds: MutableMap<String, Long> = if (replace) mutableMapOf()
            else db.vocabularyDao().allForExport()
                .associateBy { wordKey(it.language, it.word) }
                .mapValues { (_, word) -> word.id }
                .toMutableMap()
            root.optJSONArray("vocabularyWords")?.let { words ->
                for (i in 0 until words.length()) {
                    val o = words.getJSONObject(i)
                    val word = o.getString("word")
                    require(word.isNotBlank() && word.length <= 300) { "Invalid vocabulary word." }
                    val language = o.optString("language", "en")
                    val key = wordKey(language, word)
                    val backupId = o.optLong("id", 0L)
                    val existingId = existingWordIds[key]
                        ?: backupId.takeIf { replace && it > 0 }
                    val row = VocabularyWord(
                        id = existingId ?: 0L,
                        word = word, meaning = o.optString("meaning"),
                        partOfSpeech = o.optString("partOfSpeech"), example = o.optString("example"),
                        language = language, tags = o.optString("tags"),
                        difficulty = o.optInt("difficulty", 1).coerceIn(1, 5),
                        mastered = o.optBoolean("mastered"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                    val id = db.vocabularyDao().upsert(row)
                    if (existingId == null) added++
                    existingWordIds[key] = id
                    importedWordIds[backupId] = id
                }
            }
            val existingNoteIds: MutableMap<String, Long> = if (replace) mutableMapOf()
            else db.grammarNoteDao().allForExport()
                .associateBy { noteKey(it.category, it.title) }
                .mapValues { (_, note) -> note.id }
                .toMutableMap()
            root.optJSONArray("grammarNotes")?.let { notes ->
                for (i in 0 until notes.length()) {
                    val o = notes.getJSONObject(i)
                    val title = o.getString("title")
                    require(title.isNotBlank() && title.length <= 300) { "Invalid grammar note." }
                    val category = o.optString("category", "Other")
                    val key = noteKey(category, title)
                    val backupId = o.optLong("id", 0L)
                    val existingId = existingNoteIds[key]
                        ?: backupId.takeIf { replace && it > 0 }
                    val savedId = db.grammarNoteDao().upsert(GrammarNote(
                        id = existingId ?: 0L,
                        title = title, category = category,
                        explanation = o.optString("explanation"), correctExample = o.optString("correctExample"),
                        incorrectExample = o.optString("incorrectExample"),
                        commonMistake = o.optBoolean("commonMistake"), pinned = o.optBoolean("pinned"),
                        tags = o.optString("tags"), updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    ))
                    if (existingId == null) added++
                    existingNoteIds[key] = savedId
                }
            }
            root.optJSONArray("reviewStates")?.let { states ->
                for (i in 0 until states.length()) {
                    val o = states.getJSONObject(i)
                    val backupWordId = o.getLong("wordId")
                    val wordId = importedWordIds[backupWordId]
                        ?: backupWordId.takeIf { db.vocabularyDao().get(it) != null }
                        ?: error("Review state refers to a missing word.")
                    val hadReview = db.reviewStateDao().get(wordId) != null
                    db.reviewStateDao().upsert(ReviewState(
                        wordId = wordId, box = o.optInt("box").coerceIn(0, 10),
                        nextReviewAt = o.optLong("nextReviewAt"), lastReviewedAt = o.optLong("lastReviewedAt"),
                        correctCount = o.optInt("correctCount").coerceAtLeast(0),
                        reviewCount = o.optInt("reviewCount").coerceAtLeast(0)
                    ))
                    if (!hadReview) added++
                }
            }
            val existingTranslationIds: MutableMap<String, Long> = if (replace) mutableMapOf()
            else db.translationHistoryDao().allForExport().associateBy {
                translationKey(it.sourceLanguage, it.targetLanguage, it.sourceText)
            }.mapValues { (_, item) -> item.id }.toMutableMap()
            root.optJSONArray("translationHistory")?.let { history ->
                for (i in 0 until history.length()) {
                    val o = history.getJSONObject(i)
                    val source = o.getString("sourceText")
                    require(source.length <= 4_000) { "Invalid translation record." }
                    val sourceLanguage = o.optString("sourceLanguage", "en")
                    val targetLanguage = o.optString("targetLanguage", "en")
                    val key = translationKey(sourceLanguage, targetLanguage, source)
                    val backupId = o.optLong("id", 0L)
                    val existingId = existingTranslationIds[key]
                        ?: backupId.takeIf { replace && it > 0 }
                    val savedId = db.translationHistoryDao().insert(TranslationHistory(
                        id = existingId ?: 0L,
                        sourceLanguage = sourceLanguage,
                        targetLanguage = targetLanguage,
                        sourceText = source, translatedText = o.optString("translatedText"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        favorite = o.optBoolean("favorite")
                    ))
                    if (existingId == null) added++
                    existingTranslationIds[key] = savedId
                }
            }
            MomentumSettingsBackup(context).importPreferences(
                root.optJSONObject("settings"),
                root.optJSONObject("budgets"),
                replace
            )
        }
        return "${if(replace) "Replaced with" else "Added"} $added records, including language study data. Vault entries are kept separately and were not imported."
    }

    private suspend fun lifeRulesJson(db: AppDatabase): JSONArray {
        val rules = JSONArray()
        db.lifeRuleDao().getAllForBackup().forEach { r ->
            rules.put(
                JSONObject()
                    .put("uuid", r.uuid).put("updatedAt", r.updatedAt)
                    .put("deleted", r.deleted).put("dirty", r.dirty)
                    .put("title", r.title).put("body", r.body).put("kind", r.kind)
                    .put("pinned", r.pinned).put("createdEpochDay", r.createdEpochDay)
            )
        }
        return rules
    }

    private fun validUuid(obj: JSONObject, key: String = "uuid"): String {
        val value=obj.optString(key); require(value.length in 1..64 && runCatching { UUID.fromString(value); true }.getOrDefault(false)) { "Missing or invalid UUID." }; return value
    }
    private fun readBytes(context: Context, source: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(source)
            ?: error("Could not open the selected backup file.")
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_IMPORT_BYTES) { "Backup exceeds the 10 MB safety limit." }
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
    }

    private fun decodeBytes(bytes: ByteArray, passphrase: CharArray?): ByteArray =
        if (PortableBackupCrypto.isEncrypted(bytes)) {
            val secret = passphrase?.takeIf { it.isNotEmpty() }
                ?: error("This backup is encrypted. Enter its backup password.")
            PortableBackupCrypto.decrypt(bytes, secret)
        } else {
            bytes
        }

    private fun parseRoot(bytes: ByteArray): JSONObject {
        val root=JSONObject(bytes.toString(Charsets.UTF_8)); require(root.optString("format")=="momentum-backup") { "This is not a Momentum backup." }
        if(root.optInt("formatVersion",-1)==1 || root.optInt("version",-1)==1) upgradeV1(root)
        when (root.optInt("formatVersion",-1)) {
            PREVIOUS_VERSION -> {
                require(root.optInt("schemaVersion",-1) in LEGACY_SCHEMA_VERSIONS) {
                    "This backup version is not supported."
                }
                upgradeV2(root)
                upgradeV3(root)
            }
            FORMAT_VERSION_3 -> {
                require(root.optInt("schemaVersion",-1)==SCHEMA_VERSION_20) {
                    "This backup version is not supported."
                }
                upgradeV3(root)
            }
            VERSION -> require(root.optInt("schemaVersion",-1)==CURRENT_SCHEMA) {
                "This backup version is not supported."
            }
            else -> error("This backup version is not supported.")
        }
        require(root.optLong("exportedAt",0)>0) { "Backup timestamp is missing." }
        BACKUP_TABLE_KEYS.forEach { key ->
            val rows=root.optJSONArray(key) ?: error("Backup is missing '$key' data.")
            require(rows.length()<=MAX_ROWS) { "Too many records in '$key'." }
        }
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
        root.put("formatVersion",PREVIOUS_VERSION).put("schemaVersion",13).put("exportedAt",exportedAt)
            .put("budgets",JSONObject()).put("settings",JSONObject())
    }

    private fun upgradeV2(root: JSONObject) {
        listOf(
            "habitSkips","categoryBudgets","contacts","loanPeople","loanAttachments",
            "loanPayments","lifeRuleFiles"
        ).forEach { key -> if (!root.has(key)) root.put(key,JSONArray()) }
        if (!root.has("lifeRules")) root.put("lifeRules",JSONArray())
        if (!root.has("customNotifications")) root.put("customNotifications",JSONArray())
        if (!root.has("budgets")) root.put("budgets",JSONObject())
        if (!root.has("settings")) root.put("settings",JSONObject())
        root.put("formatVersion",FORMAT_VERSION_3).put("schemaVersion",SCHEMA_VERSION_20)
    }

    /** v4 adds language study tables while preserving all earlier JSON payloads. */
    private fun upgradeV3(root: JSONObject) {
        listOf("vocabularyWords", "grammarNotes", "reviewStates", "translationHistory")
            .forEach { key -> if (!root.has(key)) root.put(key, JSONArray()) }
        root.put("formatVersion", VERSION).put("schemaVersion", CURRENT_SCHEMA)
    }
}
