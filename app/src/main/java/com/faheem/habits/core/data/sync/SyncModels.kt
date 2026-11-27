package com.faheem.habits.core.data.sync

import com.faheem.habits.core.data.SyncState
import kotlinx.serialization.Serializable

const val SYNC_STATUS_TABLE = "momentum_sync"

@Serializable
data class SyncEnvelope(
    val id: String,
    val updatedAt: Long,
    val deviceId: String,
    val deleted: Boolean
)

data class SyncCandidate(
    val id: String,
    val updatedAt: Long,
    val deviceId: String,
    val deleted: Boolean,
    val dirty: Boolean = false
)

fun SyncCandidate.winsOver(other: SyncCandidate): Boolean =
    updatedAt > other.updatedAt ||
        (updatedAt == other.updatedAt && deviceId > other.deviceId)

/**
 * Decides whether a remote row should replace the local row.
 * Local tombstones and unsynced local edits are never overwritten by a normal remote row.
 * A remote tombstone wins unless the local row is a strictly newer unsynced edit.
 */
fun shouldApplyRemote(local: SyncCandidate?, remote: SyncCandidate): Boolean {
    if (local == null) return true
    return when {
        local.deleted && !remote.deleted -> false
        local.dirty && !remote.deleted -> false
        local.dirty && remote.deleted -> local.updatedAt <= remote.updatedAt
        remote.deleted && !local.deleted -> true
        else -> remote.winsOver(local)
    }
}

private fun sameRevision(a: SyncCandidate, b: SyncCandidate): Boolean =
    a.updatedAt == b.updatedAt && a.deviceId == b.deviceId && a.deleted == b.deleted

/** Result of reconciling one pulled row; conflicts are counted without overwriting local edits. */
data class SyncDecision(
    val applyRemote: Boolean,
    val conflict: Boolean = false
)

/** A conflict is a divergent remote row meeting an unsynced local edit or tombstone. */
fun decideRemote(local: SyncCandidate?, remote: SyncCandidate): SyncDecision {
    if (local == null) return SyncDecision(applyRemote = true)
    val applyRemote = shouldApplyRemote(local, remote)
    return SyncDecision(
        applyRemote = applyRemote,
        conflict = !applyRemote && local.dirty && !sameRevision(local, remote)
    )
}

data class SyncRunResult(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val conflicts: Int = 0,
    val pending: Int = 0,
    val projectWipeInProgress: Boolean = false
)

data class SyncCursor(val updatedAt: String, val id: String = "") {
    fun encode(): String = "$updatedAt|$id"

    companion object {
        fun decode(value: String): SyncCursor {
            val separator = value.indexOf('|')
            return if (separator < 0) SyncCursor(value) else {
                SyncCursor(value.substring(0, separator), value.substring(separator + 1))
            }
        }
    }
}

fun isRetryableSyncStatus(statusCode: Int): Boolean =
    statusCode == 408 || statusCode == 429 || statusCode in 500..599

/** UI-facing counts derived only from real cloud tables, never from the synthetic status row. */
data class SyncStatusSummary(
    val pending: Int,
    val conflicts: Int,
    val trackedTables: Int,
    val errorTables: Int,
    val hasSynced: Boolean,
    val running: Boolean,
    val waiting: Boolean,
    val needsAttention: Boolean
)

fun summarizeSyncStatus(
    states: List<SyncState>,
    failedTables: Int = 0,
    workRunning: Boolean = false,
    workQueued: Boolean = false,
    workFailed: Boolean = false
): SyncStatusSummary {
    val tracked = states.filter { it.tableName != SYNC_STATUS_TABLE }
    val pending = tracked.sumOf { it.pendingCount }
    val conflicts = tracked.sumOf { it.conflictCount }
    val errorTables = tracked.count { !it.lastError.isNullOrBlank() }
    val hasSynced = states.any { it.lastSyncEpochMillis > 0 }
    val waiting = workQueued || pending > 0
    return SyncStatusSummary(
        pending = pending,
        conflicts = conflicts,
        trackedTables = tracked.size,
        errorTables = errorTables,
        hasSynced = hasSynced,
        running = workRunning,
        waiting = waiting,
        needsAttention = workFailed || failedTables > 0 || errorTables > 0 || conflicts > 0
    )
}
