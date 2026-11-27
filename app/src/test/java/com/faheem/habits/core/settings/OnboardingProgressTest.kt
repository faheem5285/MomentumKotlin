package com.faheem.habits.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingProgressTest {
    private val now = 1_700_000_000_000L

    @Test
    fun freshInstallShowsFirstTwoRelevantCards() {
        val visible = visibleOnboardingTasks(
            OnboardingProgress(phase = OnboardingPhase.PENDING),
            OnboardingSignals(signedIn = false),
            now
        )
        assertEquals(
            listOf(OnboardingTask.WELCOME, OnboardingTask.FIRST_HABIT),
            visible
        )
    }

    @Test
    fun unknownAndDonePhasesShowNothing() {
        assertTrue(
            visibleOnboardingTasks(
                OnboardingProgress(phase = OnboardingPhase.UNKNOWN),
                OnboardingSignals(),
                now
            ).isEmpty()
        )
        assertTrue(
            visibleOnboardingTasks(
                OnboardingProgress(phase = OnboardingPhase.DONE),
                OnboardingSignals(),
                now
            ).isEmpty()
        )
    }

    @Test
    fun completedAndDismissedTasksAreHidden() {
        val doneWelcome = OnboardingProgress.Initial.copy(
            phase = OnboardingPhase.PENDING,
            tasks = mapOf(
                OnboardingTask.WELCOME.id to OnboardingTaskState(done = true),
                OnboardingTask.FIRST_HABIT.id to OnboardingTaskState(dismissed = true)
            )
        )
        assertEquals(
            listOf(OnboardingTask.FIRST_TASK, OnboardingTask.FIRST_EXPENSE),
            visibleOnboardingTasks(doneWelcome, OnboardingSignals(), now)
        )
    }

    @Test
    fun snoozedTaskReturnsWhenDueAndStaysHiddenWhileSnoozed() {
        val progress = OnboardingProgress.Initial.copy(
            phase = OnboardingPhase.PENDING,
            tasks = mapOf(
                OnboardingTask.WELCOME.id to OnboardingTaskState(snoozedUntil = now + 10_000L)
            )
        )
        assertFalse(OnboardingTask.WELCOME in visibleOnboardingTasks(progress, OnboardingSignals(), now))
        assertTrue(
            OnboardingTask.WELCOME in visibleOnboardingTasks(
                progress,
                OnboardingSignals(),
                now + 10_001L
            )
        )
    }

    @Test
    fun relevanceRespondsToUserDataAndSignIn() {
        val signals = OnboardingSignals(
            signedIn = true,
            habitsTotal = 3,
            openTasks = 2,
            monthSpentMinorUnits = 5_000L,
            permissions = PermissionStatuses(
                mapOf(
                    PermissionKind.NOTIFICATIONS to PermissionStatus.DENIED
                )
            )
        )
        assertEquals(
            listOf(OnboardingTask.WELCOME, OnboardingTask.NOTIFICATIONS),
            visibleOnboardingTasks(OnboardingProgress.Initial.copy(phase = OnboardingPhase.PENDING), signals, now)
        )
    }

    @Test
    fun finishAndRevisitPreserveTaskHistory() {
        val original = OnboardingProgress.Initial.copy(
            phase = OnboardingPhase.PENDING,
            tasks = mapOf(OnboardingTask.WELCOME.id to OnboardingTaskState(done = true, doneAt = 42L))
        )
        val finished = original.finish()
        assertEquals(OnboardingPhase.DONE, finished.phase)
        assertEquals(42L, finished.state(OnboardingTask.WELCOME).doneAt)

        val revisited = finished.revisit()
        assertEquals(OnboardingPhase.PENDING, revisited.phase)
        assertTrue(revisited.state(OnboardingTask.WELCOME).done)
    }

    @Test
    fun withTaskMarksChecklistPending() {
        val updated = OnboardingProgress.Initial.withTask(
            OnboardingTask.WELCOME,
            OnboardingTaskState(done = true, doneAt = now)
        )
        assertEquals(OnboardingPhase.PENDING, updated.phase)
        assertTrue(updated.state(OnboardingTask.WELCOME).done)
    }

    @Test
    fun grantedPermissionsHidePermissionCards() {
        val statuses = PermissionStatuses(
            PermissionKind.entries.associateWith { PermissionStatus.GRANTED }
        )
        val signals = OnboardingSignals(
            signedIn = true,
            habitsTotal = 1,
            openTasks = 1,
            monthSpentMinorUnits = 5_000L,
            permissions = statuses
        )
        assertEquals(
            listOf(OnboardingTask.WELCOME, OnboardingTask.EXPLORE),
            visibleOnboardingTasks(OnboardingProgress.Initial.copy(phase = OnboardingPhase.PENDING), signals, now)
        )
    }

    @Test
    fun permissionSummaryCountsOnlySupportedChecks() {
        val statuses = PermissionStatuses(
            mapOf(
                PermissionKind.NOTIFICATIONS to PermissionStatus.GRANTED,
                PermissionKind.EXACT_ALARMS to PermissionStatus.DENIED,
                PermissionKind.USAGE_ACCESS to PermissionStatus.GRANTED,
                PermissionKind.AUTO_START to PermissionStatus.UNSUPPORTED
            )
        )
        assertEquals("2 of 3 enabled", statuses.enabledSummary)
        assertTrue(statuses.needsAttention)
        assertEquals(PermissionStatus.UNSUPPORTED, statuses.status(PermissionKind.OVERLAY))
    }

    @Test
    fun signedOutCopyIsExact() {
        assertEquals("Local data is still available on this device", OnboardingCopy.LOCAL_DATA)
        assertEquals(
            "No cloud backup — local data may not be recoverable if device lost/app data erased",
            OnboardingCopy.NO_CLOUD_BACKUP
        )
        assertEquals(
            "Sync unavailable — latest changes may not yet be synchronized",
            OnboardingCopy.SYNC_UNAVAILABLE
        )
        assertEquals(
            "Backup failure — latest backup could not be completed.",
            OnboardingCopy.BACKUP_FAILURE
        )
    }
}
