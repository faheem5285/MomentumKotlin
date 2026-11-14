package com.faheem.habits.features.settings.data

import com.faheem.habits.core.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Keeps database diagnostics out of the Settings UI.
 * This repository only reads database metadata and integrity status.
 */
class DatabaseHealthRepository @Inject constructor(
    private val database: AppDatabase
) {
    suspend fun databaseVersion(): Int = withContext(Dispatchers.IO) {
        database.openHelper.readableDatabase.version
    }

    suspend fun checkIntegrity(): String = withContext(Dispatchers.IO) {
        database.openHelper.readableDatabase.query("PRAGMA integrity_check").use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else "No result"
        }
    }
}
