package com.faheem.habits.features.todos.data

import com.faheem.habits.core.data.ToDoDao
import com.faheem.habits.core.data.ToDoItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ToDoRepository @Inject constructor(
    private val dao: ToDoDao
) {
    val items: Flow<List<ToDoItem>> = dao.getAll()
    suspend fun save(item: ToDoItem): Int =
        if (item.id == 0) dao.insert(item).toInt() else item.id.also { dao.update(item) }
    suspend fun getById(id: Int): ToDoItem? = dao.getById(id)
    suspend fun delete(item: ToDoItem) = dao.delete(item)
}
