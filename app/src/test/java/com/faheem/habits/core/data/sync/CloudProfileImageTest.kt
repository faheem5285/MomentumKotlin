package com.faheem.habits.core.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudProfileImageTest {
    @Test
    fun pulledCloudPathBecomesLocalSentinelRatherThanUri() {
        assertEquals("cloud:contacts/uuid/profile", profilePathToStoredUri(" contacts/uuid/profile "))
        assertEquals("cloud:loan-people/uuid/profile", profilePathToStoredUri("loan-people/uuid/profile"))
        assertEquals("", profilePathToStoredUri("  "))
    }

    @Test
    fun sentinelAndLegacyCloudPathsAreRecognised() {
        assertEquals("contacts/uuid/profile", cloudProfilePath("cloud:contacts/uuid/profile"))
        assertEquals("contacts/uuid/profile", cloudProfilePath("CLOUD:contacts/uuid/profile"))
        assertEquals("contacts/uuid/profile", cloudProfilePath("contacts/uuid/profile"))
        assertEquals("loan-people/uuid/profile", cloudProfilePath("cloud:loan-people/uuid/profile"))
        assertNull(cloudProfilePath("content://provider/image"))
        assertNull(cloudProfilePath(""))
    }

    @Test
    fun onlyLocalUrisArePendingUpload() {
        assertTrue(shouldUploadProfileImage("content://provider/image"))
        assertFalse(shouldUploadProfileImage("cloud:contacts/uuid/profile"))
        assertFalse(shouldUploadProfileImage("contacts/uuid/profile"))
        assertFalse(shouldUploadProfileImage(""))
    }
}
