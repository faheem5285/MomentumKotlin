package com.faheem.habits.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLayoutTest {
    @Test
    fun eightRequiredGroupsAreRenderedInOrder() {
        assertEquals(
            listOf(
                SettingsSection.APPEARANCE,
                SettingsSection.NOTIFICATIONS,
                SettingsSection.PRIVACY_AND_SECURITY,
                SettingsSection.DATA_AND_BACKUP,
                SettingsSection.CLOUD_AND_SYNC,
                SettingsSection.SCREEN_TIME_AND_DEVICE,
                SettingsSection.ACCOUNT,
                SettingsSection.ABOUT
            ),
            SettingsLayout.sections
        )
    }

    @Test
    fun noDestinationAppearsTwice() {
        assertTrue(SettingsLayout.duplicateDestinationIds().isEmpty())
        assertEquals(
            SettingsDestination.entries.size,
            SettingsDestination.entries.map { it.id }.distinct().size
        )
    }

    @Test
    fun everyGroupContainsAtLeastOneDestination() {
        SettingsLayout.sections.forEach { section ->
            assertTrue("${section.title} has no destinations", SettingsLayout.destinationsIn(section).isNotEmpty())
        }
    }
}
