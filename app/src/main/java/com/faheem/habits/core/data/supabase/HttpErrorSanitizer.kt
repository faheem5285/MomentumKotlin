package com.faheem.habits.core.data.supabase

/**
 * Converts raw HTTP/auth failures into messages that are safe to persist in
 * sync state, WorkManager output and the UI. Raw response bodies are never
 * returned; only a small allow-list of Supabase error codes is preserved.
 */
object HttpErrorSanitizer {
    private const val GENERIC_FAILURE = "The cloud request could not be completed."
    private val SAFE_CODES = listOf("PGRST205", "PGRST116", "42501", "22P02", "23505")

    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val URL = Regex("""https?://[^\s"',}\]]+""")
    private val BEARER = Regex("""(?i)bearer\s+[A-Za-z0-9._\-]+""")
    private val JWT = Regex("""eyJ[A-Za-z0-9._\-]+""")
    private val UUID = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
    private val LONG_SECRET = Regex("""[A-Za-z0-9_\-]{40,}""")
    private val SENSITIVE_FIELD = Regex(
        """(?i)\b(token|refresh_token|access_token|password|email|phone|authorization)\b\s*[:=]\s*"?(?:[^\s",}\]]+)"""
    )

    /** Builds a user-facing message from a status code without using the response body text. */
    fun forHttp(status: Int, body: String? = null): String {
        val code = body?.let { raw -> SAFE_CODES.firstOrNull { raw.contains(it, ignoreCase = true) } }
        val suffix = code?.let { " (Supabase code: $it)" }.orEmpty()
        val base = when (status) {
            400 -> "The cloud request was rejected. Check the information and try again."
            401 -> "Your cloud session is no longer valid. Sign in again to use cloud sync."
            403 -> "Cloud access was denied for this account."
            404 -> "The requested cloud resource is unavailable."
            409 -> "The cloud request conflicts with another operation. Try again shortly."
            429 -> "The cloud service is rate-limiting requests. Try again shortly."
            in 500..599 -> "The cloud service is temporarily unavailable."
            else -> GENERIC_FAILURE
        }
        return base + suffix
    }

    /** Last line of defense for exception messages that come from other components. */
    fun forDisplay(message: String?): String {
        val trimmed = message?.trim().orEmpty()
        if (trimmed.isEmpty()) return GENERIC_FAILURE
        val redacted = SENSITIVE_FIELD.replace(trimmed, "$1=[redacted]")
            .let { BEARER.replace(it, "Bearer [redacted]") }
            .let { JWT.replace(it, "[redacted]") }
            .let { URL.replace(it, "[url]") }
            .let { EMAIL.replace(it, "[email]") }
            .let { UUID.replace(it, "[id]") }
            .let { LONG_SECRET.replace(it, "[redacted]") }
        return redacted.take(240)
    }
}
