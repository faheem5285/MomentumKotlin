package com.faheem.habits.core.data.supabase

/**
 * Pure session decisions shared by the auth flow and JVM tests.
 * A small expiry buffer avoids returning an access token that is about to expire.
 */
internal object AuthSessionPolicy {
    private const val EXPIRY_BUFFER_MS = 60_000L

    fun hasFreshAccessToken(token: String?, expiresAtMs: Long, nowMs: Long): Boolean =
        !token.isNullOrBlank() && nowMs < expiresAtMs - EXPIRY_BUFFER_MS

    /** Only definitive auth rejection should discard a saved refresh token. */
    fun shouldClearSession(httpStatus: Int): Boolean =
        httpStatus == 400 || httpStatus == 401 || httpStatus == 403
}
