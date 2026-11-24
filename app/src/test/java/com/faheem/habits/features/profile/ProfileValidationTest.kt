package com.faheem.habits.features.profile

import com.faheem.habits.core.ui.passwordStrength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidationTest {
    @Test fun validatesEmailAndPasswordInputs() {
        assertTrue(ProfileValidation.isValidEmail("person@example.com"))
        assertFalse(ProfileValidation.isValidEmail("person example.com"))
        assertTrue(ProfileValidation.isValidPassword("a secure pass"))
        assertFalse(ProfileValidation.isValidPassword("short"))
    }

    @Test fun passwordStrengthIsBoundedAndImprovesWithVariety() {
        assertEquals(0, passwordStrength(""))
        assertTrue(passwordStrength("Aa7!StrongPass") > passwordStrength("password"))
        assertTrue(passwordStrength("Aa7!StrongPass") in 1..4)
    }

    @Test fun nonceIsFreshAndUrlSafe() {
        val first = ProfileValidation.newNonce()
        val second = ProfileValidation.newNonce()
        assertNotEquals(first, second)
        assertTrue(first.length >= 40)
        assertTrue(first.matches(Regex("^[A-Za-z0-9_-]+$")))
        assertEquals(64, ProfileValidation.hashNonce(first).length)
        assertNotEquals(first, ProfileValidation.hashNonce(first))
    }
}
