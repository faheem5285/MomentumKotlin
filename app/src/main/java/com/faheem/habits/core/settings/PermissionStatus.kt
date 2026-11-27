package com.faheem.habits.core.settings

/** Permissions surfaced by the onboarding cards and setup screen. */
enum class PermissionKind {
    NOTIFICATIONS,
    EXACT_ALARMS,
    USAGE_ACCESS,
    OVERLAY,
    ACCESSIBILITY,
    BATTERY_OPTIMIZATION,
    AUTO_START,
    BACKGROUND_RESTRICTIONS
}

enum class PermissionStatus { GRANTED, DENIED, UNSUPPORTED }

/**
 * Verified snapshot. Never infer status from an intent or from a previous
 * session: every call re-checks the current OS state.
 */
data class PermissionStatuses(val values: Map<PermissionKind, PermissionStatus> = emptyMap()) {
    fun status(kind: PermissionKind): PermissionStatus = values[kind] ?: PermissionStatus.UNSUPPORTED

    val managedKinds: List<PermissionKind>
        get() = listOf(PermissionKind.NOTIFICATIONS, PermissionKind.EXACT_ALARMS, PermissionKind.USAGE_ACCESS)

    val deniedManaged: List<PermissionKind>
        get() = managedKinds.filter { status(it) == PermissionStatus.DENIED }

    val needsAttention: Boolean get() = deniedManaged.isNotEmpty()

    /** Counts only permissions the device exposes; manufacturer-only checks are excluded. */
    val enabledSummary: String
        get() {
            val applicable = values.filterValues { it != PermissionStatus.UNSUPPORTED }
            val enabled = applicable.count { it.value == PermissionStatus.GRANTED }
            return "$enabled of ${applicable.size} enabled"
        }
}
