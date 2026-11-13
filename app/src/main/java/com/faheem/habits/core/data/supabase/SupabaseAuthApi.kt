package com.faheem.habits.core.data.supabase

import android.content.Context
import com.faheem.habits.BuildConfig
import com.faheem.habits.core.data.AppDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SessionRestoreResult { AUTHENTICATED, NO_SESSION, TEMPORARY_FAILURE }

/**
 * Supabase email/password auth. Refresh tokens are encrypted by Android Keystore;
 * access tokens are not persisted by this app.
 */
@Suppress("TooManyFunctions")
object SupabaseAuthApi {
    private val http = HttpClient(Android)
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshMutex = Mutex()
    private val mutableSignOutEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signOutEvents = mutableSignOutEvents.asSharedFlow()

    @Serializable
    private data class Credentials(val email: String, val password: String)

    @Serializable
    private data class SignupCredentials(val email: String, val password: String, val data: SignupMetadata)

    @Serializable
    private data class SignupMetadata(@SerialName("display_name") val displayName: String)

    @Serializable
    private data class RefreshCredentials(@SerialName("refresh_token") val refreshToken: String)

    @Serializable
    private data class AuthResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("token_type") val tokenType: String? = null,
        val user: AuthUser? = null
    )

    @Serializable
    private data class AuthUser(
        val id: String? = null,
        val email: String? = null,
        @SerialName("user_metadata") val userMetadata: JsonObject? = null
    )

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var accessTokenExpiresAt: Long = 0L

    private fun rememberSession(session: AuthResponse) {
        accessToken = session.accessToken
        val lifetimeMs = (session.expiresIn ?: 3600L).coerceAtLeast(60L) * 1_000L
        accessTokenExpiresAt = System.currentTimeMillis() + lifetimeMs
    }

    private fun cacheProfile(context: Context, session: AuthResponse) {
        val name = session.user?.userMetadata?.get("display_name")?.jsonPrimitive?.contentOrNull
            ?.trim()?.take(80).orEmpty()
        val email = session.user?.email?.trim()?.take(160).orEmpty()
        val userId = session.user?.id?.trim().orEmpty()
        if (name.isNotBlank() || email.isNotBlank() || userId.isNotBlank()) {
            val editor = context.getSharedPreferences("account_profile", Context.MODE_PRIVATE).edit()
            if (name.isNotBlank()) editor.putString("display_name", name)
            if (email.isNotBlank()) editor.putString("email", email)
            if (userId.isNotBlank()) editor.putString("user_id", userId)
            editor.apply()
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
        val refreshToken = session.refreshToken
        check(!refreshToken.isNullOrBlank()) {
            "Sign-in succeeded but Supabase did not return a refresh token."
        }
        activateAccountDatabase(context.applicationContext, session)
        rememberSession(session)
        cacheProfile(context.applicationContext, session)
        SecureTokenStore.saveRefreshToken(context.applicationContext, refreshToken)
    }

    suspend fun restoreSession(context: Context): SessionRestoreResult = refreshMutex.withLock {
        // A parallel caller may already have rotated the refresh token while this
        // caller was waiting. Reuse that fresh in-memory session instead of rotating again.
        if (hasFreshToken()) return@withLock SessionRestoreResult.AUTHENTICATED

        val appContext = context.applicationContext
        val refreshToken = SecureTokenStore.readRefreshToken(appContext)
            ?: return@withLock SessionRestoreResult.NO_SESSION
        try {
            val body = request(
                "token?grant_type=refresh_token",
                json.encodeToString(RefreshCredentials(refreshToken))
            )
            val session = json.decodeFromString<AuthResponse>(body)
            val updatedToken = session.refreshToken
            check(!updatedToken.isNullOrBlank()) {
                "Supabase did not return a refreshed session token."
            }
            activateAccountDatabase(appContext, session)
            // Persist the rotated token before exposing the new access token.
            SecureTokenStore.saveRefreshToken(appContext, updatedToken)
            cacheProfile(appContext, session)
            rememberSession(session)
            SessionRestoreResult.AUTHENTICATED
        } catch (e: AuthHttpException) {
            if (e.status in listOf(400, 401, 403)) {
                SecureTokenStore.clear(appContext)
                clearInMemorySession()
                SessionRestoreResult.NO_SESSION
            } else {
                SessionRestoreResult.TEMPORARY_FAILURE
            }
        } catch (_: Exception) {
            // Keep the encrypted token on network/storage errors; never sign out offline.
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

    fun currentUserId(context: Context): String? = context.applicationContext
        .getSharedPreferences("account_profile", Context.MODE_PRIVATE)
        .getString("user_id", null)

    private suspend fun activateAccountDatabase(context: Context, session: AuthResponse) = withContext(Dispatchers.IO) {
        val userId = session.user?.id?.takeIf(String::isNotBlank)
            ?: error("Supabase did not return the signed-in account ID.")
        AppDatabase.activateAccount(context, userId)
    }

    /** Revokes the Supabase session when online and always clears this device's saved session. */
    suspend fun signOut(context: Context) {
        val appContext = context.applicationContext
        val token = runCatching { getAccessToken(appContext) }.getOrNull()
        try {
            if (!token.isNullOrBlank() && SupabaseProvider.isConfigured) {
                val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
                    .removeSuffix("/rest/v1")
                    .removeSuffix("/auth/v1")
                    .trimEnd('/')
                runCatching {
                    http.post("$baseUrl/auth/v1/logout") {
                        header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                        header("Authorization", "Bearer $token")
                    }
                }
            }
        } finally {
            SecureTokenStore.clear(appContext)
            clearInMemorySession()
            appContext.getSharedPreferences("account_profile", Context.MODE_PRIVATE)
                .edit().clear().apply()
            mutableSignOutEvents.tryEmit(Unit)
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
