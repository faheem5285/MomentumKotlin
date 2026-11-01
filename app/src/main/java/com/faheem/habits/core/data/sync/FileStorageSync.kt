package com.faheem.habits.core.data.sync

import android.content.Context
import android.net.Uri
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.FileItem
import com.faheem.habits.core.data.supabase.SupabaseRestClient
import java.io.File
import java.io.IOException

private const val FILE_BUCKET = "momentum-files"

class FileStorageSync(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.get(context),
    private val client: SupabaseRestClient = SupabaseRestClient(context)
) {
    suspend fun uploadPending() {
        val resolver = context.contentResolver
        db.fileDao().getAllForSync().filter { it.dirty }.forEach { file ->
            val bytes = resolver.openInputStream(Uri.parse(file.uri))?.use { it.readBytes() }
                ?: throw IOException("Could not read file '${file.name}' for cloud backup.")
            client.uploadStorage(FILE_BUCKET, file.uuid, bytes, file.mime)
        }
    }

    suspend fun restoreMissingFiles() {
        val resolver = context.contentResolver
        val restoreDirectory = File(context.filesDir, "synced-files").apply { mkdirs() }
        db.fileDao().getAllForSync().filterNot { it.deleted }.forEach { file ->
            if (canRead(resolver, file.uri)) return@forEach
            val bytes = client.downloadStorage(FILE_BUCKET, file.uuid)
            val extension = file.name.substringAfterLast('.', "").trim()
                .takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
            val destination = File(restoreDirectory, "${file.uuid}$extension")
            destination.writeBytes(bytes)
            db.fileDao().update(file.copy(uri = Uri.fromFile(destination).toString(), dirty = false))
        }
    }

    private fun canRead(resolver: android.content.ContentResolver, uri: String): Boolean =
        try {
            resolver.openInputStream(Uri.parse(uri))?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
}
