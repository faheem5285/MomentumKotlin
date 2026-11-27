package com.faheem.habits.app

import com.faheem.habits.core.ui.AppModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentumRoutesTest {
    @Test
    fun moduleRoutesKeepOptionalArgumentsStable() {
        assertEquals("module/habits", MomentumRoutes.module(AppModule.Habits))
        assertEquals("module/habits?itemId=7", MomentumRoutes.module(AppModule.Habits, itemId = 7))
        assertEquals("module/habits?requestToken=9", MomentumRoutes.module(AppModule.Habits, requestToken = 9))
        assertEquals(
            "module/habits?itemId=7&requestToken=9",
            MomentumRoutes.module(AppModule.Habits, itemId = 7, requestToken = 9)
        )
    }

    @Test
    fun deepLinksUseTheDocumentedContract() {
        assertEquals(
            "momentum://module/habits?itemId=7&requestToken=9",
            MomentumRoutes.moduleDeepLink(AppModule.Habits, itemId = 7, requestToken = 9)
        )
        assertEquals("momentum://module/profile", MomentumRoutes.moduleDeepLink(AppModule.Profile))
    }

    @Test
    fun moduleParsingIsCaseInsensitiveAndRejectsUnknownNames() {
        assertEquals(AppModule.Habits, MomentumRoutes.parseModule("habits"))
        assertEquals(AppModule.Habits, MomentumRoutes.parseModule(" HABITS "))
        assertEquals(AppModule.Profile, MomentumRoutes.parseModule(AppModule.Profile.name))
        assertNull(MomentumRoutes.parseModule("not-a-module"))
        assertNull(MomentumRoutes.parseModule(null))
    }

    @Test
    fun hiddenModulesAreBlockedForUserAndNotificationEntries() {
        val hidden = { _: AppModule -> false }
        assertFalse(isModuleEntryAllowed(AppModule.Habits, hidden, ModuleEntrySource.NORMAL))
        assertFalse(isModuleEntryAllowed(AppModule.Habits, hidden, ModuleEntrySource.DEEP_LINK))
        assertFalse(isModuleEntryAllowed(AppModule.Habits, hidden, ModuleEntrySource.NOTIFICATION))
    }

    @Test
    fun accountAndSettingsEntriesRemainAvailable() {
        val hidden = { _: AppModule -> false }
        assertTrue(isModuleEntryAllowed(AppModule.Profile, hidden, ModuleEntrySource.NORMAL))
        assertTrue(isModuleEntryAllowed(AppModule.Settings, hidden, ModuleEntrySource.DEEP_LINK))
        assertTrue(isModuleEntryAllowed(AppModule.Habits, hidden, ModuleEntrySource.SYSTEM))
    }

    @Test
    fun visibleModulesStayReachable() {
        val allVisible = { _: AppModule -> true }
        assertTrue(isModuleEntryAllowed(AppModule.Habits, allVisible, ModuleEntrySource.NORMAL))
        assertTrue(isModuleEntryAllowed(AppModule.Habits, allVisible, ModuleEntrySource.DEEP_LINK))
        assertTrue(isModuleEntryAllowed(AppModule.Habits, allVisible, ModuleEntrySource.NOTIFICATION))
    }
}
