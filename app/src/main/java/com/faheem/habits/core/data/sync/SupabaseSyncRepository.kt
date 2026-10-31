@file:Suppress("ReturnCount")

package com.faheem.habits.core.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.SyncState
import com.faheem.habits.core.data.VaultRecord
import com.faheem.habits.core.data.backup.MomentumBackup
import com.faheem.habits.core.data.supabase.SupabaseRestClient
import java.io.File
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private const val VAULT_TABLE = "momentum_vault_entries"

private fun maxCursor(current: String, candidate: String): String =
    if (candidate > current) candidate else current

class SupabaseSyncRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.get(context),
    private val client: SupabaseRestClient = SupabaseRestClient(context)
) {
    suspend fun sync(): SyncRunResult = withContext(Dispatchers.IO) {
        val stateDao = db.syncStateDao()
        val existing = stateDao.get(VAULT_TABLE) ?: SyncState(VAULT_TABLE)
        if (existing.paused) return@withContext SyncRunResult()
        createFirstSyncBackup()

        val deviceId = DeviceIdentity.get(context)
        val local = db.vaultDao().getAll()
        val dirty = local.filter { it.dirty }
        val rows = buildJsonArray {
            dirty.forEach { add(it.toCloudJson(deviceId)) }
        }
        val pushed = client.upsert(VAULT_TABLE, rows)
        if (dirty.isNotEmpty()) db.vaultDao().markClean(dirty.map { it.uuid })

        val pulled = client.pull(VAULT_TABLE, existing.cursor.ifBlank { "1970-01-01T00:00:00Z" })
        var conflicts = 0
        var newestCursor = existing.cursor
        pulled.forEach { element ->
            val remote = element.jsonObject
            newestCursor = maxCursor(newestCursor, remote.string("updated_at"))
            val result = applyRemote(remote)
            conflicts += result
        }
        val remaining = db.vaultDao().getAll().count { it.dirty }
        stateDao.upsert(
            existing.copy(
                cursor = newestCursor,
                pendingCount = remaining,
                conflictCount = existing.conflictCount + conflicts,
                lastSyncEpochMillis = System.currentTimeMillis(),
                lastError = null
            )
        )
        SyncRunResult(pushed = pushed, pulled = pulled.size, conflicts = conflicts, pending = remaining)
    }

    suspend fun setPaused(paused: Boolean) {
        val current = db.syncStateDao().get(VAULT_TABLE) ?: SyncState(VAULT_TABLE)
        db.syncStateDao().upsert(current.copy(paused = paused))
    }

    suspend fun deleteCloudData() {
        client.deleteAll(VAULT_TABLE)
    }

    private suspend fun applyRemote(remote: JsonObject): Int {
        val uuid = remote.string("id")
        val updatedAt = remote.instantMillis("updated_at")
        val remoteDevice = remote.string("device_id")
        val deleted = remote["deleted_at"]?.jsonPrimitive?.contentOrNull != null
        val current = db.vaultDao().getByUuid(uuid)
        if (current != null) {
            val localCandidate = SyncCandidate(uuid, current.updatedAt, current.deviceId, current.deleted)
            val remoteCandidate = SyncCandidate(uuid, updatedAt, remoteDevice, deleted)
            if (current.dirty && remoteCandidate.winsOver(localCandidate)) {
                db.vaultDao().upsert(remote.toVaultRecord(current.id))
                return 1
            }
            if (current.dirty) return 1
            if (!remoteCandidate.winsOver(localCandidate)) return 0
            db.vaultDao().upsert(remote.toVaultRecord(current.id))
            return 0
        }
        db.vaultDao().upsert(remote.toVaultRecord())
        return 0
    }

    private suspend fun createFirstSyncBackup() {
        val marker = File(context.filesDir, "sync-first-local-backup.json")
        if (marker.exists()) return
        val bytes = MomentumBackup.exportBytes(context)
        marker.outputStream().use { it.write(bytes) }
    }
}

object SupabaseSyncScheduler {
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<SupabaseSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                java.time.Duration.ofSeconds(30)
            )
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork("momentum-sync", androidx.work.ExistingWorkPolicy.KEEP, request)
    }
}

internal fun VaultRecord.toCloudJson(deviceId: String): JsonObject = buildJsonObject {
    put("id", uuid)
    put("nonce", "\\x${nonce.toHex()}")
    put("ciphertext", "\\x${ciphertext.toHex()}")
    put("updated_at", Instant.ofEpochMilli(updatedAt).toString())
    put("deleted_at", if (deleted) Instant.ofEpochMilli(updatedAt).toString() else null)
    put("device_id", deviceId.ifBlank { this@toCloudJson.deviceId })
    put("version", 1)
}

private fun JsonObject.toVaultRecord(id: Int = 0): VaultRecord = VaultRecord(
    id = id,
    nonce = getBytea("nonce"),
    ciphertext = getBytea("ciphertext"),
    updatedAt = instantMillis("updated_at"),
    uuid = string("id"),
    deleted = this["deleted_at"]?.jsonPrimitive?.contentOrNull != null,
    dirty = false,
    deviceId = string("device_id")
)

private fun JsonObject.string(name: String): String =
    this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.instantMillis(name: String): Long =
    try {
        Instant.parse(string(name)).toEpochMilli()
    } catch (_: DateTimeParseException) {
        0L
    }

private fun JsonObject.getBytea(name: String): ByteArray {
    val value = string(name).removePrefix("\\x")
    return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
