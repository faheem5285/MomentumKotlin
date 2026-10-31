package com.faheem.habits.core.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode

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
    indices = [Index("habitId"), Index("epochDay")]
)
data class HabitSkip(
    val habitId: Int,
    val epochDay: Long,
    val reason: String = "freeze"
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

@Entity(tableName = "loans", indices = [Index("settled"), Index("epochDay"), Index(value = ["uuid"], unique = true)])
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
)

@Entity(
    tableName = "loan_payments",
    indices = [Index("loanId"), Index("epochDay")]
)
data class LoanPayment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val loanId: Int,
    val amountMinorUnits: Long,
    val epochDay: Long,
    val note: String = ""
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

@Entity(tableName = "vault_entries")
data class VaultRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val updatedAt: Long
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

@Entity(
    tableName = "category_budgets",
    primaryKeys = ["category", "monthKey"],
    indices = [Index("monthKey")]
)
data class CategoryBudget(
    val category: String,
    val monthKey: String,
    val limitMinorUnits: Long
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY id")
    fun getHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits ORDER BY id")
    suspend fun getAllForBackup(): List<Habit>

    @Query("SELECT * FROM completions")
    fun getCompletions(): Flow<List<Completion>>

    @Query("SELECT * FROM habit_skips")
    fun getSkips(): Flow<List<HabitSkip>>

    @Query("SELECT * FROM completions")
    suspend fun getCompletionsForBackup(): List<Completion>

    @Query("SELECT * FROM habit_reminders ORDER BY hour, minute, id")
    fun getReminders(): Flow<List<HabitReminder>>

    @Query("SELECT * FROM habit_reminders ORDER BY id")
    suspend fun getRemindersForBackup(): List<HabitReminder>

    @Query("SELECT habit_reminders.* FROM habit_reminders INNER JOIN habits ON habits.id = habit_reminders.habitId WHERE habit_reminders.enabled = 1 AND (habit_reminders.daysMask != 0 OR habit_reminders.dateEpochDay IS NOT NULL) AND habits.archived = 0")
    suspend fun getRemindersForScheduling(): List<HabitReminder>

    @Query("SELECT * FROM habit_reminders WHERE habitId = :habitId ORDER BY hour, minute, id")
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
    @Delete suspend fun deleteReminder(reminder: HabitReminder)

    @Insert suspend fun insertHabit(habit: Habit): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertImportedHabit(habit: Habit): Long
    @Update suspend fun updateHabit(habit: Habit)
    @Delete suspend fun deleteHabit(habit: Habit)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(completion: Completion)
    @Query("SELECT * FROM completions WHERE habitId = :habitId AND epochDay = :epochDay LIMIT 1")
    suspend fun getCompletion(habitId: Int, epochDay: Long): Completion?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertImportedCompletion(completion: Completion): Long
    @Query("SELECT * FROM completions WHERE uuid = :uuid LIMIT 1") suspend fun getCompletionByUuid(uuid: String): Completion?

    @Query("DELETE FROM completions WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun deleteCompletion(habitId: Int, epochDay: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkip(skip: HabitSkip)

    @Query("DELETE FROM habit_skips WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun deleteSkip(habitId: Int, epochDay: Long)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY epochDay DESC, id DESC")
    fun getAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY id")
    suspend fun getAllForBackup(): List<Expense>

    @Insert suspend fun insert(expense: Expense)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertImported(expense: Expense): Long
    @Query("SELECT * FROM expenses WHERE uuid = :uuid LIMIT 1") suspend fun getByUuid(uuid: String): Expense?
    @Query("DELETE FROM expenses") suspend fun deleteAll()
    @Delete suspend fun delete(expense: Expense)

    @Query("SELECT * FROM category_budgets ORDER BY monthKey DESC, category")
    fun getBudgets(): Flow<List<CategoryBudget>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: CategoryBudget)
}

@Dao
@Suppress("TooManyFunctions")
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY settled ASC, epochDay DESC, id DESC")
    fun getAll(): Flow<List<Loan>>

    @Query("SELECT * FROM loans ORDER BY id")
    suspend fun getAllForBackup(): List<Loan>

    @Insert suspend fun insert(loan: Loan)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertImported(loan: Loan): Long
    @Query("SELECT * FROM loans WHERE uuid = :uuid LIMIT 1") suspend fun getByUuid(uuid: String): Loan?
    @Query("DELETE FROM loans") suspend fun deleteAll()
    @Update suspend fun update(loan: Loan)
    @Delete suspend fun delete(loan: Loan)

    @Query("SELECT * FROM loan_payments WHERE loanId = :loanId ORDER BY epochDay DESC, id DESC")
    fun getPayments(loanId: Int): Flow<List<LoanPayment>>

    @Insert suspend fun insertPayment(payment: LoanPayment)

    @Query("SELECT COALESCE(SUM(amountMinorUnits), 0) FROM loan_payments WHERE loanId = :loanId")
    suspend fun getPaidAmount(loanId: Int): Long
}

@Dao
interface FileDao {
    @Query("SELECT * FROM library_files ORDER BY id DESC")
    fun getAll(): Flow<List<FileItem>>

    @Query("SELECT * FROM library_files ORDER BY id")
    suspend fun getAllForBackup(): List<FileItem>

    @Insert suspend fun insert(file: FileItem)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertImported(file: FileItem): Long
    @Query("SELECT * FROM library_files WHERE uuid = :uuid LIMIT 1") suspend fun getByUuid(uuid: String): FileItem?
    @Query("DELETE FROM library_files") suspend fun deleteAll()
    @Delete suspend fun delete(file: FileItem)
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_entries ORDER BY updatedAt DESC")
    suspend fun getAll(): List<VaultRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: VaultRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<VaultRecord>)

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun delete(id: Int)
}

@Dao
interface ToDoDao {
    @Query("SELECT * FROM todo_items ORDER BY completed ASC, dueEpochDay ASC, id DESC")
    fun getAll(): Flow<List<ToDoItem>>

    @Query("SELECT * FROM todo_items ORDER BY id")
    suspend fun getAllForBackup(): List<ToDoItem>

    @Query("SELECT * FROM todo_items WHERE completed = 0 AND reminderHour IS NOT NULL AND reminderMinute IS NOT NULL")
    suspend fun getItemsForScheduling(): List<ToDoItem>

    @Query("SELECT * FROM todo_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): ToDoItem?

    @Insert suspend fun insert(item: ToDoItem): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertImported(item: ToDoItem): Long
    @Query("SELECT * FROM todo_items WHERE uuid = :uuid LIMIT 1") suspend fun getByUuid(uuid: String): ToDoItem?
    @Query("DELETE FROM todo_items") suspend fun deleteAll()
    @Update suspend fun update(item: ToDoItem)
    @Delete suspend fun delete(item: ToDoItem)
}

@Database(
    entities = [Habit::class, Completion::class, HabitSkip::class, HabitReminder::class, Expense::class, CategoryBudget::class, Loan::class, LoanPayment::class, FileItem::class, VaultRecord::class, ToDoItem::class],
    version = 12,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun loanDao(): LoanDao
    abstract fun fileDao(): FileDao
    abstract fun vaultDao(): VaultDao
    abstract fun toDoDao(): ToDoDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

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

        private fun Double.toMinorUnitsForMigration(): Long {
            require(isFinite()) { "A stored money value is not finite; database migration was stopped to protect data." }
            return BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "momentum.db"
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
                    .build().also { instance = it }
            }
    }

}
