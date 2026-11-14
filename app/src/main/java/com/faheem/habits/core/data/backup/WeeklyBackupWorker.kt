package com.faheem.habits.core.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val BACKUP_PREFS = "momentum_data_backup"
private const val FOLDER_KEY = "weekly_folder_uri"
private const val ENABLED_KEY = "weekly_enabled"
private const val LAST_BACKUP_KEY = "last_backup_at"
private const val UNIQUE_WORK = "momentum_weekly_local_backup"

object WeeklyBackupSettings {
    fun enabled(context: Context) = context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED_KEY, false)
    fun folder(context: Context) = context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE).getString(FOLDER_KEY, null)
    fun lastBackupAt(context: Context) = context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE).getLong(LAST_BACKUP_KEY, 0L)

    fun setFolder(context: Context, uri: Uri) {
        context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE).edit().putString(FOLDER_KEY, uri.toString()).apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED_KEY, enabled).apply()
        val manager = WorkManager.getInstance(context)
        if (enabled && folder(context) != null) {
            val request = PeriodicWorkRequestBuilder<WeeklyBackupWorker>(7, TimeUnit.DAYS).build()
            manager.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            manager.cancelUniqueWork(UNIQUE_WORK)
        }
    }
}

class WeeklyBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val folderString = WeeklyBackupSettings.folder(applicationContext) ?: return@withContext Result.failure()
        val tree = Uri.parse(folderString)
        try {
            val resolver = applicationContext.contentResolver
            val treeDocument = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val fileName = "momentum-weekly-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.json"
            val destination = DocumentsContract.createDocument(resolver, treeDocument, "application/json", fileName)
                ?: return@withContext Result.retry()
            MomentumBackup.export(applicationContext, destination)
            pruneCopies(tree)
            applicationContext.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE).edit()
                .putLong(LAST_BACKUP_KEY, System.currentTimeMillis()).commit()
            Result.success()
        } catch (_: SecurityException) {
            Result.failure()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun pruneCopies(tree: Uri) {
        val resolver = applicationContext.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val rows = mutableListOf<Pair<Long, Uri>>()
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val modifiedColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                if (!name.startsWith("momentum-weekly-") || !name.endsWith(".json")) continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(idColumn))
                rows += (if (cursor.isNull(modifiedColumn)) 0L else cursor.getLong(modifiedColumn)) to uri
            }
        }
        rows.sortedByDescending { it.first }.drop(5).forEach { (_, uri) -> DocumentsContract.deleteDocument(resolver, uri) }
    }
}
