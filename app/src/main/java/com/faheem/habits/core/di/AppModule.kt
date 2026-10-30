package com.faheem.habits.core.di

import android.content.Context
import androidx.room.Room
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.ExpenseDao
import com.faheem.habits.core.data.FileDao
import com.faheem.habits.core.data.ToDoDao
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
}
