package com.faheem.habits.features.vault.data

import com.faheem.habits.core.data.VaultDao
import com.faheem.habits.core.data.VaultRecord
import javax.inject.Inject

class VaultRepository @Inject constructor(
    private val dao: VaultDao
) {
    suspend fun getAll() = dao.getAll()
    suspend fun getAllForSync() = dao.getAllForSync()
    suspend fun getById(id: Int) = dao.getById(id)
    suspend fun getByUuid(uuid: String) = dao.getByUuid(uuid)
    suspend fun upsert(record: VaultRecord) = dao.upsert(record)
    suspend fun upsertAll(records: List<VaultRecord>) = dao.upsertAll(records)
    suspend fun insertImported(record: VaultRecord) = dao.insertImported(record)
    suspend fun markDeleted(id: Int, updatedAt: Long) = dao.markDeleted(id, updatedAt)
    suspend fun markAllDeleted(updatedAt: Long) = dao.markAllDeleted(updatedAt)
}
