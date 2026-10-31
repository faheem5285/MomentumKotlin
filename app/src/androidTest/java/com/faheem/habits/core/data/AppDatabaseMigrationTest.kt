package com.faheem.habits.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private val migrations = arrayOf(
        AppDatabase.MIGRATION_3_4,
        AppDatabase.MIGRATION_4_5,
        AppDatabase.MIGRATION_5_6,
        AppDatabase.MIGRATION_6_7,
        AppDatabase.MIGRATION_7_8,
        AppDatabase.MIGRATION_8_9,
        AppDatabase.MIGRATION_9_10,
        AppDatabase.MIGRATION_10_11,
        AppDatabase.MIGRATION_11_12,
        AppDatabase.MIGRATION_12_13
    )

    @Test
    fun everyMigrationStepValidatesIndependently() {
        val steps = listOf(
            3 to 4, 4 to 5, 5 to 6, 6 to 7, 7 to 8, 8 to 9, 9 to 10, 10 to 11, 11 to 12, 12 to 13
        )
        steps.forEach { (from, to) ->
            val dbName = "migration_step_${from}_$to"
            helper.createDatabase(dbName, from).close()
            helper.runMigrationsAndValidate(
                dbName,
                to,
                true,
                migrations.single { it.startVersion == from && it.endVersion == to }
            ).close()
        }
    }

    @Test
    fun fullMigrationChainFromVersion3Validates() {
        val dbName = "migration_chain"
        helper.createDatabase(dbName, 3).close()
        helper.runMigrationsAndValidate(dbName, 13, true, *migrations).close()
    }

    @Test
    fun fullChainPreservesRowsAndConvertsMoneyToMinorUnits() {
        val dbName = "migration_full_chain"
        helper.createDatabase(dbName, 3).apply {
            execSQL("INSERT INTO habits (id, name) VALUES (7, 'Keep me')")
            execSQL("INSERT INTO expenses (id, title, amount, category, epochDay) VALUES (4, 'Half cent', 1.005, 'Food', 20000)")
            execSQL("INSERT INTO expenses (id, title, amount, category, epochDay) VALUES (5, 'Whole', 19.99, 'Bills', 20001)")
            execSQL("INSERT INTO loans (id, person, amount, lent, note, epochDay) VALUES (9, 'Friend', 5.50, 1, '', 20002)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 13, true, *migrations)
        db.query("SELECT id, name FROM habits WHERE id = 7").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Keep me", cursor.getString(1))
        }
        db.query("SELECT amountMinorUnits FROM expenses WHERE id = 4").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(101L, cursor.getLong(0))
        }
        db.query("SELECT amountMinorUnits FROM expenses WHERE id = 5").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1999L, cursor.getLong(0))
        }
        db.query("SELECT amountMinorUnits, uuid, updatedAt FROM loans WHERE id = 9").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(550L, cursor.getLong(0))
            assertTrue(cursor.getString(1).isNotBlank())
            assertTrue(cursor.getLong(2) > 0L)
        }
        db.close()
    }

    @Test
    fun uuidBackfillCoversEverySyncReadyTable() {
        val dbName = "uuid_backfill"
        helper.createDatabase(dbName, 9).apply {
            execSQL("INSERT INTO habits (id,name,description,icon,colorIndex,timeOfDay,daysMask,archived,createdEpochDay) VALUES (1,'Habit','','star',0,0,127,0,20000)")
            execSQL("INSERT INTO completions (habitId,epochDay) VALUES (1,20000)")
            execSQL("INSERT INTO habit_reminders (id,habitId,hour,minute,daysMask,enabled,dateEpochDay) VALUES (2,1,9,0,127,1,NULL)")
            execSQL("INSERT INTO expenses (id,title,amount,category,epochDay) VALUES (3,'Lunch',2.5,'Food',20000)")
            execSQL("INSERT INTO loans (id,person,amount,lent,note,epochDay,settled) VALUES (4,'Friend',3.5,1,'',20000,0)")
            execSQL("INSERT INTO library_files (id,name,uri,mime,addedEpochDay) VALUES (5,'File','content://example/item','text/plain',20000)")
            execSQL("INSERT INTO todo_items (id,title,notes,dueEpochDay,completed,category,priority,reminderHour,reminderMinute) VALUES (6,'Task','',20000,0,'Personal',1,NULL,NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 13, true, *migrations)
        listOf("habits", "completions", "habit_reminders", "expenses", "loans", "library_files", "todo_items").forEach { table ->
            db.query("SELECT uuid,updatedAt FROM `$table`").use { cursor ->
                assertTrue("Expected backfilled row in $table", cursor.moveToFirst())
                assertTrue(cursor.getString(0).isNotBlank())
                assertTrue(cursor.getLong(1) > 0L)
            }
        }
        db.close()
    }
}
