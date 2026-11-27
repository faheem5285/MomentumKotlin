package com.faheem.habits.features.files

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.Contact
import com.faheem.habits.core.data.FileItem
import com.faheem.habits.core.data.sync.MAX_FILE_TRANSFER_BYTES
import com.faheem.habits.core.data.today
import com.faheem.habits.core.ui.MomentumTheme
import com.faheem.habits.features.contacts.ContactImportCounts
import com.faheem.habits.features.contacts.ContactImportPlan
import com.faheem.habits.features.contacts.persistContactImportPlan
import com.faheem.habits.features.files.data.FileAddResult
import com.faheem.habits.features.files.data.FileLinkDraft
import com.faheem.habits.features.files.data.FileRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Device-side M8 scenarios. These tests are compiled here, not executed without a device/emulator. */
@RunWith(AndroidJUnit4::class)
class M8ContactsAndFilesInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun deniedSafUriIsNotPersisted() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = FileRepository(db.fileDao(), context)

            val result = repository.add(
                FileLinkDraft(
                    name = "Blocked.txt",
                    uri = "content://com.invalid.provider/no-access",
                    mime = "text/plain",
                    sizeBytes = 12L,
                    persisted = true
                )
            )

            assertEquals(FileAddResult.ACCESS_DENIED, result)
            assertTrue(db.fileDao().getAllForBackup().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun knownOversizedFileIsRejectedBeforeUriCheck() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = FileRepository(db.fileDao(), context)

            val result = repository.add(
                FileLinkDraft(
                    name = "Huge.bin",
                    uri = "content://com.invalid.provider/huge",
                    mime = "application/octet-stream",
                    sizeBytes = MAX_FILE_TRANSFER_BYTES + 1L,
                    persisted = true
                )
            )

            assertEquals(FileAddResult.TOO_LARGE, result)
            assertTrue(db.fileDao().getAllForBackup().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun missingFileCanBeReconnectedToReadableUri() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val temporary = File(context.cacheDir, "m8-regrant.txt")
        try {
            val dao = db.fileDao()
            val repository = FileRepository(dao, context)
            val missing = FileItem(
                name = "Notes.txt",
                uri = "content://com.invalid.provider/missing",
                mime = "text/plain",
                addedEpochDay = today(),
                uuid = "m8-file-uuid",
                missing = true
            )
            dao.insert(missing)
            temporary.writeText("re-granted")

            val result = repository.reconnect(
                missing,
                FileLinkDraft(
                    name = "Notes.txt",
                    uri = Uri.fromFile(temporary).toString(),
                    mime = "text/plain",
                    persisted = true
                )
            )

            assertEquals(FileAddResult.LINKED, result)
            val updated = dao.getByUuid("m8-file-uuid")!!
            assertFalse(updated.missing)
            assertTrue(updated.dirty)
            assertTrue(updated.lastAccessedEpochDay > 0)
        } finally {
            temporary.delete()
            db.close()
        }
    }

    @Test
    fun failedContactImportRollsBackWholeBatch() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.contactDao()
            val duplicateUuid = "duplicate-uuid"
            val plan = ContactImportPlan(
                contacts = listOf(
                    Contact(name = "First", uuid = duplicateUuid),
                    Contact(name = "Second", uuid = duplicateUuid)
                ),
                counts = ContactImportCounts(imported = 2, skippedContacts = 0, duplicateNumbers = 0)
            )

            val failure = runCatching { persistContactImportPlan(db, dao, plan) }.exceptionOrNull()

            assertNotNull(failure)
            assertTrue(dao.getAllForBackup().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun deniedFileStatusUsesExactCopyAndDismissAction() {
        var dismissed = false
        composeRule.setContent {
            MomentumTheme(reduceEffects = true) {
                FileAddStatusCard(
                    result = FileAddResult.ACCESS_DENIED,
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeRule.onNodeWithText(
            "Access was denied. The file was not linked; select it again to grant access."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").performClick()
        assertTrue(dismissed)
    }
}
