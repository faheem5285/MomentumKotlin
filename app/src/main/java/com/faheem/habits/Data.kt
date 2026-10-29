package com.faheem.habits

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
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
    val settled: Boolean = false
)

@Entity(tableName = "library_files")
data class FileItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val uri: String,
    val mime: String,
    val addedEpochDay: Long
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY id")
    fun getHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM completions")
    fun getCompletions(): Flow<List<Completion>>

    @Insert suspend fun insertHabit(habit: Habit)
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

@Database(
    entities = [Habit::class, Completion::class, Expense::class, Loan::class, FileItem::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun loanDao(): LoanDao
    abstract fun fileDao(): FileDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "momentum.db"
                )
                    .fallbackToDestructiveMigration() // dev only: wipes data when schema changes
                    .build().also { instance = it }
            }
    }
}
