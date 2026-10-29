package com.faheem.habits.core.data.supabase

import android.content.Context
import com.faheem.habits.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

enum class SessionRestoreResult { AUTHENTICATED, NO_SESSION, TEMPORARY_FAILURE }

/**
 * Supabase email/password auth. Refresh tokens are encrypted by Android Keystore;
 * access tokens are not persisted by this app.
 */
object SupabaseAuthApi {
    private val http = HttpClient(Android)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Credentials(val email: String, val password: String)

    @Serializable
    private data class RefreshCredentials(val refresh_token: String)

    @Serializable
    private data class AuthResponse(
        val access_token: String? = null,
        val refresh_token: String? = null,
        val expires_in: Long? = null,
        val token_type: String? = null
    )

    private class AuthHttpException(val status: Int, detail: String) :
        IllegalStateException("Supabase authentication failed ($status): $detail")

    suspend fun signUp(email: String, password: String) {
        request("signup", json.encodeToString(Credentials(email, password)))
    }

    suspend fun signIn(context: Context, email: String, password: String) {
        val body = request("token?grant_type=password", json.encodeToString(Credentials(email, password)))
        val session = json.decodeFromString<AuthResponse>(body)
        val refreshToken = session.refresh_token
        check(!refreshToken.isNullOrBlank()) {
            "Sign-in succeeded but Supabase did not return a refresh token."
        }
        SecureTokenStore.saveRefreshToken(context.applicationContext, refreshToken)
    }

    suspend fun restoreSession(context: Context): SessionRestoreResult {
        val refreshToken = SecureTokenStore.readRefreshToken(context.applicationContext)
            ?: return SessionRestoreResult.NO_SESSION
        return try {
            val body = request(
                "token?grant_type=refresh_token",
                json.encodeToString(RefreshCredentials(refreshToken))
            )
            val session = json.decodeFromString<AuthResponse>(body)
            val updatedToken = session.refresh_token
            check(!updatedToken.isNullOrBlank()) {
                "Supabase did not return a refreshed session token."
            }
            SecureTokenStore.saveRefreshToken(context.applicationContext, updatedToken)
            SessionRestoreResult.AUTHENTICATED
        } catch (e: AuthHttpException) {
            if (e.status in listOf(400, 401, 403)) {
                SecureTokenStore.clear(context.applicationContext)
                SessionRestoreResult.NO_SESSION
            } else {
                SessionRestoreResult.TEMPORARY_FAILURE
            }
        } catch (_: Exception) {
            // Keep the encrypted token on network errors so an offline start does not destroy it.
            SessionRestoreResult.TEMPORARY_FAILURE
        }
    }

    private suspend fun request(endpoint: String, body: String): String {
        check(SupabaseProvider.isConfigured) {
            "Supabase is not configured. Check your local Gradle properties."
        }
        val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
            .removeSuffix("/rest/v1")
            .removeSuffix("/auth/v1")
            .trimEnd('/')
        val response = http.post("$baseUrl/auth/v1/$endpoint") {
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val responseBody = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw AuthHttpException(response.status.value, responseBody.take(500))
        }
        return responseBody
    }
}
