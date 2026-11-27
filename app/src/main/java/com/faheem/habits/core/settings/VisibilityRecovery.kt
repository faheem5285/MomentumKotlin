package com.faheem.habits.core.settings

/** Pure helpers for recovering hidden home modules without a factory reset. */
object VisibilityRecovery {
    /** Absent keys are treated as visible, matching MomentumSettingsRepository. */
    fun hiddenModules(
        visibility: Map<String, Boolean>,
        recoverableModuleNames: List<String>
    ): List<String> = recoverableModuleNames.filter { visibility[it] == false }

    /** Returns the exact values written by showAllModules(). */
    fun recoverAll(
        visibility: Map<String, Boolean>,
        recoverableModuleNames: List<String>
    ): Map<String, Boolean> = visibility.toMutableMap().apply {
        recoverableModuleNames.forEach { put(it, true) }
    }.toMap()

    fun hiddenCount(
        visibility: Map<String, Boolean>,
        recoverableModuleNames: List<String>
    ): Int = hiddenModules(visibility, recoverableModuleNames).size
}
