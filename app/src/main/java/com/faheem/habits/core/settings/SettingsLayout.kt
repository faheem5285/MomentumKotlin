package com.faheem.habits.core.settings

/**
 * Stable Settings structure. The UI renders these sections in order and uses
 * the destination ids to ensure a setting appears in exactly one place.
 */
enum class SettingsSection(val title: String) {
    APPEARANCE("Appearance"),
    NOTIFICATIONS("Notifications"),
    PRIVACY_AND_SECURITY("Privacy & Security"),
    DATA_AND_BACKUP("Data & Backup"),
    CLOUD_AND_SYNC("Cloud & Sync"),
    SCREEN_TIME_AND_DEVICE("Screen Time & Device"),
    ACCOUNT("Account"),
    ABOUT("About")
}

/** Identifiers used by the Settings screen; each is rendered once. */
enum class SettingsDestination(val id: String, val section: SettingsSection) {
    APPEARANCE(id = "appearance", section = SettingsSection.APPEARANCE),
    NOTIFICATIONS(id = "notifications", section = SettingsSection.NOTIFICATIONS),
    PERMISSIONS(id = "permissions", section = SettingsSection.PRIVACY_AND_SECURITY),
    VAULT(id = "vault", section = SettingsSection.PRIVACY_AND_SECURITY),
    HIDDEN_MODULES(id = "hidden_modules", section = SettingsSection.PRIVACY_AND_SECURITY),
    BACKUP(id = "backup", section = SettingsSection.DATA_AND_BACKUP),
    GLOBAL_SEARCH(id = "global_search", section = SettingsSection.DATA_AND_BACKUP),
    CLOUD_SYNC(id = "cloud_sync", section = SettingsSection.CLOUD_AND_SYNC),
    SCREEN_TIME(id = "screen_time", section = SettingsSection.SCREEN_TIME_AND_DEVICE),
    BATTERY(id = "battery", section = SettingsSection.SCREEN_TIME_AND_DEVICE),
    NETWORK(id = "network", section = SettingsSection.SCREEN_TIME_AND_DEVICE),
    PROFILE(id = "profile", section = SettingsSection.ACCOUNT),
    GETTING_STARTED(id = "getting_started", section = SettingsSection.ABOUT),
    PRIVACY_NOTE(id = "privacy_note", section = SettingsSection.ABOUT)
}

object SettingsLayout {
    val sections: List<SettingsSection> = SettingsSection.entries

    fun destinationsIn(section: SettingsSection): List<SettingsDestination> =
        SettingsDestination.entries.filter { it.section == section }

    fun duplicateDestinationIds(): List<String> = SettingsDestination.entries
        .groupingBy { it.id }
        .eachCount()
        .filterValues { it > 1 }
        .keys
        .toList()
}
