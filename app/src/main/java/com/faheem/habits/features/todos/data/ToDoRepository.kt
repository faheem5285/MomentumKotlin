package com.faheem.habits.features.todos.data

import com.faheem.habits.core.data.ToDoDao
import com.faheem.habits.core.data.ToDoItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ToDoRepository @Inject constructor(
    private val dao: ToDoDao
) {
    val items: Flow<List<ToDoItem>> = dao.getAll()
    suspend fun save(item: ToDoItem): Int {
        val changed = item.copy(
            updatedAt = System.currentTimeMillis(),
            deleted = false,
            dirty = true
        )
        return if (changed.id == 0) dao.insert(changed).toInt() else changed.id.also { dao.update(changed) }
    }
    suspend fun getById(id: Int): ToDoItem? = dao.getById(id)
    suspend fun delete(item: ToDoItem) = dao.delete(item.id)
}
