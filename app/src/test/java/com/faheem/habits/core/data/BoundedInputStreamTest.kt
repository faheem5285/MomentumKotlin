package com.faheem.habits.core.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class BoundedInputStreamTest {
    @Test
    fun readsExactlyTheConfiguredBudget() {
        val source = ByteArrayInputStream("abcdef".toByteArray())
        val limited = BoundedInputStream(source, 3)
        val first = ByteArray(2)
        assertEquals(2, limited.read(first))
        val rest = ByteArray(4)
        assertEquals(1, limited.read(rest))
        assertArrayEquals("ab".toByteArray(), first.copyOf(2))
        assertEquals('c'.code, rest[0].toInt())
    }

    @Test
    fun refusesToReadPastTheBudget() {
        val limited = BoundedInputStream(ByteArrayInputStream("abcdef".toByteArray()), 2)
        assertEquals('a'.code, limited.read())
        assertEquals('b'.code, limited.read())
        assertThrows(InputStreamLimitExceededException::class.java) { limited.read() }
    }

    @Test
    fun exactLengthStillReturnsEndOfStream() {
        val limited = BoundedInputStream(ByteArrayInputStream("ab".toByteArray()), 2)
        val buffer = ByteArray(8)
        assertEquals(2, limited.read(buffer))
        assertEquals(-1, limited.read(buffer))
    }

    @Test
    fun skipRespectsTheSameBudget() {
        val limited = BoundedInputStream(ByteArrayInputStream("abcdef".toByteArray()), 3)
        assertEquals(2L, limited.skip(2))
        assertEquals('c'.code, limited.read())
        assertThrows(InputStreamLimitExceededException::class.java) { limited.skip(1) }
    }
}
