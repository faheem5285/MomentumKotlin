package com.faheem.habits.core.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

const val ALL_DAYS = 127

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    @ColumnInfo(defaultValue = "''") val description: String = "",     // what to do
    @ColumnInfo(defaultValue = "'star'") val icon: String = "star",        // key in HabitIcons
    @ColumnInfo(defaultValue = "0") val colorIndex: Int = 0,          // index in HabitColors
    @ColumnInfo(defaultValue = "0") val timeOfDay: Int = 0,           // 0 anytime, 1 morning, 2 afternoon, 3 evening
    @ColumnInfo(defaultValue = "127") val daysMask: Int = 127,          // bit0 = Mon ... bit6 = Sun
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    @ColumnInfo(defaultValue = "0") val createdEpochDay: Long = 0
)

@Entity(
    tableName = "completions",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [ForeignKey(
        entity = Habit::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Completion(val habitId: Int, val epochDay: Long)

@Entity(
    tableName = "habit_reminders",
    foreignKeys = [ForeignKey(
        entity = Habit::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("habitId")]
)
data class HabitReminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val habitId: Int,
    val hour: Int,
    val minute: Int,
    val daysMask: Int = ALL_DAYS,
    val enabled: Boolean = true,
    val dateEpochDay: Long? = null
)

data class HabitReminderDraft(
    val hour: Int,
    val minute: Int,
    val daysMask: Int,
    val dateEpochDay: Long? = null
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val epochDay: Long
)

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val person: String,
    val amount: Double,
    val lent: Boolean,          // true = I lent money, false = I borrowed
    val note: String,
    val epochDay: Long,
    @ColumnInfo(defaultValue = "0") val settled: Boolean = false
)

@Entity(tableName = "library_files")
data class FileItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val uri: String,
    val mime: String,
    val addedEpochDay: Long
)

@Entity(tableName = "vault_entries")
data class VaultRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val updatedAt: Long
)

@Entity(tableName = "todo_items")
data class ToDoItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val notes: String = "",
    val dueEpochDay: Long,
    val completed: Boolean = false,
    @ColumnInfo(defaultValue = "'Personal'") val category: String = "Personal",
    @ColumnInfo(defaultValue = "1") val priority: Int = 1,
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY id")
    fun getHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM completions")
    fun getCompletions(): Flow<List<Completion>>

    @Query("SELECT * FROM habit_reminders ORDER BY hour, minute, id")
    fun getReminders(): Flow<List<HabitReminder>>

    @Query("SELECT habit_reminders.* FROM habit_reminders INNER JOIN habits ON habits.id = habit_reminders.habitId WHERE habit_reminders.enabled = 1 AND (habit_reminders.daysMask != 0 OR habit_reminders.dateEpochDay IS NOT NULL) AND habits.archived = 0")
    suspend fun getRemindersForScheduling(): List<HabitReminder>

    @Query("SELECT * FROM habit_reminders WHERE habitId = :habitId ORDER BY hour, minute, id")
    suspend fun getRemindersForHabit(habitId: Int): List<HabitReminder>

    @Query("SELECT * FROM habit_reminders WHERE id = :id LIMIT 1")
    suspend fun getReminder(id: Int): HabitReminder?

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun getHabit(id: Int): Habit?

    @Insert suspend fun insertReminder(reminder: HabitReminder): Long
    @Update suspend fun updateReminder(reminder: HabitReminder)
    @Delete suspend fun deleteReminder(reminder: HabitReminder)

    @Insert suspend fun insertHabit(habit: Habit): Long
    @Update suspend fun updateHabit(habit: Habit)
    @Delete suspend fun deleteHabit(habit: Habit)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(completion: Completion)

    @Query("DELETE FROM completions WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun deleteCompletion(habitId: Int, epochDay: Long)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY epochDay DESC, id DESC")
    fun getAll(): Flow<List<Expense>>

    @Insert suspend fun insert(expense: Expense)
    @Delete suspend fun delete(expense: Expense)
}

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY settled ASC, epochDay DESC, id DESC")
    fun getAll(): Flow<List<Loan>>

    @Insert suspend fun insert(loan: Loan)
    @Update suspend fun update(loan: Loan)
    @Delete suspend fun delete(loan: Loan)
}

@Dao
interface FileDao {
    @Query("SELECT * FROM library_files ORDER BY id DESC")
    fun getAll(): Flow<List<FileItem>>

    @Insert suspend fun insert(file: FileItem)
    @Delete suspend fun delete(file: FileItem)
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_entries ORDER BY updatedAt DESC")
    suspend fun getAll(): List<VaultRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: VaultRecord)

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun delete(id: Int)
}

@Dao
interface ToDoDao {
    @Query("SELECT * FROM todo_items ORDER BY completed ASC, dueEpochDay ASC, id DESC")
    fun getAll(): Flow<List<ToDoItem>>

    @Query("SELECT * FROM todo_items WHERE completed = 0 AND reminderHour IS NOT NULL AND reminderMinute IS NOT NULL")
    suspend fun getItemsForScheduling(): List<ToDoItem>

    @Query("SELECT * FROM todo_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): ToDoItem?

    @Insert suspend fun insert(item: ToDoItem): Long
    @Update suspend fun update(item: ToDoItem)
    @Delete suspend fun delete(item: ToDoItem)
}

@Database(
    entities = [Habit::class, Completion::class, HabitReminder::class, Expense::class, Loan::class, FileItem::class, VaultRecord::class, ToDoItem::class],
    version = 9,
    exportSchema = false
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
                db.execSQL("ALTER TABLE `loans` ADD COLUMN `settled` INTEGER NOT NULL DEFAULT 0")
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
                    .build().also { instance = it }
            }
    }

}
