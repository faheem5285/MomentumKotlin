@file:Suppress("LongParameterList", "TooManyFunctions")

package com.faheem.habits.core.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode
import java.io.File

const val ALL_DAYS = 127

@Entity(tableName = "habits", indices = [Index(value = ["uuid"], unique = true), Index("archived"), Index("createdEpochDay")])
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String = "",     // what to do
    val icon: String = "star",        // key in HabitIcons
    val colorIndex: Int = 0,          // index in HabitIcons colors
    val timeOfDay: Int = 0,           // 0 anytime, 1 morning, 2 afternoon, 3 evening
    val daysMask: Int = 127,          // bit0 = Mon ... bit6 = Sun
    val archived: Boolean = false,
    val createdEpochDay: Long = 0,
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true
    ,@ColumnInfo(defaultValue = "'specific_days'") val scheduleType: String = "specific_days"
    ,@ColumnInfo(defaultValue = "0") val frequencyPerWeek: Int = 0
    ,@ColumnInfo(defaultValue = "1") val everyNDays: Int = 1
    ,@ColumnInfo(defaultValue = "1") val targetQuantity: Int = 1
    ,@ColumnInfo(defaultValue = "''") val quantityUnit: String = ""
    ,@ColumnInfo(defaultValue = "0") val timerMinutes: Int = 0
    ,@ColumnInfo(defaultValue = "'General'") val category: String = "General"
    ,@ColumnInfo(defaultValue = "0") val streakFreezes: Int = 0
)

@Entity(
    tableName = "completions",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [ForeignKey(
        entity = Habit::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("habitId"), Index("epochDay"), Index(value = ["uuid"], unique = true)]
)
data class Completion(
    val habitId: Int,
    val epochDay: Long,
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true
    ,@ColumnInfo(defaultValue = "''") val note: String = ""
    ,@ColumnInfo(defaultValue = "1") val quantity: Int = 1
    ,@ColumnInfo(defaultValue = "0") val durationSeconds: Long = 0
    ,@ColumnInfo(defaultValue = "0") val completedAtEpochMillis: Long = 0
)

@Entity(
    tableName = "habit_skips",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [ForeignKey(entity = Habit::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("habitId"), Index("epochDay"), Index(value = ["uuid"], unique = true)]
)
data class HabitSkip(
    val habitId: Int,
    val epochDay: Long,
    val reason: String = "freeze",
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(defaultValue = "''") val deviceId: String = ""
)

@Entity(
    tableName = "habit_reminders",
    foreignKeys = [ForeignKey(
        entity = Habit::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("habitId"), Index("hour", "minute"), Index("enabled"), Index("dateEpochDay"), Index(value = ["uuid"], unique = true)]
)
data class HabitReminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val habitId: Int,
    val hour: Int,
    val minute: Int,
    val daysMask: Int = ALL_DAYS,
    val enabled: Boolean = true,
    val dateEpochDay: Long? = null,
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true
)

data class HabitReminderDraft(
    val hour: Int,
    val minute: Int,
    val daysMask: Int,
    val dateEpochDay: Long? = null
)

@Entity(tableName = "expenses", indices = [Index("epochDay"), Index("category"), Index(value = ["uuid"], unique = true)])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amountMinorUnits: Long,
    val category: String,
    val epochDay: Long,
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true
    ,@ColumnInfo(defaultValue = "'Cash'") val account: String = "Cash"
    ,@ColumnInfo(defaultValue = "''") val recurringRule: String = ""
    ,@ColumnInfo(defaultValue = "''") val categoryIcon: String = ""
    ,@ColumnInfo(defaultValue = "0") val categoryColor: Int = 0
)

@Entity(tableName = "loan_people", indices = [Index("archived"), Index(value = ["uuid"], unique = true)])
data class LoanPerson(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val profileImageUri: String = "",
    val notes: String = "",
    val archived: Boolean = false,
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true
)

@Entity(tableName = "contacts", indices = [Index("name"), Index(value = ["uuid"], unique = true)])
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    @ColumnInfo(defaultValue = "''") val phone: String = "",
    @ColumnInfo(defaultValue = "''") val email: String = "",
    @ColumnInfo(defaultValue = "''") val address: String = "",
    @ColumnInfo(defaultValue = "''") val notes: String = "",
    @ColumnInfo(defaultValue = "''") val profileImageUri: String = "",
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true
)

@Entity(
    tableName = "loans",
    indices = [Index("settled"), Index("epochDay"), Index("personId"), Index("status"), Index(value = ["uuid"], unique = true)],
    foreignKeys = [ForeignKey(entity = LoanPerson::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.SET_NULL)]
)
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val person: String,
    val amountMinorUnits: Long,
    val lent: Boolean,          // true = I lent money, false = I borrowed
    val note: String,
    val epochDay: Long,
    val settled: Boolean = false,
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true
    ,val dueEpochDay: Long? = null
    ,@ColumnInfo(defaultValue = "''") val title: String = ""
    ,@ColumnInfo(defaultValue = "'pending'") val status: String = "pending"
    ,val personId: Int? = null
)

@Entity(
    tableName = "loan_attachments",
    indices = [Index("loanId"), Index(value = ["uuid"], unique = true)],
    foreignKeys = [ForeignKey(entity = Loan::class, parentColumns = ["id"], childColumns = ["loanId"], onDelete = ForeignKey.CASCADE)]
)
data class LoanAttachment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val loanId: Int,
    val name: String,
    val uri: String,
    val mime: String = "",
    @ColumnInfo(defaultValue = "0") val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true
)

@Entity(
    tableName = "loan_payments",
    indices = [Index("loanId"), Index("epochDay"), Index(value = ["uuid"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = Loan::class,
            parentColumns = ["id"],
            childColumns = ["loanId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LoanPayment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val loanId: Int,
    val amountMinorUnits: Long,
    val epochDay: Long,
    val note: String = "",
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(defaultValue = "''") val deviceId: String = ""
)

@Entity(tableName = "library_files", indices = [Index("addedEpochDay"), Index(value = ["uuid"], unique = true)])
data class FileItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val uri: String,
    val mime: String,
    val addedEpochDay: Long,
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true
    ,@ColumnInfo(defaultValue = "''") val tags: String = ""
    ,@ColumnInfo(defaultValue = "''") val folder: String = ""
    ,@ColumnInfo(defaultValue = "0") val lastAccessedEpochDay: Long = 0
    ,@ColumnInfo(defaultValue = "0") val missing: Boolean = false
)

@Entity(
    tableName = "vault_entries",
    indices = [Index(value = ["uuid"], unique = true)]
)
data class VaultRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(defaultValue = "''") val deviceId: String = ""
)

@Entity(tableName = "todo_items", indices = [Index("dueEpochDay"), Index("completed"), Index("reminderHour", "reminderMinute"), Index(value = ["uuid"], unique = true)])
data class ToDoItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val notes: String = "",
    val dueEpochDay: Long,
    val completed: Boolean = false,
    @ColumnInfo(defaultValue = "'Personal'") val category: String = "Personal",
    @ColumnInfo(defaultValue = "1") val priority: Int = 1,
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true
    ,val dueEpochMillis: Long? = null
    ,@ColumnInfo(defaultValue = "''") val repeatRule: String = ""
    ,@ColumnInfo(defaultValue = "''") val project: String = ""
    ,@ColumnInfo(defaultValue = "''") val labels: String = ""
    ,val parentId: Int? = null
    ,@ColumnInfo(defaultValue = "0") val sortOrder: Long = 0
)

/** A user-written life rule, best habit or quote. */
@Entity(
    tableName = "life_rules",
    indices = [Index("kind"), Index("pinned"), Index(value = ["uuid"], unique = true)]
)
data class LifeRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val body: String = "",
    val kind: String = "Rule",        // Rule, Habit or Quote
    val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val createdEpochDay: Long = 0,
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true
)

@Entity(
    tableName = "life_rule_files",
    indices = [Index("ruleId"), Index(value = ["uuid"], unique = true)],
    foreignKeys = [ForeignKey(entity = LifeRule::class, parentColumns = ["id"], childColumns = ["ruleId"], onDelete = ForeignKey.CASCADE)]
)
data class LifeRuleFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ruleId: Int,
    val name: String,
    val uri: String,
    val mime: String = "",
    val folderPath: String = "",
    @ColumnInfo(defaultValue = "0") val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true
)

/**
 * A notification written by the user and scheduled at a time they choose.
 *
 * Repeat modes are stored as plain strings so the local database stays easy to
 * inspect and older backups remain readable.
 */
@Entity(
    tableName = "custom_notifications",
    indices = [Index("enabled"), Index("repeatMode"), Index(value = ["uuid"], unique = true)]
)
data class CustomNotification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val message: String = "",
    val repeatMode: String = "once",   // once, daily, weekly or monthly
    val hour: Int = 9,
    val minute: Int = 0,
    val dateEpochDay: Long? = null,    // used by one-off notifications
    val daysMask: Int = ALL_DAYS,      // bit0 = Mon ... bit6 = Sun, used by weekly
    val dayOfMonth: Int = 1,           // used by monthly
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val lastDeliveredEpochMillis: Long = 0,
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true
)

@Entity(
    tableName = "category_budgets",
    primaryKeys = ["category", "monthKey"],
    indices = [Index("monthKey"), Index(value = ["uuid"], unique = true)]
)
data class CategoryBudget(
    val category: String,
    val monthKey: String,
    val limitMinorUnits: Long,
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(defaultValue = "''") val deviceId: String = ""
)

@Entity(tableName = "sync_state")
data class SyncState(
    @PrimaryKey val tableName: String,
    val cursor: String = "",
    val pendingCount: Int = 0,
    val conflictCount: Int = 0,
    val lastSyncEpochMillis: Long = 0,
    val lastError: String? = null,
    val paused: Boolean = false
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY id")
    suspend fun getAllForSync(): List<Habit>
    @Query("SELECT * FROM habit_skips")
    suspend fun getAllSkipsForSync(): List<HabitSkip>
    @Query("SELECT * FROM habit_skips WHERE uuid = :uuid LIMIT 1")
    suspend fun getSkipByUuid(uuid: String): HabitSkip?
    @Query("SELECT * FROM habit_reminders")
    suspend fun getAllRemindersForSync(): List<HabitReminder>
    @Query("UPDATE habit_skips SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markSkipsClean(uuids: List<String>)
    @Query("UPDATE habit_reminders SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markRemindersClean(uuids: List<String>)
    @Query("UPDATE habits SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markHabitsClean(uuids: List<String>)
    @Query("UPDATE completions SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markCompletionsClean(uuids: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabit(habit: Habit)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(completion: Completion)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkipForSync(skip: HabitSkip)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminderForSync(reminder: HabitReminder)
    @Query("SELECT * FROM habits WHERE deleted = 0 ORDER BY id")
    fun getHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits ORDER BY id")
    suspend fun getAllForBackup(): List<Habit>

    @Query("SELECT * FROM completions WHERE deleted = 0")
    fun getCompletions(): Flow<List<Completion>>

    @Query("SELECT * FROM habit_skips WHERE deleted = 0")
    fun getSkips(): Flow<List<HabitSkip>>

    @Query("SELECT * FROM completions")
    suspend fun getCompletionsForBackup(): List<Completion>

    @Query("SELECT * FROM habit_reminders WHERE deleted = 0 ORDER BY hour, minute, id")
    fun getReminders(): Flow<List<HabitReminder>>

    @Query("SELECT * FROM habit_reminders ORDER BY id")
    suspend fun getRemindersForBackup(): List<HabitReminder>

    @Query("SELECT habit_reminders.* FROM habit_reminders INNER JOIN habits ON habits.id = habit_reminders.habitId WHERE habit_reminders.deleted = 0 AND habit_reminders.enabled = 1 AND (habit_reminders.daysMask != 0 OR habit_reminders.dateEpochDay IS NOT NULL) AND habits.archived = 0 AND habits.deleted = 0")
    suspend fun getRemindersForScheduling(): List<HabitReminder>

    @Query("SELECT * FROM habit_reminders WHERE habitId = :habitId AND deleted = 0 ORDER BY hour, minute, id")
    suspend fun getRemindersForHabit(habitId: Int): List<HabitReminder>

    @Query("SELECT * FROM habit_reminders WHERE id = :id LIMIT 1")
    suspend fun getReminder(id: Int): HabitReminder?

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun getHabit(id: Int): Habit?
    @Query("SELECT * FROM habits WHERE uuid = :uuid LIMIT 1") suspend fun getHabitByUuid(uuid: String): Habit?
    @Query("DELETE FROM habits") suspend fun deleteAllHabits()
    @Query("DELETE FROM habit_reminders") suspend fun deleteAllReminders()
    @Query("DELETE FROM completions") suspend fun deleteAllCompletions()

    @Insert suspend fun insertReminder(reminder: HabitReminder): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertImportedReminder(reminder: HabitReminder): Long
    @Query("SELECT * FROM habit_reminders WHERE uuid = :uuid LIMIT 1") suspend fun getReminderByUuid(uuid: String): HabitReminder?
    @Update suspend fun updateReminder(reminder: HabitReminder)
    @Query("UPDATE habit_reminders SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun deleteReminder(id: Int, updatedAt: Long = System.currentTimeMillis())

    @Insert suspend fun insertHabit(habit: Habit): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertImportedHabit(habit: Habit): Long
    @Update suspend fun updateHabit(habit: Habit)
    @Query("UPDATE habits SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun deleteHabit(id: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE completions SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE habitId = :habitId AND deleted = 0")
    suspend fun deleteCompletionsForHabit(habitId: Int, updatedAt: Long)

    @Query("UPDATE habit_skips SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE habitId = :habitId AND deleted = 0")
    suspend fun deleteSkipsForHabit(habitId: Int, updatedAt: Long)

    @Query("UPDATE habit_reminders SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE habitId = :habitId AND deleted = 0")
    suspend fun deleteRemindersForHabit(habitId: Int, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(completion: Completion)
    @Query("SELECT * FROM completions WHERE habitId = :habitId AND epochDay = :epochDay LIMIT 1")
    suspend fun getCompletion(habitId: Int, epochDay: Long): Completion?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertImportedCompletion(completion: Completion): Long
    @Query("SELECT * FROM completions WHERE uuid = :uuid LIMIT 1") suspend fun getCompletionByUuid(uuid: String): Completion?

    @Query("UPDATE completions SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE habitId = :habitId AND epochDay = :epochDay AND deleted = 0")
    suspend fun deleteCompletion(habitId: Int, epochDay: Long, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkip(skip: HabitSkip)

    @Query("SELECT * FROM habit_skips WHERE habitId = :habitId AND epochDay = :epochDay LIMIT 1")
    suspend fun getSkip(habitId: Int, epochDay: Long): HabitSkip?

    @Query("UPDATE habit_skips SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE habitId = :habitId AND epochDay = :epochDay AND deleted = 0")
    suspend fun deleteSkip(habitId: Int, epochDay: Long, updatedAt: Long = System.currentTimeMillis())
}

@Dao
@Suppress("TooManyFunctions")
interface ExpenseDao {
    @Query("SELECT * FROM expenses")
    suspend fun getAllForSync(): List<Expense>
    @Query("SELECT * FROM category_budgets")
    suspend fun getAllBudgetsForSync(): List<CategoryBudget>
    @Query("SELECT * FROM category_budgets WHERE uuid = :uuid LIMIT 1")
    suspend fun getBudgetByUuid(uuid: String): CategoryBudget?
    @Query("SELECT * FROM category_budgets WHERE category = :category AND monthKey = :monthKey LIMIT 1")
    suspend fun getBudget(category: String, monthKey: String): CategoryBudget?
    @Query("DELETE FROM category_budgets")
    suspend fun deleteAllBudgets()
    @Query("UPDATE expenses SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markClean(uuids: List<String>)
    @Query("UPDATE category_budgets SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markBudgetsClean(uuids: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(expense: Expense)
    @Query("SELECT * FROM expenses WHERE deleted = 0 ORDER BY epochDay DESC, id DESC")
    fun getAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY id")
    suspend fun getAllForBackup(): List<Expense>

    @Insert suspend fun insert(expense: Expense)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertImported(expense: Expense): Long
    @Query("SELECT * FROM expenses WHERE uuid = :uuid LIMIT 1") suspend fun getByUuid(uuid: String): Expense?
    @Query("DELETE FROM expenses") suspend fun deleteAll()
    @Query("UPDATE expenses SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun delete(id: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM category_budgets ORDER BY monthKey DESC, category")
    fun getBudgets(): Flow<List<CategoryBudget>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: CategoryBudget)
}

@Dao
@Suppress("TooManyFunctions")
interface LoanDao {
    @Query("SELECT * FROM loan_people WHERE archived = 0 AND deleted = 0 ORDER BY name COLLATE NOCASE")
    fun getPeople(): Flow<List<LoanPerson>>
    @Insert suspend fun insertPerson(person: LoanPerson): Long
    @Update suspend fun updatePerson(person: LoanPerson)
    @Query("UPDATE loan_people SET archived = 1, updatedAt = :updatedAt, dirty = 1 WHERE id = :id")
    suspend fun archivePerson(id: Int, updatedAt: Long = System.currentTimeMillis())
    @Query("SELECT * FROM loan_people WHERE id = :id LIMIT 1")
    suspend fun getPerson(id: Int): LoanPerson?
    @Query("SELECT * FROM loan_people ORDER BY id")
    suspend fun getAllPeopleForSync(): List<LoanPerson>
    @Query("SELECT * FROM loan_people WHERE uuid = :uuid LIMIT 1")
    suspend fun getPersonByUuid(uuid: String): LoanPerson?
    @Query("UPDATE loan_people SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markPeopleClean(uuids: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersonForSync(person: LoanPerson)
    @Query("SELECT * FROM loan_attachments ORDER BY id")
    suspend fun getAllAttachmentsForSync(): List<LoanAttachment>
    @Query("SELECT * FROM loan_attachments WHERE uuid = :uuid LIMIT 1")
    suspend fun getAttachmentByUuid(uuid: String): LoanAttachment?
    @Query("UPDATE loan_attachments SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markAttachmentsClean(uuids: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttachmentForSync(attachment: LoanAttachment)
    @Update suspend fun updateAttachment(attachment: LoanAttachment)
    @Query("UPDATE loan_people SET archived = 1, deleted = 1, updatedAt = :updatedAt, dirty = 1 WHERE id = :id")
    suspend fun deletePerson(id: Int, updatedAt: Long = System.currentTimeMillis())
    @Query("DELETE FROM loan_people")
    suspend fun deleteAllPeople()
    @Query("SELECT * FROM loan_attachments WHERE loanId = :loanId AND deleted = 0 ORDER BY addedAt DESC")
    fun getAttachments(loanId: Int): Flow<List<LoanAttachment>>
    @Insert suspend fun insertAttachment(attachment: LoanAttachment): Long
    @Query("UPDATE loan_attachments SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun deleteAttachment(id: Int, updatedAt: Long = System.currentTimeMillis())
    @Query("UPDATE loan_attachments SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE loanId = :loanId AND deleted = 0")
    suspend fun deleteAttachmentsForLoan(loanId: Int, updatedAt: Long)
    @Query("UPDATE loan_payments SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE loanId = :loanId AND deleted = 0")
    suspend fun deletePaymentsForLoan(loanId: Int, updatedAt: Long)
    @Query("SELECT * FROM loans")
    suspend fun getAllForSync(): List<Loan>
    @Query("SELECT * FROM loan_payments")
    suspend fun getAllPaymentsForSync(): List<LoanPayment>
    @Query("SELECT * FROM loan_payments WHERE uuid = :uuid LIMIT 1")
    suspend fun getPaymentByUuid(uuid: String): LoanPayment?
    @Query("UPDATE loans SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markClean(uuids: List<String>)
    @Query("UPDATE loan_payments SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markPaymentsClean(uuids: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLoan(loan: Loan)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayment(payment: LoanPayment)
    @Query("SELECT * FROM loans WHERE deleted = 0 ORDER BY settled ASC, epochDay DESC, id DESC")
    fun getAll(): Flow<List<Loan>>

    @Query("SELECT * FROM loans ORDER BY id")
    suspend fun getAllForBackup(): List<Loan>

    @Insert suspend fun insert(loan: Loan): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertImported(loan: Loan): Long
    @Query("SELECT * FROM loans WHERE uuid = :uuid LIMIT 1") suspend fun getByUuid(uuid: String): Loan?
    @Query("DELETE FROM loans") suspend fun deleteAll()
    @Update suspend fun update(loan: Loan)
    @Query("UPDATE loans SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun delete(id: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM loan_payments WHERE loanId = :loanId AND deleted = 0 ORDER BY epochDay DESC, id DESC")
    fun getPayments(loanId: Int): Flow<List<LoanPayment>>

    @Insert suspend fun insertPayment(payment: LoanPayment)

    @Query("SELECT COALESCE(SUM(amountMinorUnits), 0) FROM loan_payments WHERE loanId = :loanId AND deleted = 0")
    suspend fun getPaidAmount(loanId: Int): Long
}

@Dao
@Suppress("TooManyFunctions")
interface FileDao {
    @Query("SELECT * FROM library_files")
    suspend fun getAllForSync(): List<FileItem>
    @Query("UPDATE library_files SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markClean(uuids: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertForSync(file: FileItem)
    @Query("SELECT * FROM library_files WHERE deleted = 0 ORDER BY id DESC")
    fun getAll(): Flow<List<FileItem>>

    @Query("SELECT * FROM library_files ORDER BY id")
    suspend fun getAllForBackup(): List<FileItem>
    @Insert suspend fun insert(file: FileItem)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertImported(file: FileItem): Long
    @Query("SELECT * FROM library_files WHERE uuid = :uuid LIMIT 1") suspend fun getByUuid(uuid: String): FileItem?
    @Update suspend fun update(file: FileItem)
    @Query("DELETE FROM library_files") suspend fun deleteAll()
    @Query("UPDATE library_files SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun delete(id: Int, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_entries WHERE deleted = 0 ORDER BY updatedAt DESC")
    suspend fun getAll(): List<VaultRecord>

    @Query("SELECT * FROM vault_entries ORDER BY updatedAt DESC")
    suspend fun getAllForSync(): List<VaultRecord>

    @Query("SELECT updatedAt FROM vault_entries WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun observeUpdatedAt(): Flow<List<Long>>

    @Query("SELECT * FROM vault_entries WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): VaultRecord?

    @Query("UPDATE vault_entries SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markClean(uuids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: VaultRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<VaultRecord>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImported(record: VaultRecord): Long

    @Update
    suspend fun update(record: VaultRecord)

    @Query("DELETE FROM vault_entries")
    suspend fun deleteAll()

    @Query("SELECT * FROM vault_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): VaultRecord?

    @Query("UPDATE vault_entries SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markDeleted(id: Int, updatedAt: Long)

    @Query("UPDATE vault_entries SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE deleted = 0")
    suspend fun markAllDeleted(updatedAt: Long)
}

@Dao
@Suppress("TooManyFunctions")
interface ToDoDao {
    @Query("SELECT * FROM todo_items")
    suspend fun getAllForSync(): List<ToDoItem>
    @Query("UPDATE todo_items SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markClean(uuids: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertForSync(item: ToDoItem)
    @Query("SELECT * FROM todo_items WHERE deleted = 0 ORDER BY completed ASC, dueEpochDay ASC, id DESC")
    fun getAll(): Flow<List<ToDoItem>>

    @Query("SELECT * FROM todo_items ORDER BY id")
    suspend fun getAllForBackup(): List<ToDoItem>

    @Query("SELECT * FROM todo_items WHERE deleted = 0 AND completed = 0 AND reminderHour IS NOT NULL AND reminderMinute IS NOT NULL")
    suspend fun getItemsForScheduling(): List<ToDoItem>

    @Query("SELECT * FROM todo_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): ToDoItem?

    @Insert suspend fun insert(item: ToDoItem): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertImported(item: ToDoItem): Long
    @Query("SELECT * FROM todo_items WHERE uuid = :uuid LIMIT 1") suspend fun getByUuid(uuid: String): ToDoItem?
    @Query("DELETE FROM todo_items") suspend fun deleteAll()
    @Update suspend fun update(item: ToDoItem)
    @Query("UPDATE todo_items SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun delete(id: Int, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface LifeRuleDao {
    @Query("SELECT * FROM life_rules ORDER BY id")
    suspend fun getAllForSync(): List<LifeRule>
    @Query("SELECT * FROM life_rules WHERE deleted = 0 ORDER BY pinned DESC, kind ASC, id DESC")
    fun getAll(): Flow<List<LifeRule>>

    @Query("SELECT * FROM life_rules ORDER BY id")
    suspend fun getAllForBackup(): List<LifeRule>

    @Query("SELECT * FROM life_rules WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): LifeRule?

    @Insert suspend fun insert(rule: LifeRule): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertForSync(rule: LifeRule): Long
    @Query("UPDATE life_rules SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markClean(uuids: List<String>)
    @Update suspend fun update(rule: LifeRule)
    @Query("UPDATE life_rules SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun delete(id: Int, updatedAt: Long = System.currentTimeMillis())
    @Query("SELECT * FROM life_rule_files WHERE ruleId = :ruleId AND deleted = 0 ORDER BY addedAt DESC")
    fun getFiles(ruleId: Int): Flow<List<LifeRuleFile>>
    @Insert suspend fun insertFile(file: LifeRuleFile): Long
    @Query("UPDATE life_rule_files SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markFileDeleted(id: Int, updatedAt: Long)
    @Query("SELECT * FROM life_rule_files ORDER BY id")
    suspend fun getAllFilesForSync(): List<LifeRuleFile>
    @Query("SELECT * FROM life_rule_files WHERE uuid = :uuid LIMIT 1")
    suspend fun getFileByUuid(uuid: String): LifeRuleFile?
    @Query("UPDATE life_rule_files SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markFilesClean(uuids: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFileForSync(file: LifeRuleFile)
    @Update suspend fun updateFile(file: LifeRuleFile)
    @Query("UPDATE life_rule_files SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun deleteFile(id: Int, updatedAt: Long = System.currentTimeMillis())
    @Query("DELETE FROM life_rules") suspend fun deleteAll()
}

@Dao
interface CustomNotificationDao {
    @Query("SELECT * FROM custom_notifications ORDER BY id")
    suspend fun getAllForSync(): List<CustomNotification>
    @Query("UPDATE custom_notifications SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markClean(uuids: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertForSync(notification: CustomNotification)
    @Query("SELECT * FROM custom_notifications WHERE deleted = 0 ORDER BY enabled DESC, hour ASC, minute ASC, id DESC")
    fun getAll(): Flow<List<CustomNotification>>

    @Query("SELECT * FROM custom_notifications ORDER BY id")
    suspend fun getAllForBackup(): List<CustomNotification>

    @Query("SELECT * FROM custom_notifications WHERE deleted = 0 AND enabled = 1 ORDER BY hour ASC, minute ASC")
    suspend fun getForScheduling(): List<CustomNotification>

    @Query("SELECT * FROM custom_notifications WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): CustomNotification?

    @Query("SELECT * FROM custom_notifications WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): CustomNotification?

    @Insert suspend fun insert(notification: CustomNotification): Long
    @Update suspend fun update(notification: CustomNotification)
    @Query("UPDATE custom_notifications SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun delete(id: Int, updatedAt: Long = System.currentTimeMillis())
    @Query("DELETE FROM custom_notifications") suspend fun deleteAll()
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun getAll(): Flow<List<Contact>>
    @Query("SELECT * FROM contacts ORDER BY id")
    suspend fun getAllForBackup(): List<Contact>
    @Query("SELECT * FROM contacts ORDER BY id")
    suspend fun getAllForSync(): List<Contact>
    @Query("SELECT * FROM contacts WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): Contact?
    @Query("UPDATE contacts SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markClean(uuids: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertForSync(contact: Contact)
    @Insert
    suspend fun insert(contact: Contact): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImported(contact: Contact): Long
    @Update
    suspend fun update(contact: Contact)
    @Query("DELETE FROM contacts")
    suspend fun deleteAll()
    @Query("UPDATE contacts SET deleted = 1, updatedAt = :updatedAt, dirty = 1 WHERE id = :id")
    suspend fun softDelete(id: Int, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state ORDER BY tableName")
    fun observeAll(): Flow<List<SyncState>>

    @Query("SELECT * FROM sync_state WHERE tableName = :tableName LIMIT 1")
    suspend fun get(tableName: String): SyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncState)
}

@Database(
    entities = [Habit::class, Completion::class, HabitSkip::class, HabitReminder::class, Expense::class, CategoryBudget::class, LoanPerson::class, Contact::class, Loan::class, LoanPayment::class, LoanAttachment::class, FileItem::class, VaultRecord::class, ToDoItem::class, LifeRule::class, LifeRuleFile::class, CustomNotification::class, SyncState::class],
    version = 20,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun loanDao(): LoanDao
    abstract fun contactDao(): ContactDao
    abstract fun fileDao(): FileDao
    abstract fun vaultDao(): VaultDao
    abstract fun toDoDao(): ToDoDao
    abstract fun lifeRuleDao(): LifeRuleDao
    abstract fun customNotificationDao(): CustomNotificationDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        private const val DATABASE_NAME = "momentum.db"
        private const val ACCOUNT_SCOPE_PREFS = "momentum_account_database_scope"
        private const val ACTIVE_USER_KEY = "active_user_id"

        /** Keeps Room rows and sync cursors in a separate local database per Supabase user. */
        fun activateAccount(context: Context, userId: String) = synchronized(this) {
            val normalizedId = runCatching { UUID.fromString(userId).toString() }
                .getOrElse { error("Supabase returned an invalid account ID.") }
            val appContext = context.applicationContext
            val scopePrefs = appContext.getSharedPreferences(ACCOUNT_SCOPE_PREFS, Context.MODE_PRIVATE)
            val activeUser = scopePrefs.getString(ACTIVE_USER_KEY, null)
            if (activeUser == normalizedId) return@synchronized

            val db = instance
            if (db != null) {
                runCatching {
                    db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
                }
                db.close()
                instance = null
            }

            val baseFile = appContext.getDatabasePath(DATABASE_NAME)
            val accountDirectory = File(appContext.filesDir, "account-databases").apply {
                check(isDirectory || mkdirs()) { "Could not prepare account-local data storage." }
            }
            if (baseFile.exists()) {
                val previousFile = activeUser?.let { accountDatabaseFile(accountDirectory, it) }
                    ?: File(accountDirectory, "unassigned-legacy-database.db")
                baseFile.copyTo(previousFile, overwrite = true)
            }
            deleteDatabaseFiles(baseFile)

            val accountFile = accountDatabaseFile(accountDirectory, normalizedId)
            if (accountFile.exists()) {
                accountFile.copyTo(baseFile, overwrite = true)
            }
            check(scopePrefs.edit().putString(ACTIVE_USER_KEY, normalizedId).commit()) {
                "Could not save the active account for local data."
            }
        }

        private fun accountDatabaseFile(directory: File, userId: String): File =
            File(directory, "account-${userId.replace("-", "")}.db")

        private fun deleteDatabaseFiles(database: File) {
            listOf(database, File("${database.path}-wal"), File("${database.path}-shm"), File("${database.path}-journal"))
                .forEach { file ->
                    if (file.exists()) check(file.delete()) { "Could not switch the local account database." }
                }
        }

        // The last committed app schema was v3. Keep its data while adding the
        // habit fields and loan settlement state introduced in v4.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `icon` TEXT NOT NULL DEFAULT 'star'")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `colorIndex` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `timeOfDay` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `daysMask` INTEGER NOT NULL DEFAULT 127")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `createdEpochDay` INTEGER NOT NULL DEFAULT 0")
                val loanColumns = db.query("PRAGMA table_info(`loans`)").use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
                }
                if ("settled" !in loanColumns) {
                    db.execSQL("ALTER TABLE `loans` ADD COLUMN `settled` INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `vault_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nonce` BLOB NOT NULL, `ciphertext` BLOB NOT NULL, `updatedAt` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `habit_reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitId` INTEGER NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `daysMask` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_reminders_habitId` ON `habit_reminders` (`habitId`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `habit_reminders` ADD COLUMN `dateEpochDay` INTEGER")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `todo_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `notes` TEXT NOT NULL, `dueEpochDay` INTEGER NOT NULL, `completed` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `todo_items` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'Personal'")
                db.execSQL("ALTER TABLE `todo_items` ADD COLUMN `priority` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `todo_items` ADD COLUMN `reminderHour` INTEGER")
                db.execSQL("ALTER TABLE `todo_items` ADD COLUMN `reminderMinute` INTEGER")
            }
        }

        // Room's identity hash includes entity class names. The architecture
        // package move changed those names without changing any table columns,
        // so advance the version and let Room validate the existing schema.
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                val syncTables = listOf("habits", "completions", "habit_reminders", "library_files", "todo_items")
                syncTables.forEach { table ->
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1")
                }

                // Rebuild legacy REAL amount tables through BigDecimal so values
                // like 1.005 are rounded from their canonical decimal text.
                db.execSQL("CREATE TABLE `_new_expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `amountMinorUnits` INTEGER NOT NULL, `category` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `uuid` TEXT NOT NULL DEFAULT '', `updatedAt` INTEGER NOT NULL DEFAULT 0, `deleted` INTEGER NOT NULL DEFAULT 0, `dirty` INTEGER NOT NULL DEFAULT 1)")
                db.query("SELECT id,title,amount,category,epochDay FROM expenses").use { cursor ->
                    while (cursor.moveToNext()) db.execSQL(
                        "INSERT INTO `_new_expenses` (`id`,`title`,`amountMinorUnits`,`category`,`epochDay`) VALUES (?,?,?,?,?)",
                        arrayOf(cursor.getLong(0), cursor.getString(1), cursor.getDouble(2).toMinorUnitsForMigration(), cursor.getString(3), cursor.getLong(4))
                    )
                }
                db.execSQL("DROP TABLE `expenses`")
                db.execSQL("ALTER TABLE `_new_expenses` RENAME TO `expenses`")
                db.execSQL("CREATE TABLE `_new_loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `person` TEXT NOT NULL, `amountMinorUnits` INTEGER NOT NULL, `lent` INTEGER NOT NULL, `note` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `settled` INTEGER NOT NULL DEFAULT 0, `uuid` TEXT NOT NULL DEFAULT '', `updatedAt` INTEGER NOT NULL DEFAULT 0, `deleted` INTEGER NOT NULL DEFAULT 0, `dirty` INTEGER NOT NULL DEFAULT 1)")
                db.query("SELECT id,person,amount,lent,note,epochDay,settled FROM loans").use { cursor ->
                    while (cursor.moveToNext()) db.execSQL(
                        "INSERT INTO `_new_loans` (`id`,`person`,`amountMinorUnits`,`lent`,`note`,`epochDay`,`settled`) VALUES (?,?,?,?,?,?,?)",
                        arrayOf(cursor.getLong(0), cursor.getString(1), cursor.getDouble(2).toMinorUnitsForMigration(), cursor.getInt(3), cursor.getString(4), cursor.getLong(5), cursor.getInt(6))
                    )
                }
                db.execSQL("DROP TABLE `loans`")
                db.execSQL("ALTER TABLE `_new_loans` RENAME TO `loans`")

                val allTables = syncTables + listOf("expenses", "loans")
                allTables.forEach { table ->
                    val keyColumn = when (table) { "completions" -> "habitId, epochDay"; else -> "id" }
                    db.query("SELECT $keyColumn FROM `$table`").use { cursor ->
                        while (cursor.moveToNext()) {
                            val where = if (table == "completions") "habitId = ? AND epochDay = ?" else "id = ?"
                            val args = if (table == "completions") arrayOf(cursor.getInt(0), cursor.getLong(1)) else arrayOf(cursor.getLong(0))
                            db.execSQL("UPDATE `$table` SET uuid = ?, updatedAt = ?, deleted = 0, dirty = 1 WHERE $where", arrayOf(UUID.randomUUID().toString(), now, *args))
                        }
                    }
                }

                val indexes = listOf(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_habits_uuid` ON `habits` (`uuid`)",
                    "CREATE INDEX IF NOT EXISTS `index_habits_archived` ON `habits` (`archived`)",
                    "CREATE INDEX IF NOT EXISTS `index_habits_createdEpochDay` ON `habits` (`createdEpochDay`)",
                    "CREATE INDEX IF NOT EXISTS `index_completions_epochDay` ON `completions` (`epochDay`)",
                    "CREATE INDEX IF NOT EXISTS `index_completions_habitId` ON `completions` (`habitId`)",
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_completions_uuid` ON `completions` (`uuid`)",
                    "CREATE INDEX IF NOT EXISTS `index_habit_reminders_hour_minute` ON `habit_reminders` (`hour`, `minute`)",
                    "CREATE INDEX IF NOT EXISTS `index_habit_reminders_enabled` ON `habit_reminders` (`enabled`)",
                    "CREATE INDEX IF NOT EXISTS `index_habit_reminders_dateEpochDay` ON `habit_reminders` (`dateEpochDay`)",
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_reminders_uuid` ON `habit_reminders` (`uuid`)",
                    "CREATE INDEX IF NOT EXISTS `index_expenses_epochDay` ON `expenses` (`epochDay`)",
                    "CREATE INDEX IF NOT EXISTS `index_expenses_category` ON `expenses` (`category`)",
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_expenses_uuid` ON `expenses` (`uuid`)",
                    "CREATE INDEX IF NOT EXISTS `index_loans_settled` ON `loans` (`settled`)",
                    "CREATE INDEX IF NOT EXISTS `index_loans_epochDay` ON `loans` (`epochDay`)",
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_loans_uuid` ON `loans` (`uuid`)",
                    "CREATE INDEX IF NOT EXISTS `index_library_files_addedEpochDay` ON `library_files` (`addedEpochDay`)",
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_library_files_uuid` ON `library_files` (`uuid`)",
                    "CREATE INDEX IF NOT EXISTS `index_todo_items_dueEpochDay` ON `todo_items` (`dueEpochDay`)",
                    "CREATE INDEX IF NOT EXISTS `index_todo_items_completed` ON `todo_items` (`completed`)",
                    "CREATE INDEX IF NOT EXISTS `index_todo_items_reminderHour_reminderMinute` ON `todo_items` (`reminderHour`, `reminderMinute`)",
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_todo_items_uuid` ON `todo_items` (`uuid`)"
                )
                indexes.forEach(db::execSQL)
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = mapOf(
                    "habits" to listOf(
                        "scheduleType TEXT NOT NULL DEFAULT 'specific_days'",
                        "frequencyPerWeek INTEGER NOT NULL DEFAULT 0",
                        "everyNDays INTEGER NOT NULL DEFAULT 1",
                        "targetQuantity INTEGER NOT NULL DEFAULT 1",
                        "quantityUnit TEXT NOT NULL DEFAULT ''",
                        "timerMinutes INTEGER NOT NULL DEFAULT 0",
                        "category TEXT NOT NULL DEFAULT 'General'",
                        "streakFreezes INTEGER NOT NULL DEFAULT 0"
                    ),
                    "completions" to listOf(
                        "note TEXT NOT NULL DEFAULT ''",
                        "quantity INTEGER NOT NULL DEFAULT 1",
                        "durationSeconds INTEGER NOT NULL DEFAULT 0",
                        "completedAtEpochMillis INTEGER NOT NULL DEFAULT 0"
                    ),
                    "expenses" to listOf(
                        "account TEXT NOT NULL DEFAULT 'Cash'",
                        "recurringRule TEXT NOT NULL DEFAULT ''",
                        "categoryIcon TEXT NOT NULL DEFAULT ''",
                        "categoryColor INTEGER NOT NULL DEFAULT 0"
                    ),
                    "loans" to listOf("dueEpochDay INTEGER"),
                    "library_files" to listOf(
                        "tags TEXT NOT NULL DEFAULT ''",
                        "folder TEXT NOT NULL DEFAULT ''",
                        "lastAccessedEpochDay INTEGER NOT NULL DEFAULT 0",
                        "missing INTEGER NOT NULL DEFAULT 0"
                    ),
                    "todo_items" to listOf(
                        "dueEpochMillis INTEGER",
                        "repeatRule TEXT NOT NULL DEFAULT ''",
                        "project TEXT NOT NULL DEFAULT ''",
                        "labels TEXT NOT NULL DEFAULT ''",
                        "parentId INTEGER",
                        "sortOrder INTEGER NOT NULL DEFAULT 0"
                    )
                )
                columns.forEach { (table, definitions) ->
                    definitions.forEach { definition ->
                        db.execSQL("ALTER TABLE `$table` ADD COLUMN $definition")
                    }

                }
                db.execSQL("CREATE TABLE IF NOT EXISTS `habit_skips` (`habitId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `reason` TEXT NOT NULL, PRIMARY KEY(`habitId`, `epochDay`), FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_skips_habitId` ON `habit_skips` (`habitId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_skips_epochDay` ON `habit_skips` (`epochDay`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `category_budgets` (`category` TEXT NOT NULL, `monthKey` TEXT NOT NULL, `limitMinorUnits` INTEGER NOT NULL, PRIMARY KEY(`category`, `monthKey`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_budgets_monthKey` ON `category_budgets` (`monthKey`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `loan_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `loanId` INTEGER NOT NULL, `amountMinorUnits` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `note` TEXT NOT NULL, FOREIGN KEY(`loanId`) REFERENCES `loans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_payments_loanId` ON `loan_payments` (`loanId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_payments_epochDay` ON `loan_payments` (`epochDay`)")
            }

        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `vault_entries` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `vault_entries` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `vault_entries` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `vault_entries` ADD COLUMN `deviceId` TEXT NOT NULL DEFAULT ''")
                db.query("SELECT id FROM `vault_entries`").use { cursor ->
                    while (cursor.moveToNext()) {
                        db.execSQL("UPDATE `vault_entries` SET uuid = ? WHERE id = ?", arrayOf(UUID.randomUUID().toString(), cursor.getInt(0)))
                    }

                }
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vault_entries_uuid` ON `vault_entries` (`uuid`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_state` (`tableName` TEXT NOT NULL, `cursor` TEXT NOT NULL, `pendingCount` INTEGER NOT NULL, `conflictCount` INTEGER NOT NULL, `lastSyncEpochMillis` INTEGER NOT NULL, `lastError` TEXT, `paused` INTEGER NOT NULL, PRIMARY KEY(`tableName`))")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `habit_skips` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `habit_skips` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `habit_skips` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `habit_skips` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `habit_skips` ADD COLUMN `deviceId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `category_budgets` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `category_budgets` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `category_budgets` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `category_budgets` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `category_budgets` ADD COLUMN `deviceId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `loan_payments` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `loan_payments` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `loan_payments` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `loan_payments` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `loan_payments` ADD COLUMN `deviceId` TEXT NOT NULL DEFAULT ''")
                listOf("habit_skips", "category_budgets", "loan_payments").forEach { table ->
                    db.query("SELECT rowid FROM `$table` WHERE uuid = ''").use { cursor ->
                        while (cursor.moveToNext()) {
                            db.execSQL("UPDATE `$table` SET uuid = ? WHERE rowid = ?", arrayOf(UUID.randomUUID().toString(), cursor.getLong(0)))
                        }
                    }
                }
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_skips_uuid` ON `habit_skips` (`uuid`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_budgets_uuid` ON `category_budgets` (`uuid`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_loan_payments_uuid` ON `loan_payments` (`uuid`)")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `life_rules` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`body` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`pinned` INTEGER NOT NULL, " +
                        "`createdEpochDay` INTEGER NOT NULL DEFAULT 0, " +
                        "`uuid` TEXT NOT NULL DEFAULT '', " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`deleted` INTEGER NOT NULL DEFAULT 0, " +
                        "`dirty` INTEGER NOT NULL DEFAULT 1)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_life_rules_kind` ON `life_rules` (`kind`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_life_rules_pinned` ON `life_rules` (`pinned`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_life_rules_uuid` ON `life_rules` (`uuid`)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_notifications` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`message` TEXT NOT NULL, " +
                        "`repeatMode` TEXT NOT NULL, " +
                        "`hour` INTEGER NOT NULL, " +
                        "`minute` INTEGER NOT NULL, " +
                        "`dateEpochDay` INTEGER, " +
                        "`daysMask` INTEGER NOT NULL, " +
                        "`dayOfMonth` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`lastDeliveredEpochMillis` INTEGER NOT NULL DEFAULT 0, " +
                        "`uuid` TEXT NOT NULL DEFAULT '', " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`deleted` INTEGER NOT NULL DEFAULT 0, " +
                        "`dirty` INTEGER NOT NULL DEFAULT 1)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_notifications_enabled` ON `custom_notifications` (`enabled`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_notifications_repeatMode` ON `custom_notifications` (`repeatMode`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_notifications_uuid` ON `custom_notifications` (`uuid`)")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loan_people` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `phone` TEXT NOT NULL DEFAULT '', " +
                        "`address` TEXT NOT NULL DEFAULT '', `profileImageUri` TEXT NOT NULL DEFAULT '', " +
                        "`notes` TEXT NOT NULL DEFAULT '', `archived` INTEGER NOT NULL DEFAULT 0, " +
                        "`uuid` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`deleted` INTEGER NOT NULL DEFAULT 0, `dirty` INTEGER NOT NULL DEFAULT 1)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_people_archived` ON `loan_people` (`archived`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_loan_people_uuid` ON `loan_people` (`uuid`)")
                db.execSQL("ALTER TABLE `loans` ADD COLUMN `title` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `loans` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'pending'")
                db.execSQL("ALTER TABLE `loans` ADD COLUMN `personId` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_personId` ON `loans` (`personId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_status` ON `loans` (`status`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loan_attachments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `loanId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `uri` TEXT NOT NULL, `mime` TEXT NOT NULL DEFAULT '', " +
                        "`addedAt` INTEGER NOT NULL DEFAULT 0, `uuid` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0, `deleted` INTEGER NOT NULL DEFAULT 0, " +
                        "`dirty` INTEGER NOT NULL DEFAULT 1, FOREIGN KEY(`loanId`) REFERENCES `loans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_attachments_loanId` ON `loan_attachments` (`loanId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_loan_attachments_uuid` ON `loan_attachments` (`uuid`)")
                db.execSQL("INSERT INTO loan_people (name, uuid) SELECT DISTINCT TRIM(person), lower(hex(randomblob(16))) FROM loans WHERE TRIM(person) <> ''")
                db.execSQL("UPDATE loans SET personId = (SELECT id FROM loan_people p WHERE p.name = TRIM(loans.person) LIMIT 1)")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `life_rule_files` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ruleId` INTEGER NOT NULL, " +
                            "`name` TEXT NOT NULL, `uri` TEXT NOT NULL, `mime` TEXT NOT NULL DEFAULT '', " +
                            "`folderPath` TEXT NOT NULL DEFAULT '', `addedAt` INTEGER NOT NULL DEFAULT 0, " +
                            "`uuid` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL DEFAULT 0, " +
                            "`deleted` INTEGER NOT NULL DEFAULT 0, `dirty` INTEGER NOT NULL DEFAULT 1, " +
                            "FOREIGN KEY(`ruleId`) REFERENCES `life_rules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_life_rule_files_ruleId` ON `life_rule_files` (`ruleId`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_life_rule_files_uuid` ON `life_rule_files` (`uuid`)")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("PRAGMA foreign_keys=OFF")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `loans_repaired` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `person` TEXT NOT NULL, " +
                            "`amountMinorUnits` INTEGER NOT NULL, `lent` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                            "`epochDay` INTEGER NOT NULL, `settled` INTEGER NOT NULL, `uuid` TEXT NOT NULL DEFAULT '', " +
                            "`updatedAt` INTEGER NOT NULL DEFAULT 0, `deleted` INTEGER NOT NULL DEFAULT 0, " +
                            "`dirty` INTEGER NOT NULL DEFAULT 1, `dueEpochDay` INTEGER, `title` TEXT NOT NULL DEFAULT '', " +
                            "`status` TEXT NOT NULL DEFAULT 'pending', `personId` INTEGER, " +
                            "FOREIGN KEY(`personId`) REFERENCES `loan_people`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
                    )
                    db.execSQL(
                        "INSERT INTO `loans_repaired` (id, person, amountMinorUnits, lent, note, epochDay, settled, uuid, " +
                            "updatedAt, deleted, dirty, dueEpochDay, title, status, personId) " +
                            "SELECT id, person, amountMinorUnits, lent, note, epochDay, settled, uuid, updatedAt, deleted, dirty, " +
                            "dueEpochDay, title, status, personId FROM `loans`"
                    )
                    db.execSQL("DROP TABLE `loans`")
                    db.execSQL("ALTER TABLE `loans_repaired` RENAME TO `loans`")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_settled` ON `loans` (`settled`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_epochDay` ON `loans` (`epochDay`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_personId` ON `loans` (`personId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_status` ON `loans` (`status`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_loans_uuid` ON `loans` (`uuid`)")
                    db.execSQL("PRAGMA foreign_keys=ON")
                }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `contacts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL DEFAULT '', `email` TEXT NOT NULL DEFAULT '', `address` TEXT NOT NULL DEFAULT '', `notes` TEXT NOT NULL DEFAULT '', `profileImageUri` TEXT NOT NULL DEFAULT '', `uuid` TEXT NOT NULL DEFAULT '', `updatedAt` INTEGER NOT NULL DEFAULT 0, `deleted` INTEGER NOT NULL DEFAULT 0, `dirty` INTEGER NOT NULL DEFAULT 1)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_name` ON `contacts` (`name`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_contacts_uuid` ON `contacts` (`uuid`)")
            }
        }

        private fun Double.toMinorUnitsForMigration(): Long {
            require(isFinite()) { "A stored money value is not finite; database migration was stopped to protect data." }
            return BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_5_6)
                    .addMigrations(MIGRATION_6_7)
                    .addMigrations(MIGRATION_7_8)
                    .addMigrations(MIGRATION_8_9)
                    .addMigrations(MIGRATION_9_10)
                    .addMigrations(MIGRATION_10_11)
                    .addMigrations(MIGRATION_11_12)
                    .addMigrations(MIGRATION_12_13)
                    .addMigrations(MIGRATION_13_14)
                    .addMigrations(MIGRATION_14_15)
                    .addMigrations(MIGRATION_15_16)
                    .addMigrations(MIGRATION_16_17)
                    .addMigrations(MIGRATION_17_18)
                    .addMigrations(MIGRATION_18_19)
                    .addMigrations(MIGRATION_19_20)
                    .build().also { instance = it }
            }
    }

}
