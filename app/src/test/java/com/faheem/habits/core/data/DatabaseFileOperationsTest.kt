package com.faheem.habits.core.data

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseFileOperationsTest {
    @Test
    fun atomicCopyReplacesDestinationWithCompleteSource() {
        val directory = Files.createTempDirectory("momentum-db-copy").toFile()
        try {
            val source = File(directory, "source.db").apply { writeText("complete database bytes") }
            val destination = File(directory, "destination.db").apply { writeText("old database bytes") }

            AppDatabase.copyFileAtomically(source, destination)

            assertEquals("complete database bytes", destination.readText())
            assertEquals(setOf("source.db", "destination.db"), directory.listFiles()?.map { it.name }?.toSet())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedCopyKeepsExistingDestinationAndRemovesTemporaryFile() {
        val directory = Files.createTempDirectory("momentum-db-copy-failure").toFile()
        try {
            val missingSource = File(directory, "missing.db")
            val destination = File(directory, "destination.db").apply { writeText("keep existing data") }

            try {
                AppDatabase.copyFileAtomically(missingSource, destination)
                throw AssertionError("Expected the copy to fail when its source is missing.")
            } catch (_: IOException) {
                // The original destination must remain intact after a failed copy.
            }

            assertEquals("keep existing data", destination.readText())
            assertEquals(setOf("destination.db"), directory.listFiles()?.map { it.name }?.toSet())
        } finally {
            directory.deleteRecursively()
        }
    }
}
