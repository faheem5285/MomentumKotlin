package com.faheem.habits.core.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageTombstonesTest {
    @Test
    fun onlySyncedTombstonesPlanObjectDeletion() {
        val candidates = listOf(
            StorageTombstoneCandidate("momentum-files", "file-a", deleted = true, dirty = false),
            StorageTombstoneCandidate("momentum-files", "file-dirty", deleted = true, dirty = true),
            StorageTombstoneCandidate("momentum-files", "file-live", deleted = false, dirty = false),
            StorageTombstoneCandidate("momentum-files", "file-a", deleted = true, dirty = false),
            StorageTombstoneCandidate("momentum-files", "", deleted = true, dirty = false)
        )
        assertEquals(
            listOf("momentum-files" to "file-a"),
            planStorageTombstoneDeletion(candidates)
        )
    }

    @Test
    fun deletionPlanIsBounded() {
        val candidates = (0 until 20).map { index ->
            StorageTombstoneCandidate("momentum-files", "file-$index", deleted = true, dirty = false)
        }
        assertEquals(3, planStorageTombstoneDeletion(candidates, maxPaths = 3).size)
        assertTrue(planStorageTombstoneDeletion(candidates, maxPaths = -1).isEmpty())
    }
}
