package com.faheem.habits.core.data.sync

import com.faheem.habits.core.data.VaultRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncModelsTest {
    @Test
    fun newestTimestampWinsAndDeviceBreaksTies() {
        val older = SyncCandidate("id", 10, "device-a", false)
        val newer = SyncCandidate("id", 11, "device-a", false)
        assertTrue(newer.winsOver(older))
        assertFalse(older.winsOver(newer))
        assertTrue(SyncCandidate("id", 11, "device-b", false).winsOver(newer))
    }

    @Test
    fun cursorRoundTripsAndReadsLegacyTimestamp() {
        val cursor = SyncCursor("2026-01-02T03:04:05Z", "record-id")
        assertEquals(cursor, SyncCursor.decode(cursor.encode()))
        assertEquals(SyncCursor("2026-01-02T03:04:05Z"), SyncCursor.decode("2026-01-02T03:04:05Z"))
    }

    @Test
    fun retriesOnlyTransientServerStatuses() {
        assertTrue(isRetryableSyncStatus(408))
        assertTrue(isRetryableSyncStatus(429))
        assertTrue(isRetryableSyncStatus(503))
        assertFalse(isRetryableSyncStatus(401))
        assertFalse(isRetryableSyncStatus(403))
        assertFalse(isRetryableSyncStatus(422))
    }

    @Test
    fun vaultCloudPayloadContainsCiphertextOnly() {
        val payload = VaultRecord(
            nonce = byteArrayOf(1, 2),
            ciphertext = byteArrayOf(3, 4),
            updatedAt = 1,
            uuid = "vault-id"
        ).toCloudJson("device")
        val serialized = payload.toString()
        assertTrue(serialized.contains("ciphertext"))
        assertTrue(serialized.contains("nonce"))
        assertFalse(serialized.contains("password"))
        assertFalse(serialized.contains("username"))
    }
}
