package com.faheem.habits.features.files.data

import com.faheem.habits.core.data.FileDao
import com.faheem.habits.core.data.FileItem
import com.faheem.habits.core.data.today
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FileRepository @Inject constructor(
    private val dao: FileDao
) {
    val files: Flow<List<FileItem>> = dao.getAll()
    suspend fun add(name: String, uri: String, mime: String) =
        dao.insert(FileItem(name = name, uri = uri, mime = mime, addedEpochDay = today()))
    suspend fun delete(file: FileItem) = dao.delete(file)
}
