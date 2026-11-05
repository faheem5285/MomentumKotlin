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
        db.fileDao().getAllForSync().filter { it.dirty && !it.deleted }.forEach { file ->
            val bytes = resolver.openInputStream(Uri.parse(file.uri))?.use { it.readBytes() }
                ?: throw IOException("Could not read file '${file.name}' for cloud backup.")
            client.uploadStorage(FILE_BUCKET, file.uuid, bytes, file.mime)
        }
        db.loanDao().getAllPeopleForSync().filter { it.dirty && !it.deleted && it.profileImageUri.isNotBlank() }.forEach { person ->
            val bytes = resolver.openInputStream(Uri.parse(person.profileImageUri))?.use { it.readBytes() }
                ?: throw IOException("Could not read profile photo for '${person.name}'.")
            client.uploadStorage(FILE_BUCKET, "loan-people/${person.uuid}/profile", bytes, resolver.getType(Uri.parse(person.profileImageUri)).orEmpty())
        }
        db.contactDao().getAllForSync().filter { it.dirty && !it.deleted && it.profileImageUri.isNotBlank() }.forEach { contact ->
            val bytes = resolver.openInputStream(Uri.parse(contact.profileImageUri))?.use { it.readBytes() }
                ?: throw IOException("Could not read profile photo for '${contact.name}'.")
            client.uploadStorage(FILE_BUCKET, "contacts/${contact.uuid}/profile", bytes, resolver.getType(Uri.parse(contact.profileImageUri)).orEmpty())
        }
        db.loanDao().getAllAttachmentsForSync().filter { it.dirty && !it.deleted }.forEach { attachment ->
            val bytes = resolver.openInputStream(Uri.parse(attachment.uri))?.use { it.readBytes() }
                ?: throw IOException("Could not read loan proof '${attachment.name}'.")
            client.uploadStorage(FILE_BUCKET, "loan-attachments/${attachment.uuid}", bytes, attachment.mime)
        }
        db.lifeRuleDao().getAllFilesForSync().filter { it.dirty && !it.deleted }.forEach { file ->
            val bytes = resolver.openInputStream(Uri.parse(file.uri))?.use { it.readBytes() }
                ?: throw IOException("Could not read attachment '${file.name}'.")
            client.uploadStorage(FILE_BUCKET, "life-rule-files/${file.uuid}", bytes, file.mime)
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
        db.loanDao().getAllPeopleForSync().filterNot { it.deleted }.forEach { person ->
            val path = "loan-people/${person.uuid}/profile"
            val restored = restore(uri = person.profileImageUri, path = path, name = "${person.uuid}-profile", folder = "loan-people")
            if (restored != null) db.loanDao().updatePerson(person.copy(profileImageUri = restored, dirty = false))
        }
        db.contactDao().getAllForSync().filterNot { it.deleted }.forEach { contact ->
            val path = "contacts/${contact.uuid}/profile"
            val restored = restore(uri = contact.profileImageUri, path = path, name = "${contact.uuid}-profile", folder = "contacts")
            if (restored != null) db.contactDao().update(contact.copy(profileImageUri = restored, dirty = false))
        }
        db.loanDao().getAllAttachmentsForSync().filterNot { it.deleted }.forEach { attachment ->
            val path = "loan-attachments/${attachment.uuid}"
            val restored = restore(attachment.uri, path, attachment.name, "loan-attachments")
            if (restored != null) db.loanDao().updateAttachment(attachment.copy(uri = restored, dirty = false))
        }
        db.lifeRuleDao().getAllFilesForSync().filterNot { it.deleted }.forEach { file ->
            val path = "life-rule-files/${file.uuid}"
            val restored = restore(file.uri, path, file.name, "life-rule-files")
            if (restored != null) db.lifeRuleDao().updateFile(file.copy(uri = restored, dirty = false))
        }
    }

    private suspend fun restore(uri: String, path: String, name: String, folder: String): String? {
        if (uri.isBlank() || canRead(context.contentResolver, uri)) return null
        val bytes = client.downloadStorage(FILE_BUCKET, path)
        val extension = name.substringAfterLast('.', "").trim().takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        val directory = File(context.filesDir, "synced-files/$folder").apply { mkdirs() }
        val destination = File(directory, "${path.replace('/', '-')}$extension")
        destination.writeBytes(bytes)
        return Uri.fromFile(destination).toString()
    }

    private fun canRead(resolver: android.content.ContentResolver, uri: String): Boolean =
        try {
            resolver.openInputStream(Uri.parse(uri))?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
}
