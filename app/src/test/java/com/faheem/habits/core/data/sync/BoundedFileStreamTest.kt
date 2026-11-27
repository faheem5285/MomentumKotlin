package com.faheem.habits.core.data.sync

import com.faheem.habits.core.data.supabase.BoundedInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class BoundedFileStreamTest {
    @Test
    fun readBoundedFileBytesAcceptsExactRemainingAllowance() {
        val bytes = "0123".toByteArray()

        val bounded = ByteArrayInputStream(bytes).readBoundedFileBytes("Workbook", remainingBytes = 4)

        assertEquals("0123", String(bounded))
    }

    @Test
    fun readBoundedFileBytesRejectsSmallOverflow() {
        val error = runCatching {
            ByteArrayInputStream("0123456789".toByteArray()).readBoundedFileBytes("Workbook", remainingBytes = 4)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertNotNull(error)
    }

    @Test
    fun readBoundedFileBytesRejectsGlobalCapOverflow() {
        val bytes = ByteArray(MAX_FILE_TRANSFER_BYTES + 1)

        val error = runCatching {
            ByteArrayInputStream(bytes).readBoundedFileBytes("Workbook")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("10 MB"))
    }

    @Test
    fun readBoundedTextRejectsUnboundedInput() {
        val error = runCatching { UnboundedAsciiStream().readBoundedText("Import") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("10 MB"))
    }

    @Test
    fun uploadStreamThrowsOnceItsCapIsConsumed() {
        val stream = BoundedInputStream(ByteArrayInputStream(ByteArray(64)), 4)
        val first = stream.read(ByteArray(8))

        assertEquals(4, first)
        val error = runCatching { stream.read() }.exceptionOrNull()
        assertTrue(error is IOException)
    }
}

private class UnboundedAsciiStream : InputStream() {
    override fun read(): Int = 'x'.code

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        buffer.fill('x'.code.toByte(), offset, offset + length)
        return length
    }
}
