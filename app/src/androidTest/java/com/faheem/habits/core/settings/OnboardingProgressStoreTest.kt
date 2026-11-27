package com.faheem.habits.core.settings

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Device-side DataStore checks; compiled here, not executed without a device. */
@RunWith(AndroidJUnit4::class)
class OnboardingProgressStoreTest {
    private lateinit var store: OnboardingProgressStore

    @Before
    fun setUp() {
        store = OnboardingProgressStore(ApplicationProvider.getApplicationContext())
        runBlocking { store.clearProgress() }
    }

    @After
    fun tearDown() = runBlocking { store.clearProgress() }

    @Test
    fun pendingByDefaultAndPersistsCompletion() = runBlocking {
        assertEquals(OnboardingPhase.PENDING, store.progress.first().phase)
        store.markDone(OnboardingTask.WELCOME, 10L)
        val progress = store.progress.first()
        assertTrue(progress.state(OnboardingTask.WELCOME).done)
        assertEquals(10L, progress.state(OnboardingTask.WELCOME).doneAt)
    }

    @Test
    fun dismissAndSnoozePersistIndependently() = runBlocking {
        store.dismiss(OnboardingTask.FIRST_HABIT, 20L)
        store.snooze(OnboardingTask.FIRST_TASK, 30L)
        val progress = store.progress.first()
        assertTrue(progress.state(OnboardingTask.FIRST_HABIT).dismissed)
        assertFalse(progress.state(OnboardingTask.FIRST_TASK).dismissed)
        assertEquals(30L, progress.state(OnboardingTask.FIRST_TASK).snoozedUntil)
    }

    @Test
    fun finishAndRevisitTogglePhaseWithoutLosingTasks() = runBlocking {
        store.markDone(OnboardingTask.WELCOME, 40L)
        store.finish()
        assertEquals(OnboardingPhase.DONE, store.progress.first().phase)
        store.revisit()
        val revisited = store.progress.first()
        assertEquals(OnboardingPhase.PENDING, revisited.phase)
        assertTrue(revisited.state(OnboardingTask.WELCOME).done)
    }
}
