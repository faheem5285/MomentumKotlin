package com.faheem.habits.core.data.backup

/** Version contract shared by export, compatibility reads, and tests. */
internal object BackupFormatPolicy {
    const val VERSION = 4
    const val CURRENT_SCHEMA = 22
    const val FORMAT_VERSION_3 = 3
    const val SCHEMA_VERSION_20 = 20
    const val PREVIOUS_VERSION = 2

    val LEGACY_SCHEMAS = setOf(11, 13)

    val TABLE_KEYS = listOf(
        "habits", "completions", "habitSkips", "reminders", "expenses", "categoryBudgets",
        "contacts", "loanPeople", "loans", "loanAttachments", "loanPayments", "files",
        "todos", "lifeRules", "lifeRuleFiles", "customNotifications",
        "vocabularyWords", "grammarNotes", "reviewStates", "translationHistory"
    )

    fun isSupported(version: Int, schemaVersion: Int): Boolean = when (version) {
        VERSION -> schemaVersion == CURRENT_SCHEMA
        FORMAT_VERSION_3 -> schemaVersion == SCHEMA_VERSION_20
        PREVIOUS_VERSION -> schemaVersion in LEGACY_SCHEMAS
        else -> false
    }
}
