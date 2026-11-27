package com.faheem.habits.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.faheem.habits.core.data.sync.LocalAppDataReset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AccountIsolationInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        AppDatabase.closeForInstrumentation()
        File(context.filesDir, "account-databases").deleteRecursively()
        val base = context.getDatabasePath("momentum.db")
        listOf(base, File("${base.path}-wal"), File("${base.path}-shm"), File("${base.path}-journal"))
            .forEach { if (it.exists()) it.delete() }
        context.getSharedPreferences(AppDatabase.ACCOUNT_SCOPE_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun switchingAccountsKeepsRowsIsolated() = runBlocking {
        val first = UUID.randomUUID().toString()
        val second = UUID.randomUUID().toString()

        AppDatabase.activateAccount(context, first)
        AppDatabase.get(context).clearAllTables()
        AppDatabase.get(context).habitDao().insertHabit(Habit(name = "First only"))

        AppDatabase.activateAccount(context, second)
        AppDatabase.get(context).clearAllTables()
        assertTrue(AppDatabase.get(context).habitDao().getAllForBackup().none { it.name == "First only" })
        AppDatabase.get(context).habitDao().insertHabit(Habit(name = "Second only"))

        AppDatabase.activateAccount(context, first)
        val restored = AppDatabase.get(context).habitDao().getAllForBackup()
        assertEquals(1, restored.count { it.name == "First only" })
        assertTrue(restored.none { it.name == "Second only" })
    }

    @Test
    fun resetClearsOnlyTheActiveAccount() = runBlocking {
        val first = UUID.randomUUID().toString()
        val second = UUID.randomUUID().toString()

        AppDatabase.activateAccount(context, first)
        AppDatabase.get(context).clearAllTables()
        AppDatabase.get(context).habitDao().insertHabit(Habit(name = "First only"))

        AppDatabase.activateAccount(context, second)
        AppDatabase.get(context).clearAllTables()
        AppDatabase.get(context).habitDao().insertHabit(Habit(name = "Second only"))

        AppDatabase.activateAccount(context, first)
        LocalAppDataReset.reset(context, preserveAuthSession = true)
        assertTrue(AppDatabase.get(context).habitDao().getAllForBackup().isEmpty())

        AppDatabase.activateAccount(context, second)
        val preserved = AppDatabase.get(context).habitDao().getAllForBackup()
        assertEquals(1, preserved.count { it.name == "Second only" })
    }
}
