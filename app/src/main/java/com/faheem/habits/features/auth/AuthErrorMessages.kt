package com.faheem.habits.features.auth

/**
 * Converts provider/network failures into safe messages for the sign-in screen.
 * Backend response bodies and implementation details are never shown verbatim.
 */
internal fun userFacingAuthError(error: Throwable): String {
    val detail = error.message.orEmpty()
    return when {
        detail.contains("invalid login credentials", ignoreCase = true) ->
            "The email or password is incorrect. Check your details and try again."
        detail.contains("email not confirmed", ignoreCase = true) ->
            "Please confirm your email using the link we sent, then sign in."
        detail.contains("user already registered", ignoreCase = true) ->
            "An account already exists for this email. Try signing in instead."
        detail.contains("password should be at least", ignoreCase = true) ||
            detail.contains("weak password", ignoreCase = true) ->
            "Choose a stronger password and try again."
        detail.contains("rate limit", ignoreCase = true) ->
            "Too many attempts. Please wait a little and try again."
        detail.contains("not configured", ignoreCase = true) ->
            "Account services are not configured. Please contact support."
        else ->
            "We couldn't complete that request. Check your connection and try again."
    }
}
