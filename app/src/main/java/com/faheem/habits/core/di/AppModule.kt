package com.faheem.habits.core.di

import android.content.Context
import androidx.room.Room
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.ContactDao
import com.faheem.habits.core.data.CustomNotificationDao
import com.faheem.habits.core.data.ExpenseDao
import com.faheem.habits.core.data.FileDao
import com.faheem.habits.core.data.HabitDao
import com.faheem.habits.core.data.LifeRuleDao
import com.faheem.habits.core.data.LoanDao
import com.faheem.habits.core.data.ToDoDao
import com.faheem.habits.core.data.SyncStateDao
import com.faheem.habits.core.data.VaultDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = AppDatabase.get(context)

    @Provides
    fun provideToDoDao(database: AppDatabase): ToDoDao = database.toDoDao()

    @Provides
    fun provideExpenseDao(database: AppDatabase): ExpenseDao = database.expenseDao()

    @Provides
    fun provideFileDao(database: AppDatabase): FileDao = database.fileDao()

    @Provides
    fun provideHabitDao(database: AppDatabase): HabitDao = database.habitDao()

    @Provides
    fun provideLifeRuleDao(database: AppDatabase): LifeRuleDao = database.lifeRuleDao()

    @Provides
    fun provideLoanDao(database: AppDatabase): LoanDao = database.loanDao()

    @Provides
    fun provideContactDao(database: AppDatabase): ContactDao = database.contactDao()

    @Provides
    fun provideCustomNotificationDao(database: AppDatabase): CustomNotificationDao = database.customNotificationDao()

    @Provides
    fun provideVaultDao(database: AppDatabase): VaultDao = database.vaultDao()

    @Provides
    fun provideSyncStateDao(database: AppDatabase): SyncStateDao = database.syncStateDao()
}
