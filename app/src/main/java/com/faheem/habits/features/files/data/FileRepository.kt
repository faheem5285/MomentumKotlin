package com.faheem.habits.features.files.data

import android.content.Context
import android.net.Uri
import com.faheem.habits.core.data.FileDao
import com.faheem.habits.core.data.FileItem
import com.faheem.habits.core.data.sync.MAX_FILE_TRANSFER_BYTES
import com.faheem.habits.core.data.today
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class FileAddResult {
    LINKED,
    TEMPORARY_LINK,
    TOO_LARGE,
    ACCESS_DENIED,
    INVALID_FILE,
    FAILED
}

data class FileLinkDraft(
    val name: String,
    val uri: String,
    val mime: String,
    val folder: String = "",
    val sizeBytes: Long? = null,
    val persisted: Boolean = true
)

class FileRepository @Inject constructor(
    private val dao: FileDao,
    @ApplicationContext private val context: Context
) {
    val files = dao.getAll()

    suspend fun add(draft: FileLinkDraft): FileAddResult = withContext(Dispatchers.IO) {
        when (val validation = validate(draft)) {
            FileAddResult.LINKED, FileAddResult.TEMPORARY_LINK -> {
                val missing = validation == FileAddResult.TEMPORARY_LINK
                val inserted = runCatching {
                    dao.insert(
                        FileItem(
                            name = draft.name.trim().ifBlank { "File" },
                            uri = draft.uri,
                            mime = draft.mime.ifBlank { "*/*" },
                            folder = draft.folder.trim('/'),
                            addedEpochDay = today(),
                            missing = missing
                        )
                    )
                }
                if (inserted.isFailure) FileAddResult.FAILED else validation
            }
            else -> validation
        }
    }

    suspend fun reconnect(file: FileItem, draft: FileLinkDraft): FileAddResult = withContext(Dispatchers.IO) {
        when (val validation = validate(draft)) {
            FileAddResult.LINKED, FileAddResult.TEMPORARY_LINK -> {
                val missing = validation == FileAddResult.TEMPORARY_LINK
                val updated = runCatching {
                    dao.update(
                        file.copy(
                            name = draft.name.trim().ifBlank { file.name },
                            uri = draft.uri,
                            mime = draft.mime.ifBlank { file.mime },
                            folder = draft.folder.trim('/'),
                            lastAccessedEpochDay = today(),
                            missing = missing,
                            dirty = true,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                if (updated.isFailure) FileAddResult.FAILED else validation
            }
            else -> validation
        }
    }

    suspend fun delete(file: FileItem) = dao.delete(file.id)

    suspend fun markAccessed(file: FileItem) {
        if (file.lastAccessedEpochDay == today()) return
        runCatching { dao.update(file.copy(lastAccessedEpochDay = today())) }
    }

    suspend fun markMissing(file: FileItem) = dao.markMissing(file.id)

    suspend fun markAvailable(file: FileItem) = dao.markAvailable(file.id)

    /** Never persists a URI that cannot currently be opened. */
    private fun validate(draft: FileLinkDraft): FileAddResult {
        if (draft.name.isBlank() || draft.uri.isBlank()) return FileAddResult.INVALID_FILE
        val size = draft.sizeBytes
        if (size != null && size < 0) return FileAddResult.INVALID_FILE
        if (size != null && size > MAX_FILE_TRANSFER_BYTES) return FileAddResult.TOO_LARGE
        val uri = runCatching { Uri.parse(draft.uri) }.getOrNull() ?: return FileAddResult.INVALID_FILE
        val readable = try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
        if (!readable) return FileAddResult.ACCESS_DENIED
        return if (draft.persisted) FileAddResult.LINKED else FileAddResult.TEMPORARY_LINK
    }
}
