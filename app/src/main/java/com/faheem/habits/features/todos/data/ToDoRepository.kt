package com.faheem.habits.features.todos.data

import android.content.Context
import androidx.room.withTransaction
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.ToDoDao
import com.faheem.habits.core.data.ToDoItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ToDoRepository @Inject constructor(
    private val dao: ToDoDao,
    @ApplicationContext private val context: Context
) {
    private val database get() = AppDatabase.get(context.applicationContext)

    val items: Flow<List<ToDoItem>> = dao.getAll()

    suspend fun save(item: ToDoItem): Int {
        val changed = item.copy(
            updatedAt = System.currentTimeMillis(),
            deleted = false,
            dirty = true
        )
        return database.withTransaction {
            if (changed.id == 0) dao.insert(changed).toInt() else changed.id.also { dao.update(changed) }
        }
    }

    suspend fun getById(id: Int): ToDoItem? = dao.getById(id)

    /** A parent and its subtasks become tombstones in one commit. */
    suspend fun delete(item: ToDoItem) = database.withTransaction {
        val updatedAt = System.currentTimeMillis()
        dao.deleteChildren(item.id, updatedAt)
        dao.delete(item.id, updatedAt)
    }
}
