package com.faheem.habits.core.data.supabase

import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException

/** Explicit signup states. Raw response bodies are never exposed to the UI. */
enum class SignUpOutcome {
    CONFIRMATION_REQUIRED,
    SESSION_STARTED,
    ACCOUNT_EXISTS,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
    NETWORK_UNAVAILABLE,
    REJECTED
}

internal data class ParsedSignUp(
    val outcome: SignUpOutcome,
    val session: AuthResponse? = null
)

/**
 * Parses a Supabase signup response into a closed set of UI states. When email
 * confirmation is disabled Supabase returns a full session; otherwise it returns
 * an empty user object and confirmation is required.
 */
internal object SignUpResponseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(status: Int, body: String): ParsedSignUp {
        if (status !in 200..299) {
            return ParsedSignUp(
                when {
                    status == 429 -> SignUpOutcome.RATE_LIMITED
                    status in 500..599 -> SignUpOutcome.SERVICE_UNAVAILABLE
                    status in 400..499 && describesExistingAccount(body) -> SignUpOutcome.ACCOUNT_EXISTS
                    else -> SignUpOutcome.REJECTED
                }
            )
        }
        val session = runCatching { json.decodeFromString<AuthResponse>(body) }.getOrNull()
        val complete = session != null &&
            !session.access_token.isNullOrBlank() &&
            !session.refresh_token.isNullOrBlank() &&
            !session.user?.id.isNullOrBlank()
        return if (complete) {
            ParsedSignUp(SignUpOutcome.SESSION_STARTED, session)
        } else {
            ParsedSignUp(SignUpOutcome.CONFIRMATION_REQUIRED)
        }
    }

    fun fromFailure(error: Throwable): SignUpOutcome = when {
        error is IOException || error is HttpRequestTimeoutException -> SignUpOutcome.NETWORK_UNAVAILABLE
        error.message.orEmpty().contains("network", ignoreCase = true) ||
            error.message.orEmpty().contains("timeout", ignoreCase = true) -> SignUpOutcome.NETWORK_UNAVAILABLE
        else -> SignUpOutcome.REJECTED
    }

    /** Only fixed markers are inspected; the body itself is discarded after parsing. */
    private fun describesExistingAccount(body: String): Boolean = listOf(
        "already registered", "already exists", "email_exists", "already_registered", "user already"
    ).any { body.contains(it, ignoreCase = true) }
}
