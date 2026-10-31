package com.faheem.habits.core.data.sync

import kotlinx.serialization.Serializable

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

fun SyncCandidate.winsOver(other: SyncCandidate): Boolean =
    updatedAt > other.updatedAt ||
        (updatedAt == other.updatedAt && deviceId > other.deviceId)

data class SyncRunResult(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val conflicts: Int = 0,
    val pending: Int = 0
)

