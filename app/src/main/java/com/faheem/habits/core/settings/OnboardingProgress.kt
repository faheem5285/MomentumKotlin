package com.faheem.habits.core.settings

/**
 * Pure onboarding model. Progress is scoped to the app install (never to an
 * account) and uses tri-state [OnboardingPhase] so the app shell never flashes
 * a permission gate while DataStore is still loading.
 */
enum class OnboardingPhase { UNKNOWN, PENDING, DONE }

enum class OnboardingTask(val id: String) {
    WELCOME("welcome"),
    FIRST_HABIT("first_habit"),
    FIRST_TASK("first_task"),
    FIRST_EXPENSE("first_expense"),
    CLOUD_BACKUP("cloud_backup"),
    NOTIFICATIONS("notifications"),
    EXACT_ALARMS("exact_alarms"),
    USAGE_ACCESS("usage_access"),
    EXPLORE("explore")
}

data class OnboardingTaskState(
    val done: Boolean = false,
    val dismissed: Boolean = false,
    val snoozedUntil: Long = 0L,
    val doneAt: Long = 0L,
    val dismissedAt: Long = 0L
)

data class OnboardingProgress(
    val phase: OnboardingPhase = OnboardingPhase.UNKNOWN,
    val version: Int = CURRENT_VERSION,
    val tasks: Map<String, OnboardingTaskState> = emptyMap()
) {
    fun state(task: OnboardingTask): OnboardingTaskState = tasks[task.id] ?: OnboardingTaskState()

    companion object {
        const val CURRENT_VERSION = 1
        val Initial = OnboardingProgress()
    }
}

fun OnboardingProgress.withTask(task: OnboardingTask, state: OnboardingTaskState): OnboardingProgress =
    copy(tasks = tasks + (task.id to state), phase = OnboardingPhase.PENDING)

/** Explicit completion of the whole checklist, used by "Don't show again". */
fun OnboardingProgress.finish(): OnboardingProgress = copy(phase = OnboardingPhase.DONE)

/** Reopens the checklist from Settings without discarding completed tasks. */
fun OnboardingProgress.revisit(): OnboardingProgress = copy(phase = OnboardingPhase.PENDING)

fun OnboardingTaskState.isDue(nowMillis: Long): Boolean =
    !done && !dismissed && snoozedUntil <= nowMillis

/** Signals used to decide which cards are relevant right now. */
data class OnboardingSignals(
    val signedIn: Boolean = false,
    val habitsTotal: Int = 0,
    val openTasks: Int = 0,
    val monthSpentMinorUnits: Long = 0L,
    val permissions: PermissionStatuses = PermissionStatuses()
)

private val PRIORITY = listOf(
    OnboardingTask.WELCOME,
    OnboardingTask.FIRST_HABIT,
    OnboardingTask.FIRST_TASK,
    OnboardingTask.FIRST_EXPENSE,
    OnboardingTask.CLOUD_BACKUP,
    OnboardingTask.NOTIFICATIONS,
    OnboardingTask.EXACT_ALARMS,
    OnboardingTask.USAGE_ACCESS,
    OnboardingTask.EXPLORE
)

/** At most two cards are shown at once so a fresh install is not overwhelming. */
fun visibleOnboardingTasks(
    progress: OnboardingProgress,
    signals: OnboardingSignals,
    nowMillis: Long
): List<OnboardingTask> {
    if (progress.phase == OnboardingPhase.DONE || progress.phase == OnboardingPhase.UNKNOWN) return emptyList()
    return PRIORITY.filter { task ->
        task.isRelevant(signals) && progress.state(task).isDue(nowMillis)
    }.take(2)
}

private fun OnboardingTask.isRelevant(signals: OnboardingSignals): Boolean = when (this) {
    OnboardingTask.WELCOME, OnboardingTask.EXPLORE -> true
    OnboardingTask.FIRST_HABIT -> signals.habitsTotal == 0
    OnboardingTask.FIRST_TASK -> signals.openTasks == 0
    OnboardingTask.FIRST_EXPENSE -> signals.monthSpentMinorUnits <= 0L
    OnboardingTask.CLOUD_BACKUP -> !signals.signedIn
    OnboardingTask.NOTIFICATIONS -> signals.permissions.status(PermissionKind.NOTIFICATIONS) == PermissionStatus.DENIED
    OnboardingTask.EXACT_ALARMS -> signals.permissions.status(PermissionKind.EXACT_ALARMS) == PermissionStatus.DENIED
    OnboardingTask.USAGE_ACCESS -> signals.permissions.status(PermissionKind.USAGE_ACCESS) == PermissionStatus.DENIED
}

/** Exact signed-out wording required by the modernization checklist. */
object OnboardingCopy {
    const val LOCAL_DATA = "Local data is still available on this device"
    const val NO_CLOUD_BACKUP =
        "No cloud backup — local data may not be recoverable if device lost/app data erased"
    const val SYNC_UNAVAILABLE = "Sync unavailable — latest changes may not yet be synchronized"
    const val BACKUP_FAILURE = "Backup failure — latest backup could not be completed."
}
