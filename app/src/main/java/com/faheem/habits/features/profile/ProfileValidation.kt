package com.faheem.habits.features.profile

import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64

internal object ProfileValidation {
    private val random = SecureRandom()
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun isValidEmail(email: String): Boolean = emailPattern.matches(email.trim())
    fun isValidPassword(password: String): Boolean = password.length >= 8

    fun newNonce(): String = ByteArray(32).also(random::nextBytes).let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }

    fun hashNonce(nonce: String): String = MessageDigest.getInstance("SHA-256")
        .digest(nonce.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
