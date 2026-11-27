package com.faheem.habits.core.data

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** Raised when a caller tries to read past the configured byte budget. */
class InputStreamLimitExceededException : IOException("Input exceeded the allowed size")

/**
 * InputStream that refuses to return more than [maxBytes] bytes. It never reads
 * past the limit except for a one-byte EOF probe, so corrupt or hostile SAF,
 * network and archive streams cannot force an unbounded allocation.
 */
class BoundedInputStream(
    source: InputStream,
    private val maxBytes: Long
) : FilterInputStream(source) {
    private var remaining: Long = maxBytes.coerceAtLeast(0L)

    override fun read(): Int {
        if (remaining == 0L) return rejectOrEof()
        val value = super.read()
        if (value >= 0) remaining--
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (remaining == 0L) return rejectOrEof()
        val allowed = minOf(len.toLong(), remaining).toInt()
        val count = super.read(b, off, allowed)
        if (count > 0) remaining -= count.toLong()
        return count
    }

    override fun skip(n: Long): Long {
        if (n <= 0L) return 0L
        if (remaining == 0L) {
            if (super.read() != -1) throw InputStreamLimitExceededException()
            return 0L
        }
        val count = super.skip(minOf(n, remaining))
        if (count > 0) remaining -= count
        return count
    }

    private fun rejectOrEof(): Int {
        if (super.read() != -1) throw InputStreamLimitExceededException()
        return -1
    }
}
