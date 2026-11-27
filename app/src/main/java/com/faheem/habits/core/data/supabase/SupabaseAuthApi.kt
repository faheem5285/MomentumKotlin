package com.faheem.habits.core.data.supabase

import android.content.Context
import com.faheem.habits.BuildConfig
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.ProfileCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SessionRestoreResult { AUTHENTICATED, NO_SESSION, TEMPORARY_FAILURE }

/** Parsed auth payload; tokens stay in this layer and are never placed in UI state. */
@Serializable
internal data class AuthResponse(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
    val token_type: String? = null,
    val user: AuthUser? = null
)

@Serializable
internal data class AuthUser(
    val id: String? = null,
    val email: String? = null,
    val user_metadata: JsonObject? = null,
    val app_metadata: JsonObject? = null,
    val created_at: String? = null
)

/**
 * Supabase email/password auth. Refresh tokens are encrypted by Android Keystore;
 * access tokens are not persisted by this app.
 */
object SupabaseAuthApi {
    private val http = HttpClient(Android)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableSignOutEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val signOutEvents = mutableSignOutEvents.asSharedFlow()

    @Serializable
    private data class Credentials(val email: String, val password: String)

    @Serializable
    private data class SignupCredentials(val email: String, val password: String, val data: SignupMetadata)

    @Serializable
    private data class SignupMetadata(val display_name: String)

    @Serializable
    private data class RefreshCredentials(val refresh_token: String)

    @Serializable
    private data class IdTokenCredentials(val provider: String, val id_token: String, val nonce: String)

    @Serializable
    private data class PasswordUpdate(val password: String)

    @Serializable
    private data class ResendConfirmation(val type: String, val email: String)

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var accessTokenExpiresAt: Long = 0L

    /** Serializes refresh-token rotation so concurrent sync calls cannot race. */
    private val refreshMutex = Mutex()

    private fun rememberSession(session: AuthResponse) {
        accessToken = session.access_token
        val lifetimeMs = (session.expires_in ?: 3600L).coerceAtLeast(60L) * 1_000L
        accessTokenExpiresAt = System.currentTimeMillis() + lifetimeMs
    }

    private fun cacheProfile(
        context: Context,
        session: AuthResponse,
        googleDisplayName: String? = null,
        googlePhotoUrl: String? = null
    ) {
        val metadata = session.user?.user_metadata
        val name = listOf(
            googleDisplayName,
            metadata?.get("display_name")?.jsonPrimitive?.contentOrNull,
            metadata?.get("full_name")?.jsonPrimitive?.contentOrNull,
            metadata?.get("name")?.jsonPrimitive?.contentOrNull
        ).firstOrNull { !it.isNullOrBlank() }?.trim()?.take(80).orEmpty()
        val photoUrl = listOf(
            googlePhotoUrl,
            metadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull,
            metadata?.get("picture")?.jsonPrimitive?.contentOrNull
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
        val email = session.user?.email?.trim()?.take(160).orEmpty()
        val userId = session.user?.id?.trim().orEmpty()
        if (name.isNotBlank() || email.isNotBlank() || userId.isNotBlank()) {
            val editor = context.getSharedPreferences("account_profile", Context.MODE_PRIVATE).edit()
            if (name.isNotBlank()) editor.putString("display_name", name)
            if (email.isNotBlank()) editor.putString("email", email)
            if (userId.isNotBlank()) {
                editor.putString("user_id", userId)
                if (name.isNotBlank()) editor.putString("google_display_name_$userId", name)
                if (photoUrl.isNotBlank() && photoUrl.startsWith("https://", ignoreCase = true)) {
                    editor.putString("google_avatar_uri_$userId", photoUrl)
                }
            }
            session.user?.app_metadata?.get("provider")?.jsonPrimitive?.contentOrNull?.let { editor.putString("auth_provider", it) }
            session.user?.created_at?.let { createdAt ->
                runCatching { java.time.Instant.parse(createdAt).toEpochMilli() }.getOrNull()?.let { editor.putLong("member_since", it) }
            }
            editor.apply()
        }
    }

    private fun hasFreshToken(): Boolean =
        !accessToken.isNullOrBlank() && System.currentTimeMillis() < accessTokenExpiresAt - 60_000L

    private class AuthHttpException(val status: Int) :
        IllegalStateException(HttpErrorSanitizer.forHttp(status))

    /**
     * Returns an explicit outcome. When confirmation is disabled Supabase may
     * return a complete session; in that case the session is applied only after
     * the caller's account-link snapshot callback has succeeded.
     */
    suspend fun signUp(
        context: Context,
        email: String,
        password: String,
        displayName: String,
        beforeActivate: suspend (String) -> Unit = {}
    ): SignUpOutcome {
        val (status, body) = requestRaw(
            "signup",
            json.encodeToString(SignupCredentials(email, password, SignupMetadata(displayName)))
        )
        val parsed = SignUpResponseParser.parse(status, body)
        if (parsed.outcome == SignUpOutcome.SESSION_STARTED && parsed.session != null) {
            applyEmailSession(context.applicationContext, parsed.session!!, beforeActivate)
        }
        return parsed.outcome
    }

    suspend fun resendConfirmation(email: String) {
        request(
            "resend",
            json.encodeToString(ResendConfirmation("signup", email.trim().take(160)))
        )
    }

    suspend fun sendPasswordReset(email: String) {
        request("recover?redirect_to=momentum%3A%2F%2Fauth%2Freset", json.encodeToString(mapOf("email" to email)))
    }

    suspend fun signInWithGoogle(
        context: Context,
        idToken: String,
        nonce: String,
        profilePictureUrl: String? = null,
        profileDisplayName: String? = null,
        beforeActivate: suspend (String) -> Unit = {}
    ) {
        val body = request("token?grant_type=id_token", json.encodeToString(IdTokenCredentials("google", idToken, nonce)))
        val session = json.decodeFromString<AuthResponse>(body)
        val userId = session.user?.id?.trim().takeUnless { it.isNullOrBlank() }
            ?: error("Supabase did not return the signed-in account ID.")
        beforeActivate(userId)
        activateAccountDatabase(context.applicationContext, session)
        rememberSession(session)
        cacheProfile(context.applicationContext, session, profileDisplayName, profilePictureUrl)
        if (userId.isNotBlank()) {
            val prefs = context.applicationContext.getSharedPreferences("account_profile", Context.MODE_PRIVATE)
            val memberSince = prefs.getLong("member_since", System.currentTimeMillis())
            val displayName = prefs.getString("google_display_name_$userId", "").orEmpty()
                .ifBlank { prefs.getString("display_name", "").orEmpty() }
            val avatarUri = prefs.getString("google_avatar_uri_$userId", "").orEmpty()
            val email = prefs.getString("email", "").orEmpty()
            AppDatabase.get(context.applicationContext).profileCacheDao().upsert(
                ProfileCache(
                    userId = userId,
                    displayName = displayName,
                    email = email,
                    avatarUri = avatarUri,
                    memberSince = memberSince,
                    updatedAt = System.currentTimeMillis()
                )
            )
            prefs.edit().putString("auth_provider", "google").apply()
        }
        val refreshToken = session.refresh_token ?: error("Sign-in succeeded but Supabase did not return a refresh token.")
        SecureTokenStore.saveRefreshToken(context.applicationContext, refreshToken)
    }

    /** Verifies the current password without activating or persisting a second session. */
    private suspend fun verifyCurrentPassword(context: Context, email: String, current: String): Boolean {
        val body = request("token?grant_type=password", json.encodeToString(Credentials(email.trim(), current)))
        val verification = runCatching { json.decodeFromString<AuthResponse>(body) }.getOrNull()
        val verifiedUserId = verification?.user?.id?.trim().takeUnless { it.isNullOrBlank() }
        return verification?.access_token?.isNotBlank() == true &&
            verifiedUserId != null &&
            verifiedUserId == currentUserId(context)
    }

    suspend fun changePassword(context: Context, email: String, current: String, next: String) {
        check(verifyCurrentPassword(context, email, current)) { "The current password was not accepted." }
        val token = getAccessToken(context) ?: error("Sign in again before changing your password.")
        updatePassword(token, next)
    }

    suspend fun setPassword(context: Context, next: String) {
        val token = getAccessToken(context) ?: error("Sign in again before setting a password.")
        updatePassword(token, next)
        context.getSharedPreferences("account_profile", Context.MODE_PRIVATE).edit().putString("auth_provider", "email").apply()
    }

    private suspend fun updatePassword(token: String, next: String) {
        val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/').removeSuffix("/rest/v1").removeSuffix("/auth/v1").trimEnd('/')
        val response = http.put("$baseUrl/auth/v1/user") {
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(PasswordUpdate(next)))
        }
        if (response.status.value !in 200..299) throw AuthHttpException(response.status.value)
    }

    suspend fun completePasswordRecovery(accessToken: String, next: String) {
        val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/').removeSuffix("/rest/v1").removeSuffix("/auth/v1").trimEnd('/')
        val response = http.put("$baseUrl/auth/v1/user") {
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(PasswordUpdate(next)))
        }
        if (response.status.value !in 200..299) throw AuthHttpException(response.status.value)
    }

    suspend fun signIn(
        context: Context,
        email: String,
        password: String,
        beforeActivate: suspend (String) -> Unit = {}
    ) {
        val body = request("token?grant_type=password", json.encodeToString(Credentials(email.trim(), password)))
        val session = json.decodeFromString<AuthResponse>(body)
        applyEmailSession(context.applicationContext, session, beforeActivate)
    }

    /** Common post-authentication path for password sign-in and session-returning signup. */
    private suspend fun applyEmailSession(
        appContext: Context,
        session: AuthResponse,
        beforeActivate: suspend (String) -> Unit
    ) {
        val userId = session.user?.id?.trim().takeUnless { it.isNullOrBlank() }
            ?: error("Supabase did not return the signed-in account ID.")
        beforeActivate(userId)
        activateAccountDatabase(appContext, session)
        rememberSession(session)
        cacheProfile(appContext, session)
        appContext.getSharedPreferences("account_profile", Context.MODE_PRIVATE)
            .edit().putString("auth_provider", "email").apply()
        val refreshToken = session.refresh_token
        check(!refreshToken.isNullOrBlank()) {
            "Sign-in succeeded but Supabase did not return a refresh token."
        }
        SecureTokenStore.saveRefreshToken(appContext, refreshToken)
    }

    suspend fun restoreSession(context: Context): SessionRestoreResult = refreshMutex.withLock {
        performRefresh(context.applicationContext)
    }

    /**
     * Returns a usable access token, forcing a single refresh when the cached
     * token is stale or a request returned 401. Concurrent callers share one
     * refresh so rotated refresh tokens are never used twice.
     */
    suspend fun refreshAccessToken(context: Context, force: Boolean = false): String? =
        refreshMutex.withLock {
            if (!force && hasFreshToken()) return@withLock accessToken
            when (performRefresh(context.applicationContext)) {
                SessionRestoreResult.AUTHENTICATED -> accessToken
                else -> null
            }
        }

    suspend fun getAccessToken(context: Context): String? {
        if (hasFreshToken()) return accessToken
        return refreshAccessToken(context.applicationContext)
    }

    private suspend fun performRefresh(appContext: Context): SessionRestoreResult {
        val refreshToken = SecureTokenStore.readRefreshToken(appContext)
            ?: run {
                clearInMemorySession()
                clearAuthIdentity(appContext, keepLocalBinding = true)
                return SessionRestoreResult.NO_SESSION
            }
        return try {
            val body = request(
                "token?grant_type=refresh_token",
                json.encodeToString(RefreshCredentials(refreshToken))
            )
            val session = json.decodeFromString<AuthResponse>(body)
            activateAccountDatabase(appContext, session)
            rememberSession(session)
            cacheProfile(appContext, session)
            val updatedToken = session.refresh_token
            check(!updatedToken.isNullOrBlank()) {
                "Supabase did not return a refreshed session token."
            }
            SecureTokenStore.saveRefreshToken(appContext, updatedToken)
            SessionRestoreResult.AUTHENTICATED
        } catch (e: AuthHttpException) {
            if (e.status in listOf(400, 401, 403)) {
                SecureTokenStore.clear(appContext)
                clearInMemorySession()
                clearAuthIdentity(
                    appContext,
                    keepLocalBinding = true,
                    notice = "Your saved session expired. Sign in again to use cloud sync; local records remain available."
                )
                SessionRestoreResult.NO_SESSION
            } else {
                SessionRestoreResult.TEMPORARY_FAILURE
            }
        } catch (_: Exception) {
            // Keep the encrypted token on network errors so an offline start does not destroy it.
            SessionRestoreResult.TEMPORARY_FAILURE
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
    suspend fun signOut(context: Context, keepLocalData: Boolean = true) {
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
            if (keepLocalData) SecureTokenStore.clear(appContext)
            else SecureTokenStore.clearForAppReset(appContext)
            clearInMemorySession()
            if (keepLocalData) {
                clearAuthIdentity(appContext, keepLocalBinding = true)
            } else {
                check(appContext.getSharedPreferences("account_profile", Context.MODE_PRIVATE)
                    .edit().clear().commit()) {
                    "Could not clear local account data."
                }
            }
            mutableSignOutEvents.tryEmit(keepLocalData)
        }
    }

    fun clearInMemorySession() {
        accessToken = null
        accessTokenExpiresAt = 0L
    }

    private fun clearAuthIdentity(context: Context, keepLocalBinding: Boolean, notice: String? = null) {
        val prefs = context.getSharedPreferences("account_profile", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (keepLocalBinding) {
            prefs.getString("user_id", null)?.let { editor.putString("local_account_id", it) }
            prefs.getString("email", null)?.let { editor.putString("local_email", it) }
        }
        editor.remove("user_id").remove("email").remove("auth_provider").remove("auth_notice")
        if (notice != null) editor.putString("auth_notice", notice)
        editor.apply()
    }

    private suspend fun request(endpoint: String, body: String): String {
        val (status, responseBody) = requestRaw(endpoint, body)
        if (status !in 200..299) throw AuthHttpException(status)
        return responseBody
    }

    /** Returns the status and body only to parsers inside this layer; neither is logged. */
    private suspend fun requestRaw(endpoint: String, body: String): Pair<Int, String> {
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
        return response.status.value to response.bodyAsText()
    }
}
