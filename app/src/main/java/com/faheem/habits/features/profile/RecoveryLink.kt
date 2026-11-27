package com.faheem.habits.features.profile

import java.net.URI
import java.net.URLDecoder

/**
 * Validated recovery deep link. Only `momentum://auth/reset` with
 * `type=recovery`, an unexpired access token and no provider error is accepted.
 * The token is held only in memory by the caller and never logged.
 */
sealed interface RecoveryLinkResult {
    data object Absent : RecoveryLinkResult
    data class Valid(val accessToken: String, val expiresAtMillis: Long) : RecoveryLinkResult
    data class Invalid(val message: String) : RecoveryLinkResult
}

object RecoveryLinkParser {
    private const val EXPIRED = "That password reset link has expired. Request a new one."
    private const val INVALID = "That password reset link could not be used. Request a new one."
    private const val MAX_TOKEN_LENGTH = 4096

    fun parse(link: String?, nowMillis: Long): RecoveryLinkResult {
        val value = link?.trim().takeUnless { it.isNullOrEmpty() } ?: return RecoveryLinkResult.Absent
        val uri = runCatching { URI(value) }.getOrNull() ?: return RecoveryLinkResult.Invalid(INVALID)
        if (!uri.scheme.equals("momentum", ignoreCase = true) ||
            !uri.host.equals("auth", ignoreCase = true) ||
            uri.path.trim('/').equals("reset", ignoreCase = true).not()
        ) {
            return RecoveryLinkResult.Invalid(INVALID)
        }
        val parameters = parseParameters(uri.rawQuery) + parseParameters(uri.rawFragment)
        if (!parameters["error"].isNullOrBlank()) return RecoveryLinkResult.Invalid(INVALID)
        if (!parameters["type"].equals("recovery", ignoreCase = true)) {
            return RecoveryLinkResult.Invalid("That link is not a password reset link.")
        }
        val token = parameters["access_token"]?.trim().orEmpty()
        if (token.isEmpty() || token.length > MAX_TOKEN_LENGTH || token.any(Char::isWhitespace)) {
            return RecoveryLinkResult.Invalid(INVALID)
        }
        val expiresAtMillis = parameters["expires_in"]?.toLongOrNull()?.let { nowMillis + it * 1_000L }
            ?: parameters["expires_at"]?.toLongOrNull()?.let { it * 1_000L }
            ?: return RecoveryLinkResult.Invalid(INVALID)
        if (expiresAtMillis <= nowMillis) return RecoveryLinkResult.Invalid(EXPIRED)
        return RecoveryLinkResult.Valid(token, expiresAtMillis)
    }

    private fun parseParameters(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return buildMap {
            raw.split('&').forEach { part ->
                if (part.isBlank()) return@forEach
                val separator = part.indexOf('=')
                val key = if (separator < 0) part else part.substring(0, separator)
                val value = if (separator < 0) "" else part.substring(separator + 1)
                put(
                    decode(key),
                    decode(value)
                )
            }
        }
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
