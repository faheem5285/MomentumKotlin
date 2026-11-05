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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
    private data class SignupCredentials(val email: String, val password: String, val data: SignupMetadata)

    @Serializable
    private data class SignupMetadata(val display_name: String)

    @Serializable
    private data class RefreshCredentials(val refresh_token: String)

    @Serializable
    private data class AuthResponse(
        val access_token: String? = null,
        val refresh_token: String? = null,
        val expires_in: Long? = null,
        val token_type: String? = null,
        val user: AuthUser? = null
    )

    @Serializable
    private data class AuthUser(val user_metadata: JsonObject? = null)

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var accessTokenExpiresAt: Long = 0L

    private fun rememberSession(session: AuthResponse) {
        accessToken = session.access_token
        val lifetimeMs = (session.expires_in ?: 3600L).coerceAtLeast(60L) * 1_000L
        accessTokenExpiresAt = System.currentTimeMillis() + lifetimeMs
    }

    private fun cacheDisplayName(context: Context, session: AuthResponse) {
        val name = session.user?.user_metadata?.get("display_name")?.jsonPrimitive?.contentOrNull
            ?.trim()?.take(80).orEmpty()
        if (name.isNotBlank()) {
            context.getSharedPreferences("account_profile", Context.MODE_PRIVATE)
                .edit().putString("display_name", name).apply()
        }
    }

    private fun hasFreshToken(): Boolean =
        !accessToken.isNullOrBlank() && System.currentTimeMillis() < accessTokenExpiresAt - 60_000L

    private class AuthHttpException(val status: Int, detail: String) :
        IllegalStateException("Supabase authentication failed ($status): $detail")

    suspend fun signUp(email: String, password: String) {
        request("signup", json.encodeToString(Credentials(email, password)))
    }

    suspend fun signUp(email: String, password: String, displayName: String) {
        request("signup", json.encodeToString(SignupCredentials(email, password, SignupMetadata(displayName))))
    }

    suspend fun sendPasswordReset(email: String) {
        request("recover", json.encodeToString(mapOf("email" to email)))
    }

    suspend fun signIn(context: Context, email: String, password: String) {
        val body = request("token?grant_type=password", json.encodeToString(Credentials(email, password)))
        val session = json.decodeFromString<AuthResponse>(body)
        rememberSession(session)
        cacheDisplayName(context.applicationContext, session)
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
            rememberSession(session)
            cacheDisplayName(context.applicationContext, session)
            val updatedToken = session.refresh_token
            check(!updatedToken.isNullOrBlank()) {
                "Supabase did not return a refreshed session token."
            }
            SecureTokenStore.saveRefreshToken(context.applicationContext, updatedToken)
            SessionRestoreResult.AUTHENTICATED
        } catch (e: AuthHttpException) {
            if (e.status in listOf(400, 401, 403)) {
                SecureTokenStore.clear(context.applicationContext)
                accessToken = null
                accessTokenExpiresAt = 0L
                SessionRestoreResult.NO_SESSION
            } else {
                SessionRestoreResult.TEMPORARY_FAILURE
            }
        } catch (_: Exception) {
            // Keep the encrypted token on network errors so an offline start does not destroy it.
            SessionRestoreResult.TEMPORARY_FAILURE
        }
    }

    suspend fun getAccessToken(context: Context): String? {
        if (hasFreshToken()) return accessToken
        return when (restoreSession(context.applicationContext)) {
            SessionRestoreResult.AUTHENTICATED -> accessToken
            else -> null
        }
    }

    fun clearInMemorySession() {
        accessToken = null
        accessTokenExpiresAt = 0L
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
