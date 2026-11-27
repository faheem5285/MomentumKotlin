package com.faheem.habits.core.data.sync

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SyncJsonAccessorsTest {
    @Test
    fun jsonNullColumnsUseSafeDefaults() {
        val row = buildJsonObject {
            put("id", "record-id")
            put("device_id", JsonNull)
            put("updated_at", JsonNull)
            put("deleted_at", JsonNull)
            put("enabled", JsonNull)
            put("due_epoch_day", JsonNull)
            put("priority", JsonNull)
        }
        assertEquals("record-id", row.string("id"))
        assertEquals("", row.string("device_id"))
        assertEquals(0L, row.instantMillis("updated_at"))
        assertFalse(row.deleted())
        assertFalse(row.bool("enabled"))
        assertNull(row.nullableLong("due_epoch_day"))
        assertNull(row.nullableInt("priority"))
        assertEquals(0, row.int("priority"))
        assertEquals(0L, row.long("updated_at"))
    }
}
