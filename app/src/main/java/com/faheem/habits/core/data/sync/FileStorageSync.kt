package com.faheem.habits.core.data.sync

import android.content.Context
import android.net.Uri
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.FileItem
import com.faheem.habits.core.data.supabase.SupabaseRestClient
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

internal const val FILE_BUCKET = "momentum-files"
private const val MAX_DELETE_PATHS = 1_000

internal const val MAX_FILE_TRANSFER_BYTES = 10 * 1024 * 1024
private const val SIZE_LIMIT_MESSAGE = " exceeds the 10 MB safety limit."

/** Reads at most [remainingBytes] (capped at [MAX_FILE_TRANSFER_BYTES]) without buffering an unbounded stream. */
internal fun InputStream.readBoundedFileBytes(
    description: String = "File",
    remainingBytes: Int = MAX_FILE_TRANSFER_BYTES
): ByteArray {
    val limit = minOf(remainingBytes, MAX_FILE_TRANSFER_BYTES)
    require(limit >= 0) { "$description$SIZE_LIMIT_MESSAGE" }
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (total < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - total))
        if (count < 0) break
        total += count
        output.write(buffer, 0, count)
    }
    if (total == limit && read() >= 0) throw IllegalArgumentException("$description$SIZE_LIMIT_MESSAGE")
    return output.toByteArray()
}

/** Reads text in chunks so oversized imports are rejected before the whole file is held in memory. */
internal fun InputStream.readBoundedText(description: String = "File"): String = bufferedReader().use { reader ->
    val buffer = CharArray(8192)
    val output = StringBuilder()
    var total = 0
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) break
        total += count
        require(total <= MAX_FILE_TRANSFER_BYTES) { "$description$SIZE_LIMIT_MESSAGE" }
        output.append(buffer, 0, count)
    }
    output.toString()
}

internal fun ByteArray.requireWithinFileLimit(description: String = "File") {
    require(size <= MAX_FILE_TRANSFER_BYTES) { "$description$SIZE_LIMIT_MESSAGE" }
}

private suspend fun <T> safeCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Result.failure(failure)
}

class FileStorageSync(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.get(context),
    private val client: SupabaseRestClient = SupabaseRestClient(context)
) {
    /** Known cloud object paths for this account, bounded before deletion. */
    suspend fun storagePathsForAccount(): List<Pair<String, String>> = buildList {
        db.fileDao().getAllForSync().forEach { add(FILE_BUCKET to it.uuid) }
        db.loanDao().getAllPeopleForSync().forEach { add(FILE_BUCKET to "loan-people/${it.uuid}/profile") }
        db.contactDao().getAllForSync().forEach { add(FILE_BUCKET to "contacts/${it.uuid}/profile") }
        db.loanDao().getAllAttachmentsForSync().forEach { add(FILE_BUCKET to "loan-attachments/${it.uuid}") }
        db.lifeRuleDao().getAllFilesForSync().forEach { add(FILE_BUCKET to "life-rule-files/${it.uuid}") }
    }.distinct().take(MAX_DELETE_PATHS)

    /** Removes cloud objects for rows whose tombstones have been synced successfully. */
    suspend fun deleteTombstonedObjects(): Int {
        val candidates = buildList {
            fun add(bucket: String, path: String, deleted: Boolean, dirty: Boolean) {
                this.add(StorageTombstoneCandidate(bucket, path, deleted, dirty))
            }
            db.fileDao().getAllForSync().forEach { add(FILE_BUCKET, it.uuid, it.deleted, it.dirty) }
            db.loanDao().getAllPeopleForSync().forEach {
                add(FILE_BUCKET, "loan-people/${it.uuid}/profile", it.deleted, it.dirty)
            }
            db.contactDao().getAllForSync().forEach {
                add(FILE_BUCKET, "contacts/${it.uuid}/profile", it.deleted, it.dirty)
            }
            db.loanDao().getAllAttachmentsForSync().forEach {
                add(FILE_BUCKET, "loan-attachments/${it.uuid}", it.deleted, it.dirty)
            }
            db.lifeRuleDao().getAllFilesForSync().forEach {
                add(FILE_BUCKET, "life-rule-files/${it.uuid}", it.deleted, it.dirty)
            }
        }
        val paths = planStorageTombstoneDeletion(candidates, MAX_DELETE_PATHS)
        return if (paths.isEmpty()) 0 else client.deleteStorageObjects(paths, strict = true)
    }

    /** One bad item is recorded and skipped; the remaining uploads still run. */
    suspend fun uploadPending() {
        val resolver = context.contentResolver
        var failures = 0

        suspend fun uploadOne(
            description: String,
            uriValue: String,
            path: String,
            mime: String,
            onFailure: (suspend () -> Unit)? = null
        ) {
            val uploaded = safeCall {
                val uri = Uri.parse(uriValue)
                resolver.openInputStream(uri)?.use { input ->
                    client.uploadStorageStream(FILE_BUCKET, path, input, mime)
                } ?: throw IOException("Could not open $description for cloud backup.")
            }
            if (uploaded.isFailure) {
                failures++
                onFailure?.invoke()
            }
        }

        for (file in db.fileDao().getAllForSync().filter { it.dirty && !it.deleted }) {
            uploadOne("File", file.uri, file.uuid, file.mime) {
                safeCall { db.fileDao().markMissing(file.id) }
            }
        }
        for (person in db.loanDao().getAllPeopleForSync()
            .filter { it.dirty && !it.deleted && shouldUploadProfileImage(it.profileImageUri) }
        ) {
            val uri = Uri.parse(person.profileImageUri)
            uploadOne(
                "Profile photo",
                person.profileImageUri,
                "loan-people/${person.uuid}/profile",
                resolver.getType(uri).orEmpty()
            )
        }
        for (contact in db.contactDao().getAllForSync()
            .filter { it.dirty && !it.deleted && shouldUploadProfileImage(it.profileImageUri) }
        ) {
            val uri = Uri.parse(contact.profileImageUri)
            uploadOne(
                "Profile photo",
                contact.profileImageUri,
                "contacts/${contact.uuid}/profile",
                resolver.getType(uri).orEmpty()
            )
        }
        for (attachment in db.loanDao().getAllAttachmentsForSync().filter { it.dirty && !it.deleted }) {
            uploadOne("Loan attachment", attachment.uri, "loan-attachments/${attachment.uuid}", attachment.mime)
        }
        for (file in db.lifeRuleDao().getAllFilesForSync().filter { it.dirty && !it.deleted }) {
            uploadOne("Attachment", file.uri, "life-rule-files/${file.uuid}", file.mime)
        }

        if (failures > 0) throw IOException("$failures file${if (failures == 1) "" else "s"} could not be backed up.")
    }

    suspend fun restoreMissingFiles() {
        val resolver = context.contentResolver
        val restoreDirectory = File(context.filesDir, "synced-files").apply { mkdirs() }
        var failures = 0

        for (file in db.fileDao().getAllForSync().filterNot { it.deleted }) {
            if (canRead(resolver, file.uri)) {
                if (file.missing) safeCall { db.fileDao().markAvailable(file.id) }
                continue
            }
            val restored = safeCall {
                val extension = extensionFor(file.name)
                val destination = File(restoreDirectory, "${file.uuid}$extension")
                val temp = File(restoreDirectory, "${file.uuid}$extension.tmp")
                client.downloadStorageTo(FILE_BUCKET, file.uuid, temp)
                replaceTempFile(temp, destination)
                db.fileDao().update(file.copy(uri = Uri.fromFile(destination).toString(), dirty = false, missing = false))
            }
            if (restored.isFailure) {
                failures++
                safeCall { db.fileDao().markMissing(file.id) }
            }
        }
        for (person in db.loanDao().getAllPeopleForSync().filterNot { it.deleted }) {
            val path = "loan-people/${person.uuid}/profile"
            val restored = safeCall {
                val uri = restore(uri = person.profileImageUri, path = path, name = "${person.uuid}-profile", folder = "loan-people")
                if (uri != null) db.loanDao().updatePerson(person.copy(profileImageUri = uri, dirty = false))
                uri
            }
            if (restored.isFailure) failures++
        }
        for (contact in db.contactDao().getAllForSync().filterNot { it.deleted }) {
            val path = "contacts/${contact.uuid}/profile"
            val restored = safeCall {
                val uri = restore(uri = contact.profileImageUri, path = path, name = "${contact.uuid}-profile", folder = "contacts")
                if (uri != null) db.contactDao().update(contact.copy(profileImageUri = uri, dirty = false))
                uri
            }
            if (restored.isFailure) failures++
        }
        for (attachment in db.loanDao().getAllAttachmentsForSync().filterNot { it.deleted }) {
            val path = "loan-attachments/${attachment.uuid}"
            val restored = safeCall {
                val uri = restore(attachment.uri, path, attachment.name, "loan-attachments")
                if (uri != null) db.loanDao().updateAttachment(attachment.copy(uri = uri, dirty = false))
                uri
            }
            if (restored.isFailure) failures++
        }
        for (file in db.lifeRuleDao().getAllFilesForSync().filterNot { it.deleted }) {
            val path = "life-rule-files/${file.uuid}"
            val restored = safeCall {
                val uri = restore(file.uri, path, file.name, "life-rule-files")
                if (uri != null) db.lifeRuleDao().updateFile(file.copy(uri = uri, dirty = false))
                uri
            }
            if (restored.isFailure) failures++
        }

        if (failures > 0) throw IOException("$failures file${if (failures == 1) "" else "s"} could not be restored.")
    }

    private suspend fun restore(uri: String, path: String, name: String, folder: String): String? {
        val explicitPath = cloudProfilePath(uri)
        if (uri.isBlank() && explicitPath == null) return null
        if (explicitPath == null && canRead(context.contentResolver, uri)) return null
        val downloadPath = explicitPath ?: path
        val extension = extensionFor(name)
        val directory = File(context.filesDir, "synced-files/$folder").apply { mkdirs() }
        val destination = File(directory, "${downloadPath.replace('/', '-')}$extension")
        val temp = File(directory, "${downloadPath.replace('/', '-')}$extension.tmp")
        client.downloadStorageTo(FILE_BUCKET, downloadPath, temp)
        replaceTempFile(temp, destination)
        return Uri.fromFile(destination).toString()
    }

    private fun extensionFor(name: String): String =
        name.substringAfterLast('.', "").trim().takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()

    /** Writes via a temporary file so a failed restore cannot leave a partially restored document. */
    private fun replaceTempFile(temp: File, destination: File) {
        try {
            if (destination.exists() && !destination.delete()) error("Could not replace a restored file.")
            if (!temp.renameTo(destination)) error("Could not finish writing a restored file.")
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun canRead(resolver: android.content.ContentResolver, uri: String): Boolean =
        try {
            uri.isNotBlank() && resolver.openInputStream(Uri.parse(uri))?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
}
