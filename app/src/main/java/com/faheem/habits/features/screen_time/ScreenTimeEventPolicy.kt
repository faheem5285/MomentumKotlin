package com.faheem.habits.features.screen_time

/** Pure policy for the optional accessibility counter and Screen Time permission copy. */

internal enum class ScreenTimeAccessibilityEventKind {
    WINDOW_STATE_CHANGED,
    VIEW_SCROLLED,
    OTHER
}

internal enum class ScreenTimeOverlayAction { SHOW, HIDE_DELAYED, IGNORE }

internal data class ScrollCountDecision(
    val shouldCount: Boolean,
    val lastCountedEventMs: Long
)

internal data class PermissionCopy(val benefit: String, val whenDenied: String)

internal const val INSTAGRAM_SCROLL_DEBOUNCE_MS = 900L

internal const val USAGE_ACCESS_LIMITATION_COPY =
    "Usage access is off, so Zylo cannot measure current screen time. Statistics, limits and " +
        "alarms are paused until you turn it back on; saved settings are kept."

internal const val USAGE_ACCESS_REVOCATION_COPY =
    "Usage access was revoked or is unavailable. Statistics, limits and alarms stay paused " +
        "until you turn it back on; Zylo keeps your saved settings."

internal const val SCREEN_ALARM_HELPER =
    "Alert when screen time reaches this amount. Requires Usage access; alarms pause if it is revoked."

internal const val REELS_PERMISSION_LIMITATION_COPY =
    "Counts Instagram scroll events each day, not verified Reels plays. Turning off " +
        "Accessibility stops counting; previously saved counts are kept. Turning off Usage " +
        "access pauses Screen Time statistics, limits and alarms, but does not stop this " +
        "optional counter."

private const val ALARM_ENABLED_LABEL = "Set alarm"
private const val ALARM_DISABLED_LABEL = "Set alarm (needs Usage access)"

internal fun scrollCountDecision(
    kind: ScreenTimeAccessibilityEventKind,
    packageName: String?,
    targetPackage: String,
    eventTimeMs: Long,
    lastCountedEventMs: Long,
    debounceMs: Long = INSTAGRAM_SCROLL_DEBOUNCE_MS
): ScrollCountDecision {
    if (kind != ScreenTimeAccessibilityEventKind.VIEW_SCROLLED ||
        !packageName.equals(targetPackage, ignoreCase = true)
    ) {
        return ScrollCountDecision(false, lastCountedEventMs)
    }
    if (lastCountedEventMs > 0L &&
        (eventTimeMs < lastCountedEventMs || eventTimeMs - lastCountedEventMs < debounceMs)
    ) {
        return ScrollCountDecision(false, lastCountedEventMs)
    }
    return ScrollCountDecision(true, eventTimeMs)
}

internal fun overlayAction(
    kind: ScreenTimeAccessibilityEventKind,
    packageName: String?,
    targetPackage: String,
    ownPackageName: String
): ScreenTimeOverlayAction = when {
    kind != ScreenTimeAccessibilityEventKind.WINDOW_STATE_CHANGED -> ScreenTimeOverlayAction.IGNORE
    packageName.equals(targetPackage, ignoreCase = true) -> ScreenTimeOverlayAction.SHOW
    packageName == null || packageName.equals(ownPackageName, ignoreCase = true) ->
        ScreenTimeOverlayAction.IGNORE
    else -> ScreenTimeOverlayAction.HIDE_DELAYED
}

internal fun screenAlarmLabel(hasUsageAccess: Boolean): String =
    if (hasUsageAccess) ALARM_ENABLED_LABEL else ALARM_DISABLED_LABEL

internal fun usageAccessLimitationCopy(hasUsageAccess: Boolean): String? =
    if (hasUsageAccess) null else USAGE_ACCESS_LIMITATION_COPY

internal fun overlayPermissionCopy(): PermissionCopy = PermissionCopy(
    benefit = "Allows the optional floating network indicator and Instagram scroll counter " +
        "to appear above other apps.",
    whenDenied = "Floating indicators cannot appear; other network and Screen Time features remain available."
)

internal fun shouldScheduleUsageLimitWork(hasUsageAccess: Boolean, alarmMinutes: Int): Boolean =
    hasUsageAccess && alarmMinutes > 0
