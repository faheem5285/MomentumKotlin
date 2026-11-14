package com.faheem.habits.core.data.sync

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
    val deleted: Boolean
)

/**
 * Deterministic last-write-wins ordering.
 *
 * A tombstone wins an exact timestamp tie so an equal-time active copy cannot
 * resurrect a record that another device has deleted. Device ID is the final
 * stable tie-breaker for otherwise equivalent candidates.
 */
fun SyncCandidate.winsOver(other: SyncCandidate): Boolean =
    updatedAt > other.updatedAt ||
        (updatedAt == other.updatedAt && deleted && !other.deleted) ||
        (updatedAt == other.updatedAt && deleted == other.deleted && deviceId > other.deviceId)

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
