package com.faheem.habits.app

import com.faheem.habits.core.data.supabase.SessionRestoreResult

/**
 * Pure UI session state for the app shell.
 *
 * Cloud sign-in is optional: every phase keeps the last selected local database
 * usable. `requiresAccountAttention` routes to the Profile screen rather than
 * blocking the app, so a signed-out user can still use all local features.
 */
enum class AppSessionPhase { RESTORING, READY }

data class AppSessionState(
    val phase: AppSessionPhase = AppSessionPhase.RESTORING,
    val cloudSignedIn: Boolean = false,
    val requiresAccountAttention: Boolean = false,
    val notice: String? = null
) {
    companion object {
        val Initial = AppSessionState()
    }
}

sealed interface AppSessionEvent {
    data class RestoreFinished(
        val result: SessionRestoreResult,
        val wipeApplied: Boolean = false,
        val notice: String? = null
    ) : AppSessionEvent

    data object WipeApplied : AppSessionEvent

    data class SignedOut(val keepLocalData: Boolean) : AppSessionEvent

    data object NoticeDismissed : AppSessionEvent
}

private const val WIPE_NOTICE =
    "An administrator reset app data. You can sign in to sync again or continue offline."
private const val OFFLINE_NOTICE =
    "Offline: your local records are available. Cloud sync will resume when connected."

fun reduceAppSession(state: AppSessionState, event: AppSessionEvent): AppSessionState = when (event) {
    is AppSessionEvent.RestoreFinished -> when (event.result) {
        SessionRestoreResult.AUTHENTICATED -> if (event.wipeApplied) {
            AppSessionState(
                phase = AppSessionPhase.READY,
                requiresAccountAttention = true,
                notice = event.notice ?: WIPE_NOTICE
            )
        } else {
            AppSessionState(
                phase = AppSessionPhase.READY,
                cloudSignedIn = true,
                notice = event.notice
            )
        }

        SessionRestoreResult.NO_SESSION -> AppSessionState(
            phase = AppSessionPhase.READY,
            notice = event.notice
        )

        SessionRestoreResult.TEMPORARY_FAILURE -> AppSessionState(
            phase = AppSessionPhase.READY,
            notice = event.notice ?: OFFLINE_NOTICE
        )
    }

    AppSessionEvent.WipeApplied -> AppSessionState(
        phase = AppSessionPhase.READY,
        requiresAccountAttention = true,
        notice = WIPE_NOTICE
    )

    is AppSessionEvent.SignedOut -> AppSessionState(
        phase = AppSessionPhase.READY,
        notice = if (event.keepLocalData) {
            "You signed out. Local records are still available on this device."
        } else {
            "You signed out and reset local data. Your cloud account and records remain."
        }
    )

    AppSessionEvent.NoticeDismissed -> state.copy(notice = null)
}
